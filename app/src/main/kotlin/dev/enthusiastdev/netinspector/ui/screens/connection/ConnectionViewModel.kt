package dev.enthusiastdev.netinspector.ui.screens.connection

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.enthusiastdev.netinspector.data.wifi.ConnectionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ConnectionViewModel
    @Inject
    constructor(
        connectionRepository: ConnectionRepository,
        @ApplicationContext private val context: Context,
    ) : ViewModel() {
        val uiState: StateFlow<ConnectionUiState> =
            connectionRepository.connectionSnapshot
                .map { snapshot ->
                    if (snapshot == null) {
                        ConnectionUiState.Disconnected
                    } else {
                        ConnectionUiState.Connected(snapshot, hasScanPermission())
                    }
                }.stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = ConnectionUiState.Loading,
                )

        private fun hasScanPermission(): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) ==
                PackageManager.PERMISSION_GRANTED
    }
