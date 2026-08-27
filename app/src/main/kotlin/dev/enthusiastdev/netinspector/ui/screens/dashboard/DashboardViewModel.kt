package dev.enthusiastdev.netinspector.ui.screens.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.enthusiastdev.netinspector.data.lan.LanDiscoveryRepository
import dev.enthusiastdev.netinspector.data.persistence.preferences.AppSettingsRepository
import dev.enthusiastdev.netinspector.data.wifi.ConnectionRepository
import dev.enthusiastdev.netinspector.debug.CrashReportStore
import dev.enthusiastdev.netinspector.debug.ShareFileLauncher
import dev.enthusiastdev.netinspector.monitoring.MonitoringController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** design idea #14 - "network health at a glance," combining the three repositories every
 * other tab already reads individually rather than adding a fourth one of its own. */
@HiltViewModel
class DashboardViewModel
    @Inject
    constructor(
        connectionRepository: ConnectionRepository,
        lanDiscoveryRepository: LanDiscoveryRepository,
        monitoringController: MonitoringController,
        private val appSettingsRepository: AppSettingsRepository,
        private val crashReportStore: CrashReportStore,
        @ApplicationContext private val context: Context,
    ) : ViewModel() {
        // ideas.md #21 - "auto-detect the crash on the next app start": a crash
        // always kills the process, so a fresh DashboardViewModel is created on every process
        // start regardless of navigation state - reading the latest report once here, rather
        // than on a resume trigger like SettingsViewModel's checks, is exactly "next app start."
        private val pendingCrashReportFilename: Flow<String?> =
            combine(
                flow { emit(crashReportStore.latestReport()?.name) },
                appSettingsRepository.lastAcknowledgedCrashReport,
            ) { latestFilename, acknowledgedFilename ->
                latestFilename?.takeIf { it != acknowledgedFilename }
            }

        val uiState =
            combine(
                connectionRepository.connectionSnapshot,
                appSettingsRepository.rssiDisplayUnit,
                lanDiscoveryRepository.hosts,
                lanDiscoveryRepository.progress,
                monitoringController.isRunning,
            ) { connection, rssiDisplayUnit, hosts, sweepProgress, isMonitoringActive ->
                DashboardUiState.Content(
                    connection = connection,
                    rssiDisplayUnit = rssiDisplayUnit,
                    hostCount = hosts.size,
                    sweepProgress = sweepProgress,
                    isMonitoringActive = isMonitoringActive,
                )
            }.combine(pendingCrashReportFilename) { state, pendingFilename ->
                state.copy(pendingCrashReportFilename = pendingFilename)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = DashboardUiState.Loading,
            )

        /** Shares the pending report, same as Settings' own export action, then acknowledges
         * it so the prompt doesn't reappear for this same report. */
        fun exportCrashReport() {
            viewModelScope.launch {
                crashReportStore.latestReport()?.let { file ->
                    ShareFileLauncher.share(context, file, "text/plain", "Share crash report")
                    appSettingsRepository.setLastAcknowledgedCrashReport(file.name)
                }
            }
        }

        /** Acknowledges the pending report without sharing it - it's still reachable later via
         * Settings' "Export crash report," this only clears the dashboard prompt. */
        fun dismissCrashReport() {
            viewModelScope.launch {
                crashReportStore.latestReport()?.let { file ->
                    appSettingsRepository.setLastAcknowledgedCrashReport(file.name)
                }
            }
        }
    }
