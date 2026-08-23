package dev.enthusiastdev.netinspector.data.lan.mdns

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.enthusiastdev.netinspector.core.model.lan.DiscoveredService
import dev.enthusiastdev.netinspector.core.model.lan.Evidence
import dev.enthusiastdev.netinspector.core.model.lan.EvidenceSource
import dev.enthusiastdev.netinspector.core.model.lan.HostObservation
import dev.enthusiastdev.netinspector.core.model.lan.mdnsServiceHint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import java.time.Clock
import javax.inject.Inject
import kotlin.coroutines.resume

/**
 * design §8.2 Stage A - mDNS via `NsdManager`, including the `_services._dns-sd._udp`
 * meta-query to enumerate whatever service types actually exist on this network rather than
 * guessing from a hardcoded list. `resolveService` calls are serialised (design's own risk
 * note: "historically flaky across OEMs") since issuing overlapping resolves is a known
 * source of silent failures on some `NsdManager` implementations.
 */
class MdnsProbe
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val clock: Clock,
    ) {
        private val nsdManager: NsdManager? by lazy { context.getSystemService(NsdManager::class.java) }
        private val resolveMutex = Mutex()

        suspend fun discover(
            budgetMs: Long = TYPE_DISCOVERY_BUDGET_MS + PER_TYPE_BUDGET_MS * MAX_TYPES_ASSUMED,
        ): List<HostObservation> {
            val manager = nsdManager ?: return emptyList()
            val serviceTypes = discoverServiceTypes(manager)
            if (serviceTypes.isEmpty()) return emptyList()

            val perTypeBudget =
                ((budgetMs - TYPE_DISCOVERY_BUDGET_MS) / serviceTypes.size).coerceAtLeast(MIN_PER_TYPE_BUDGET_MS)
            return coroutineScope {
                serviceTypes
                    .map { type -> async { discoverServicesOfType(manager, type, perTypeBudget) } }
                    .awaitAll()
                    .flatten()
            }
        }

        private suspend fun discoverServiceTypes(manager: NsdManager): List<String> {
            val found = mutableSetOf<String>()
            val listener =
                object : NsdManager.DiscoveryListener {
                    override fun onDiscoveryStarted(serviceType: String) = Unit

                    override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                        found += serviceInfo.serviceType
                    }

                    override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit

                    override fun onDiscoveryStopped(serviceType: String) = Unit

                    override fun onStartDiscoveryFailed(
                        serviceType: String,
                        errorCode: Int,
                    ) = Unit

                    override fun onStopDiscoveryFailed(
                        serviceType: String,
                        errorCode: Int,
                    ) = Unit
                }
            try {
                manager.discoverServices(META_QUERY_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
                delay(TYPE_DISCOVERY_BUDGET_MS)
            } catch (ignored: SecurityException) {
                return emptyList()
            } finally {
                runCatching { manager.stopServiceDiscovery(listener) }
            }
            return found.toList()
        }

        private suspend fun discoverServicesOfType(
            manager: NsdManager,
            serviceType: String,
            budgetMs: Long,
        ): List<HostObservation> {
            val results = mutableListOf<HostObservation>()
            withTimeoutOrNull(budgetMs) {
                discoverServicesOfTypeFlow(manager, serviceType).collect { results += it }
            }
            return results
        }

        private fun discoverServicesOfTypeFlow(
            manager: NsdManager,
            serviceType: String,
        ): Flow<HostObservation> =
            callbackFlow {
                val scope: CoroutineScope = this
                val listener =
                    object : NsdManager.DiscoveryListener {
                        override fun onDiscoveryStarted(regType: String) = Unit

                        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                            scope.launch {
                                resolveService(manager, serviceInfo)?.let { trySend(it) }
                            }
                        }

                        override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit

                        override fun onDiscoveryStopped(regType: String) = Unit

                        override fun onStartDiscoveryFailed(
                            regType: String,
                            errorCode: Int,
                        ) {
                            close()
                        }

                        override fun onStopDiscoveryFailed(
                            regType: String,
                            errorCode: Int,
                        ) = Unit
                    }
                try {
                    manager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
                } catch (ignored: SecurityException) {
                    close()
                }
                awaitClose { runCatching { manager.stopServiceDiscovery(listener) } }
            }

        private suspend fun resolveService(
            manager: NsdManager,
            serviceInfo: NsdServiceInfo,
        ): HostObservation? =
            resolveMutex.withLock {
                suspendCancellableCoroutine { continuation ->
                    val listener =
                        object : NsdManager.ResolveListener {
                            override fun onResolveFailed(
                                serviceInfo: NsdServiceInfo,
                                errorCode: Int,
                            ) {
                                if (continuation.isActive) continuation.resume(null)
                            }

                            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                                if (continuation.isActive) continuation.resume(serviceInfo.toObservation())
                            }
                        }
                    try {
                        manager.resolveService(serviceInfo, listener)
                    } catch (ignored: IllegalArgumentException) {
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
            }

        private fun NsdServiceInfo.toObservation(): HostObservation? {
            val address = host as? Inet4Address ?: return null
            val txtRecords = decodeTxtRecords()
            return HostObservation(
                address = address,
                evidence = listOf(Evidence(EvidenceSource.MDNS, clock.instant(), detail = serviceType)),
                hostnames = serviceName?.let { mapOf(EvidenceSource.MDNS to it) } ?: emptyMap(),
                services =
                    listOf(
                        DiscoveredService(
                            source = EvidenceSource.MDNS,
                            serviceType = serviceType,
                            name = serviceName,
                            detail = null,
                            txtRecords = txtRecords,
                        ),
                    ),
                deviceHint = mdnsServiceHint(serviceType, txtRecords),
            )
        }

        /** design §8.2 - TXT-record values are UTF-8 text by DNS-SD convention (RFC 6763 §6.5);
         * a key with a `null` value is a boolean (presence-only) attribute. */
        private fun NsdServiceInfo.decodeTxtRecords(): Map<String, String> =
            attributes.mapValues { (_, value) ->
                value?.toString(Charsets.UTF_8)?.take(MAX_TXT_VALUE_CHARS) ?: ""
            }

        private companion object {
            const val META_QUERY_SERVICE_TYPE = "_services._dns-sd._udp"
            const val TYPE_DISCOVERY_BUDGET_MS = 1_500L
            const val PER_TYPE_BUDGET_MS = 800L
            const val MIN_PER_TYPE_BUDGET_MS = 400L
            const val MAX_TYPES_ASSUMED = 3
            const val MAX_TXT_VALUE_CHARS = 200
        }
    }
