package dev.enthusiastdev.netinspector.data.lan

import android.net.wifi.WifiManager
import dev.enthusiastdev.netinspector.core.common.net.Ipv4Subnet
import dev.enthusiastdev.netinspector.core.model.lan.Evidence
import dev.enthusiastdev.netinspector.core.model.lan.EvidenceSource
import dev.enthusiastdev.netinspector.core.model.lan.Host
import dev.enthusiastdev.netinspector.core.model.lan.HostObservation
import dev.enthusiastdev.netinspector.core.model.lan.SweepOutcome
import dev.enthusiastdev.netinspector.core.model.lan.SweepProgress
import dev.enthusiastdev.netinspector.core.model.lan.finalizeSweep
import dev.enthusiastdev.netinspector.core.model.lan.mergeObservation
import dev.enthusiastdev.netinspector.data.lan.mdns.MdnsProbe
import dev.enthusiastdev.netinspector.data.lan.netbios.NetBiosProbe
import dev.enthusiastdev.netinspector.data.lan.ssdp.SsdpProbe
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.net.Inet4Address
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** design §8.2 - a prefix shorter than this needs explicit confirmation before an active
 * sweep runs (a /16 is 65,534 probes); passive Stage A discovery is unaffected. */
private const val MIN_UNCONFIRMED_PREFIX_LENGTH = 22

interface LanDiscoveryRepository {
    /** design §8.2 - "results stream to the UI as they arrive." */
    val hosts: Flow<List<Host>>
    val progress: Flow<SweepProgress>

    /**
     * design §8.4 - this suspends for the whole sweep (Stage A then Stage B); cancelling the
     * calling coroutine (the screen's lifecycle scope) cancels every in-flight probe and
     * releases both locks, since that cleanup lives in a `finally` block.
     */
    suspend fun sweep(
        subnet: Ipv4Subnet,
        gateway: Inet4Address?,
        selfAddress: Inet4Address,
        bssid: String?,
        confirmShortPrefix: Boolean = false,
    ): SweepOutcome
}

@Singleton
class DefaultLanDiscoveryRepository
    @Inject
    constructor(
        private val wifiManager: WifiManager,
        private val mdnsProbe: MdnsProbe,
        private val ssdpProbe: SsdpProbe,
        private val netBiosProbe: NetBiosProbe,
        private val sweepPipeline: LanSweepPipeline,
        private val clock: Clock,
    ) : LanDiscoveryRepository {
        private val hostMap = MutableStateFlow<Map<Inet4Address, Host>>(emptyMap())
        override val hosts: Flow<List<Host>> = hostMap.map { it.values.toList() }

        // Keyed on subnet + BSSID, not subnet alone - two different access points very commonly
        // reuse the same default subnet (e.g. 192.168.1.0/24), and subnet alone would then fail
        // to detect the network switch at all.
        private var lastNetwork: Pair<Ipv4Subnet, String?>? = null

        private val sweepProgress =
            MutableStateFlow(SweepProgress(isRunning = false, addressesProbed = 0, addressesTotal = 0))
        override val progress: Flow<SweepProgress> = sweepProgress.asStateFlow()

        override suspend fun sweep(
            subnet: Ipv4Subnet,
            gateway: Inet4Address?,
            selfAddress: Inet4Address,
            bssid: String?,
            confirmShortPrefix: Boolean,
        ): SweepOutcome {
            if (sweepProgress.value.isRunning) return SweepOutcome.AlreadyRunning
            if (subnet.prefixLength < MIN_UNCONFIRMED_PREFIX_LENGTH && !confirmShortPrefix) {
                return SweepOutcome.NeedsConfirmation(subnet.hostCount)
            }
            runSweep(subnet, gateway, selfAddress, bssid)
            return SweepOutcome.Started
        }

        private suspend fun runSweep(
            subnet: Ipv4Subnet,
            gateway: Inet4Address?,
            selfAddress: Inet4Address,
            bssid: String?,
        ) {
            resetIfNetworkChanged(subnet, bssid)

            val observedThisSweep = mutableSetOf<Inet4Address>()
            sweepProgress.value =
                SweepProgress(isRunning = true, addressesProbed = 0, addressesTotal = subnet.hostCount.toInt())

            suspend fun emit(observation: HostObservation) {
                observedThisSweep += observation.address
                hostMap.update { mergeObservation(it, observation) }
            }

            // design C-06 - a leaked multicast lock is a significant battery drain, so both
            // locks are owned by this call, never a long-lived singleton, and released in
            // `finally` regardless of how the sweep ends (including cancellation).
            val multicastLock = wifiManager.createMulticastLock(MULTICAST_LOCK_TAG).apply { setReferenceCounted(false) }
            val wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, WIFI_LOCK_TAG)
            try {
                multicastLock.acquire()
                wifiLock.acquire()

                // design §8.2 - "known hosts," guaranteed correct without a probe.
                emit(
                    HostObservation(selfAddress, listOf(Evidence(EvidenceSource.SELF, clock.instant())), isSelf = true),
                )
                gateway?.let {
                    emit(
                        HostObservation(
                            it,
                            listOf(Evidence(EvidenceSource.GATEWAY, clock.instant())),
                            isGateway = true,
                        ),
                    )
                }

                // Stage A - passive/broadcast, concurrent with each other, ahead of the active
                // sweep so its hostnames are available to seed the results Stage B confirms.
                coroutineScope {
                    val broadcastAddress = subnet.broadcastAddress
                    val mdnsResults = async { runCatching { mdnsProbe.discover() }.getOrDefault(emptyList()) }
                    val ssdpResults = async { runCatching { ssdpProbe.discover() }.getOrDefault(emptyList()) }
                    val netbiosResults =
                        broadcastAddress?.let {
                            async { runCatching { netBiosProbe.discover(it) }.getOrDefault(emptyList()) }
                        }
                    val mdnsList = mdnsResults.await()
                    val ssdpList = ssdpResults.await()
                    val netbiosList = netbiosResults?.await() ?: emptyList()
                    (mdnsList + ssdpList + netbiosList).forEach {
                        emit(
                            it,
                        )
                    }
                }

                // Stage B (active sweep) then Stage C (enrichment of what Stage B confirmed).
                sweepPipeline.run(
                    subnet = subnet,
                    currentHosts = { hostMap.value.values },
                    onObservation = { emit(it) },
                    onProgress = {
                        probed,
                        total,
                        ->
                        sweepProgress.update { it.copy(addressesProbed = probed, addressesTotal = total) }
                    },
                )
            } finally {
                runCatching { wifiLock.release() }
                runCatching { multicastLock.release() }
                hostMap.update { finalizeSweep(it, observedThisSweep) }
                sweepProgress.update { it.copy(isRunning = false) }
            }
        }

        // design §8.3's STALE grace period assumes consecutive sweeps of the same network (a
        // host that's briefly offline); it shouldn't apply across a network switch, where every
        // previous entry is simply from a different network and must be dropped outright.
        private fun resetIfNetworkChanged(
            subnet: Ipv4Subnet,
            bssid: String?,
        ) {
            val network = subnet to bssid
            if (network == lastNetwork) return
            hostMap.value = emptyMap()
            lastNetwork = network
        }

        private companion object {
            const val MULTICAST_LOCK_TAG = "netinspector-lan-discovery"
            const val WIFI_LOCK_TAG = "netinspector-lan-sweep"
        }
    }
