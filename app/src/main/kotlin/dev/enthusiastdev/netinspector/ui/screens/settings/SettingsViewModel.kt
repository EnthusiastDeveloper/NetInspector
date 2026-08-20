package dev.enthusiastdev.netinspector.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.enthusiastdev.netinspector.core.model.diagnostics.PortSelection
import dev.enthusiastdev.netinspector.core.model.settings.RssiDisplayUnit
import dev.enthusiastdev.netinspector.core.model.settings.ThemeMode
import dev.enthusiastdev.netinspector.data.persistence.preferences.AppSettingsRepository
import dev.enthusiastdev.netinspector.data.persistence.preferences.RetentionSettingsRepository
import dev.enthusiastdev.netinspector.ui.screens.connection.currentNotificationAccessState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val appSettingsRepository: AppSettingsRepository,
        private val retentionSettingsRepository: RetentionSettingsRepository,
        @ApplicationContext private val context: Context,
    ) : ViewModel() {
        // Mirrors ConnectionViewModel's own trigger: granting POST_NOTIFICATIONS via the system
        // Settings app (after a permanent denial) fires no callback this ViewModel would
        // otherwise observe, so ON_RESUME has to force a re-check explicitly.
        private val notificationAccessTrigger = MutableStateFlow(0)

        fun refreshNotificationAccess() {
            notificationAccessTrigger.update { it + 1 }
        }

        val uiState: StateFlow<SettingsUiState> =
            combine(
                appSettingsRepository.themeMode,
                appSettingsRepository.rssiDisplayUnit,
                retentionSettingsRepository.scanHistoryRetentionDays,
                retentionSettingsRepository.diagnosticHistoryRetentionDays,
                appSettingsRepository.defaultPortSelection,
            ) { themeMode, rssiUnit, scanRetention, diagnosticRetention, portSelection ->
                SettingsUiState(
                    themeMode = themeMode,
                    rssiDisplayUnit = rssiUnit,
                    scanHistoryRetentionDays = scanRetention,
                    diagnosticHistoryRetentionDays = diagnosticRetention,
                    defaultPortSelection = portSelection,
                )
            }.combine(notificationAccessTrigger) { state, _ ->
                state.copy(monitoringNotificationAccess = context.currentNotificationAccessState())
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

        fun setThemeMode(mode: ThemeMode) {
            viewModelScope.launch { appSettingsRepository.setThemeMode(mode) }
        }

        fun setRssiDisplayUnit(unit: RssiDisplayUnit) {
            viewModelScope.launch { appSettingsRepository.setRssiDisplayUnit(unit) }
        }

        fun setDefaultPortSelection(selection: PortSelection) {
            viewModelScope.launch { appSettingsRepository.setDefaultPortSelection(selection) }
        }

        /** Retention days below 1 would mean "delete everything on every cleanup pass" - the
         * screen's number field can't itself refuse invalid input the way a slider would, so
         * clamping happens here rather than trusting the UI layer. */
        fun setScanHistoryRetentionDays(days: Int) {
            viewModelScope.launch { retentionSettingsRepository.setScanHistoryRetentionDays(days.coerceIn(1, 365)) }
        }

        fun setDiagnosticHistoryRetentionDays(days: Int) {
            viewModelScope.launch {
                retentionSettingsRepository.setDiagnosticHistoryRetentionDays(days.coerceIn(1, 365))
            }
        }
    }
