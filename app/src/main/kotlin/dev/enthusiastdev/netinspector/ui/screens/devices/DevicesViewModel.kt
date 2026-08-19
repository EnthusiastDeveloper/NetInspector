package dev.enthusiastdev.netinspector.ui.screens.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.enthusiastdev.netinspector.core.common.net.Ipv4Subnet
import dev.enthusiastdev.netinspector.core.model.lan.Host
import dev.enthusiastdev.netinspector.core.model.lan.HostConfidence
import dev.enthusiastdev.netinspector.core.model.lan.SweepOutcome
import dev.enthusiastdev.netinspector.data.lan.LanDiscoveryRepository
import dev.enthusiastdev.netinspector.data.persistence.preferences.LanAcknowledgementRepository
import dev.enthusiastdev.netinspector.data.wifi.ConnectionRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.net.Inet4Address
import javax.inject.Inject

@HiltViewModel
class DevicesViewModel
    @Inject
    constructor(
        private val connectionRepository: ConnectionRepository,
        private val lanDiscoveryRepository: LanDiscoveryRepository,
        private val lanAcknowledgementRepository: LanAcknowledgementRepository,
    ) : ViewModel() {
        private val pendingConfirmationHostCount = MutableStateFlow<Long?>(null)
        private var sweepJob: Job? = null

        val uiState =
            combine(
                lanDiscoveryRepository.hosts,
                lanDiscoveryRepository.progress,
                lanAcknowledgementRepository.isAcknowledged,
                pendingConfirmationHostCount,
                connectionRepository.connectionSnapshot,
            ) { hosts, progress, acknowledged, pendingConfirmation, connection ->
                DevicesUiState.Content(
                    hosts = hosts.sortedForDisplay(),
                    progress = progress,
                    isConnected = connection?.ipv4 != null,
                    needsAcknowledgement = !acknowledged,
                    pendingConfirmationHostCount = pendingConfirmation,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = DevicesUiState.Loading,
            )

        /** design §11.4 - the ack dialog gates the *first sweep*, not screen entry, so this is
         * called from the "Scan" action rather than an `onResumed()`-style lifecycle hook. */
        fun onScanRequested() {
            viewModelScope.launch {
                if (!lanAcknowledgementRepository.isAcknowledged.first()) return@launch
                startSweep()
            }
        }

        fun acknowledgeAndStartSweep() {
            viewModelScope.launch {
                lanAcknowledgementRepository.acknowledge()
                startSweep()
            }
        }

        fun confirmShortPrefixSweep() {
            startSweep(confirmShortPrefix = true)
        }

        fun dismissConfirmation() {
            pendingConfirmationHostCount.value = null
        }

        fun cancelSweep() {
            sweepJob?.cancel()
        }

        private fun startSweep(confirmShortPrefix: Boolean = false) {
            if (sweepJob?.isActive == true) return
            sweepJob =
                viewModelScope.launch {
                    // `connectionSnapshot` emits `null` until *both* onCapabilitiesChanged and
                    // onLinkPropertiesChanged have fired at least once (ConnectivityDataSource) -
                    // harmless for a continuously-observed StateFlow, but a plain `.first()` here
                    // can race and grab that premature null instead of the real snapshot. Wait
                    // specifically for one with IPv4 info.
                    val connection = connectionRepository.connectionSnapshot.first { it?.ipv4 != null }!!
                    val ipv4 = connection.ipv4!!
                    val selfAddress = ipv4.address as? Inet4Address ?: return@launch
                    val subnet = Ipv4Subnet(selfAddress, ipv4.prefixLength)

                    val outcome =
                        lanDiscoveryRepository.sweep(
                            subnet,
                            connection.gateway,
                            selfAddress,
                            confirmShortPrefix,
                        )
                    pendingConfirmationHostCount.value = (outcome as? SweepOutcome.NeedsConfirmation)?.hostCount
                }
        }
    }

/** Confirmed first, most useful; announced next; stale last since design §8.3 wants it
 * visually and positionally de-emphasised. Numeric within each group, not lexicographic. */
private fun List<Host>.sortedForDisplay(): List<Host> =
    sortedWith(
        compareBy(
            { host -> if (host.isSelf || host.isGateway) 0 else 1 },
            { host -> host.confidence.sortOrder() },
            { host -> host.address.toSortableString() },
        ),
    )

private fun HostConfidence.sortOrder(): Int =
    when (this) {
        HostConfidence.CONFIRMED -> 0
        HostConfidence.ANNOUNCED -> 1
        HostConfidence.STALE -> 2
    }

private fun Inet4Address.toSortableString(): String =
    address.joinToString(".") {
        (it.toInt() and 0xFF).toString().padStart(3, '0')
    }
