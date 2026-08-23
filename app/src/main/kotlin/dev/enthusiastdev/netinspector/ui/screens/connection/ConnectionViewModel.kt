package dev.enthusiastdev.netinspector.ui.screens.connection

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.enthusiastdev.netinspector.data.persistence.preferences.AppSettingsRepository
import dev.enthusiastdev.netinspector.data.wifi.ConnectionRepository
import dev.enthusiastdev.netinspector.monitoring.MonitoringController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ConnectionViewModel
    @Inject
    constructor(
        connectionRepository: ConnectionRepository,
        private val monitoringController: MonitoringController,
        private val appSettingsRepository: AppSettingsRepository,
        @ApplicationContext private val context: Context,
    ) : ViewModel() {
        // Granting location access - whether via the in-app prompt or the system Settings
        // app - changes nothing about the network itself, so it never fires
        // onCapabilitiesChanged on its own: WifiInfo redaction is computed by the OS at
        // *callback dispatch* time from the app's permission state then, not re-evaluated
        // later. The UI calls refreshLocationAccess() on ON_RESUME; flatMapLatest forces a
        // full NetworkCallback re-registration so the OS redelivers a freshly-unredacted
        // WifiInfo rather than replaying whatever was cached from before the grant.
        private val locationAccessTrigger = MutableStateFlow(0)

        val uiState: StateFlow<ConnectionUiState> =
            combine(
                locationAccessTrigger.flatMapLatest { connectionRepository.connectionSnapshot },
                appSettingsRepository.rssiDisplayUnit,
            ) { snapshot, rssiDisplayUnit ->
                if (snapshot == null) {
                    ConnectionUiState.Disconnected
                } else {
                    ConnectionUiState.Connected(snapshot, currentLocationAccessState(), rssiDisplayUnit)
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = ConnectionUiState.Loading,
            )

        fun refreshLocationAccess() {
            locationAccessTrigger.update { it + 1 }
        }

        // Mirrors locationAccessTrigger's shape: POST_NOTIFICATIONS granted via the system
        // Settings app (after a permanent denial) doesn't fire any callback this ViewModel
        // would otherwise observe, so ON_RESUME has to force a re-check explicitly.
        private val notificationAccessTrigger = MutableStateFlow(0)

        val monitoringState: StateFlow<MonitoringUiState> =
            combine(
                monitoringController.isRunning,
                notificationAccessTrigger,
            ) { isRunning, _ ->
                MonitoringUiState(isRunning, context.currentNotificationAccessState())
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = MonitoringUiState(isRunning = false, NotificationAccessState.PERMISSION_NEEDED),
            )

        fun refreshNotificationAccess() {
            notificationAccessTrigger.update { it + 1 }
        }

        fun startMonitoring() {
            val granted = context.currentNotificationAccessState() == NotificationAccessState.GRANTED
            if (granted) monitoringController.start()
        }

        fun stopMonitoring() {
            monitoringController.stop()
        }

        private fun currentLocationAccessState(): LocationAccessState {
            val hasPermission =
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
            if (!hasPermission) return LocationAccessState.PERMISSION_NEEDED

            val locationEnabled = context.getSystemService(LocationManager::class.java)?.isLocationEnabled == true
            return if (locationEnabled) LocationAccessState.GRANTED else LocationAccessState.SERVICES_DISABLED
        }
    }
