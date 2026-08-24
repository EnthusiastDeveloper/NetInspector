package dev.enthusiastdev.netinspector.ui.screens.tools.wifichanges

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.enthusiastdev.netinspector.core.model.wifi.ObservedAp
import dev.enthusiastdev.netinspector.core.model.wifi.diffScanSessions
import dev.enthusiastdev.netinspector.data.persistence.scan.ScanHistoryRepository
import dev.enthusiastdev.netinspector.data.persistence.scan.ScanObservationEntity
import dev.enthusiastdev.netinspector.data.persistence.scan.ScanSessionEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** improvement-ideas.md #6 - no new repository methods: [ScanHistoryRepository.recentSessions]
 * already gives the picker everything it needs, and [ScanHistoryRepository.observationsForSession]
 * called once per side is all the diff needs (design §2.2 - a use case would be noise here,
 * this is one repository's data reshaped for one pure function, not logic spanning two). */
@HiltViewModel
class WifiChangesViewModel
    @Inject
    constructor(
        private val scanHistoryRepository: ScanHistoryRepository,
    ) : ViewModel() {
        private val before = MutableStateFlow<ScanSessionEntity?>(null)
        private val after = MutableStateFlow<ScanSessionEntity?>(null)

        @OptIn(ExperimentalCoroutinesApi::class)
        private val diff =
            combine(before, after) { a, b -> a to b }
                .flatMapLatest { (beforeSession, afterSession) ->
                    if (beforeSession == null || afterSession == null) {
                        flowOf(null)
                    } else {
                        combine(
                            scanHistoryRepository.observationsForSession(beforeSession.id),
                            scanHistoryRepository.observationsForSession(afterSession.id),
                        ) { beforeObservations, afterObservations ->
                            diffScanSessions(
                                before = beforeObservations.map { it.toObservedAp() },
                                after = afterObservations.map { it.toObservedAp() },
                            )
                        }
                    }
                }

        val uiState: StateFlow<WifiChangesUiState> =
            combine(
                scanHistoryRepository.recentSessions(),
                before,
                after,
                diff,
            ) { sessions, beforeSession, afterSession, diff ->
                WifiChangesUiState(
                    recentSessions = sessions,
                    before = beforeSession,
                    after = afterSession,
                    diff = diff,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WifiChangesUiState())

        fun selectBefore(session: ScanSessionEntity) {
            before.value = session
        }

        fun selectAfter(session: ScanSessionEntity) {
            after.value = session
        }
    }

private fun ScanObservationEntity.toObservedAp() =
    ObservedAp(
        bssid = bssid,
        ssid = ssid,
        rssiDbm = rssiDbm,
        band = band,
        centerFrequencyMhz = centerFrequencyMhz,
        channelWidthMhz = channelWidthMhz,
        security = security,
        standard = standard,
    )
