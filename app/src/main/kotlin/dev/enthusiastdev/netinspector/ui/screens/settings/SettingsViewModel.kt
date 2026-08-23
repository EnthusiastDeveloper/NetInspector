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
import dev.enthusiastdev.netinspector.debug.CrashReportStore
import dev.enthusiastdev.netinspector.debug.DebugBundleBuilder
import dev.enthusiastdev.netinspector.debug.ShareFileLauncher
import dev.enthusiastdev.netinspector.monitoring.ConnectionAlertSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
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
        private val crashReportStore: CrashReportStore,
        private val debugBundleBuilder: DebugBundleBuilder,
        @ApplicationContext private val context: Context,
    ) : ViewModel() {
        // improvement-ideas.md #21 - a crash written since this screen was last visited is
        // filesystem state, not a Flow this ViewModel already observes, so it's re-checked on
        // resume via this trigger.
        private val crashReportAvailabilityTrigger = MutableStateFlow(0)

        fun refreshCrashReportAvailability() {
            crashReportAvailabilityTrigger.update { it + 1 }
        }

        private val baseSettings =
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
            }

        // Kotlin's fixed-arity combine() overloads top out below the 9 flows this screen now
        // reads from, so the alert settings are combined separately and merged into baseSettings
        // below rather than growing one combine() call past its available overload.
        private val alertSettings =
            combine(
                appSettingsRepository.rssiAlertThresholdDbm,
                appSettingsRepository.alertOnRssiDrop,
                appSettingsRepository.alertOnDisconnect,
                appSettingsRepository.alertOnReconnect,
                ::ConnectionAlertSettings,
            )

        val uiState: StateFlow<SettingsUiState> =
            baseSettings
                .combine(alertSettings) { state, alerts ->
                    state.copy(
                        rssiAlertThresholdDbm = alerts.rssiAlertThresholdDbm,
                        alertOnRssiDrop = alerts.alertOnRssiDrop,
                        alertOnDisconnect = alerts.alertOnDisconnect,
                        alertOnReconnect = alerts.alertOnReconnect,
                    )
                }.combine(appSettingsRepository.crashReportingEnabled) { state, crashReportingEnabled ->
                    state.copy(crashReportingEnabled = crashReportingEnabled)
                }.combine(
                    crashReportAvailabilityTrigger.map { crashReportStore.hasReports() },
                ) { state, hasCrashReports ->
                    state.copy(hasCrashReports = hasCrashReports)
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

        /** RSSI never reads outside roughly -100..0 dBm in practice - clamped here for the same
         * reason retention days are clamped above: the number field can't refuse out-of-range
         * input on its own. */
        fun setRssiAlertThresholdDbm(thresholdDbm: Int) {
            viewModelScope.launch {
                appSettingsRepository.setRssiAlertThresholdDbm(thresholdDbm.coerceIn(-100, -1))
            }
        }

        fun setAlertOnRssiDrop(enabled: Boolean) {
            viewModelScope.launch { appSettingsRepository.setAlertOnRssiDrop(enabled) }
        }

        fun setAlertOnDisconnect(enabled: Boolean) {
            viewModelScope.launch { appSettingsRepository.setAlertOnDisconnect(enabled) }
        }

        fun setAlertOnReconnect(enabled: Boolean) {
            viewModelScope.launch { appSettingsRepository.setAlertOnReconnect(enabled) }
        }

        fun setCrashReportingEnabled(enabled: Boolean) {
            viewModelScope.launch { appSettingsRepository.setCrashReportingEnabled(enabled) }
        }

        fun exportCrashReport() {
            viewModelScope.launch {
                crashReportStore.latestReport()?.let {
                    ShareFileLauncher.share(context, it, "text/plain", "Share crash report")
                }
            }
        }

        fun exportDebugBundle() {
            viewModelScope.launch {
                val zip = debugBundleBuilder.build()
                ShareFileLauncher.share(context, zip, "application/zip", "Share debug bundle")
            }
        }
    }
