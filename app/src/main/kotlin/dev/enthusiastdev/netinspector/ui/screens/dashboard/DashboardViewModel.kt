package dev.enthusiastdev.netinspector.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.enthusiastdev.netinspector.data.lan.LanDiscoveryRepository
import dev.enthusiastdev.netinspector.data.persistence.preferences.AppSettingsRepository
import dev.enthusiastdev.netinspector.data.wifi.ConnectionRepository
import dev.enthusiastdev.netinspector.monitoring.MonitoringController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
        appSettingsRepository: AppSettingsRepository,
    ) : ViewModel() {
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
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = DashboardUiState.Loading,
            )
    }
