package dev.enthusiastdev.netinspector.ui.screens.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.enthusiastdev.netinspector.core.common.net.Ipv4Subnet
import dev.enthusiastdev.netinspector.core.model.lan.Host
import dev.enthusiastdev.netinspector.core.model.lan.HostConfidence
import dev.enthusiastdev.netinspector.core.model.lan.SweepOutcome
import dev.enthusiastdev.netinspector.core.model.lan.SweepProgress
import dev.enthusiastdev.netinspector.core.model.lan.nicknameKey
import dev.enthusiastdev.netinspector.data.lan.LanDiscoveryRepository
import dev.enthusiastdev.netinspector.data.persistence.host.SavedHostRepository
import dev.enthusiastdev.netinspector.data.persistence.preferences.LanAcknowledgementRepository
import dev.enthusiastdev.netinspector.data.wifi.ConnectionRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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
        private val savedHostRepository: SavedHostRepository,
    ) : ViewModel() {
        private val pendingConfirmationHostCount = MutableStateFlow<Long?>(null)
        private val sortOrder = MutableStateFlow(DevicesSortOrder.GROUP)
        private val confidenceFilter = MutableStateFlow(HostConfidence.entries.toSet())
        private var sweepJob: Job? = null

        private data class RawState(
            val hosts: List<Host>,
            val progress: SweepProgress,
            val acknowledged: Boolean,
            val pendingConfirmation: Long?,
            val isConnected: Boolean,
        )

        /** docs/ideas.md D - a nickname isn't part of the sweep pipeline
         * at all (no `HostObservation` ever produces one), so it's joined in here rather than
         * touching `mergeObservation`: every host from the live sweep gets overlaid with
         * whatever `SavedHostRepository` has for its `nicknameKey()`, or nothing if unset. */
        private val hostsWithNicknames: Flow<List<Host>> =
            combine(
                lanDiscoveryRepository.hosts,
                savedHostRepository.observeNicknames(),
                savedHostRepository.observeKnownDeviceKeys(),
            ) { hosts, nicknames, knownDeviceKeys ->
                hosts.map { host ->
                    host.copy(
                        nickname = nicknames[host.nicknameKey()],
                        isKnownDevice = host.nicknameKey() in knownDeviceKeys,
                    )
                }
            }

        val uiState =
            combine(
                hostsWithNicknames,
                lanDiscoveryRepository.progress,
                lanAcknowledgementRepository.isAcknowledged,
                pendingConfirmationHostCount,
                connectionRepository.connectionSnapshot,
            ) { hosts, progress, acknowledged, pendingConfirmation, connection ->
                RawState(hosts, progress, acknowledged, pendingConfirmation, connection?.ipv4 != null)
            }.combine(sortOrder) { raw, sort -> raw to sort }
                .combine(confidenceFilter) { (raw, sort), filter ->
                    DevicesUiState.Content(
                        hosts = raw.hosts.filteredByConfidence(filter).sortedForDisplay(sort),
                        allHosts = raw.hosts.sortedForDisplay(sort),
                        progress = raw.progress,
                        isConnected = raw.isConnected,
                        needsAcknowledgement = !raw.acknowledged,
                        pendingConfirmationHostCount = raw.pendingConfirmation,
                        sortOrder = sort,
                        confidenceFilter = filter,
                    )
                }.stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = DevicesUiState.Loading,
                )

        fun setSortOrder(order: DevicesSortOrder) {
            sortOrder.value = order
        }

        fun toggleConfidenceFilter(confidence: HostConfidence) {
            confidenceFilter.update { current ->
                if (confidence in current) current - confidence else current + confidence
            }
        }

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

        /** docs/ideas.md D - [key] is a `Host.nicknameKey()` computed by
         * the caller (it already has the `Host` in hand from the detail screen); a blank
         * [nickname] clears the entry, see `SavedHostRepository.setNickname`. */
        fun setNickname(
            key: String,
            nickname: String,
        ) {
            viewModelScope.launch { savedHostRepository.setNickname(key, nickname) }
        }

        /** ideas.md #24 - marks/unmarks [key] as a "known device," suppressing the
         * periodic background sweep's vanish/reappear alerts for it (`diffLanPresence`,
         * `:core:model`). */
        fun setKnownDevice(
            key: String,
            isKnown: Boolean,
        ) {
            viewModelScope.launch { savedHostRepository.setKnownDevice(key, isKnown) }
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
                            connection.bssid,
                            confirmShortPrefix,
                        )
                    pendingConfirmationHostCount.value = (outcome as? SweepOutcome.NeedsConfirmation)?.hostCount
                }
        }
    }
