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
import dev.enthusiastdev.netinspector.data.wifi.ConnectionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ConnectionViewModel
    @Inject
    constructor(
        connectionRepository: ConnectionRepository,
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
            locationAccessTrigger
                .flatMapLatest {
                    connectionRepository.connectionSnapshot.map { snapshot ->
                        if (snapshot == null) {
                            ConnectionUiState.Disconnected
                        } else {
                            ConnectionUiState.Connected(snapshot, currentLocationAccessState())
                        }
                    }
                }.stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = ConnectionUiState.Loading,
                )

        fun refreshLocationAccess() {
            locationAccessTrigger.update { it + 1 }
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
