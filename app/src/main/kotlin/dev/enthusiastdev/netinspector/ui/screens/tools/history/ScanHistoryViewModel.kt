package dev.enthusiastdev.netinspector.ui.screens.tools.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.enthusiastdev.netinspector.data.persistence.preferences.RetentionSettingsRepository
import dev.enthusiastdev.netinspector.data.persistence.scan.RssiHistoryPoint
import dev.enthusiastdev.netinspector.data.persistence.scan.ScanHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltViewModel
class ScanHistoryViewModel
    @Inject
    constructor(
        private val scanHistoryRepository: ScanHistoryRepository,
    ) : ViewModel() {
        val uiState: StateFlow<ScanHistoryUiState> =
            scanHistoryRepository
                .knownAps()
                .map { ScanHistoryUiState(knownAps = it) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScanHistoryUiState())

        /** design §11.1 History - "RSSI history per BSSID," windowed to the scan-retention
         * default rather than unbounded: older sessions get swept by the retention worker
         * anyway (design §10 decision #4), so an unbounded query would just read as a flat gap
         * once history exceeds that window. */
        fun rssiHistory(bssid: String): Flow<List<RssiHistoryPoint>> {
            val since =
                Instant.now().minus(
                    RetentionSettingsRepository.DEFAULT_SCAN_RETENTION_DAYS.toLong(),
                    ChronoUnit.DAYS,
                )
            return scanHistoryRepository.rssiHistory(bssid, since)
        }
    }
