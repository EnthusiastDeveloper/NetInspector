package dev.enthusiastdev.netinspector.ui.screens.connection

import dev.enthusiastdev.netinspector.core.model.connection.ConnectionSnapshot

sealed interface ConnectionUiState {
    data object Loading : ConnectionUiState

    data object Disconnected : ConnectionUiState

    /**
     * `hasScanPermission` gates SSID/BSSID display only - every other field works without it
     * (design plan Phase 1 risk note, C-03). It is *not* a request to grant the permission;
     * that flow belongs to Phase 3.
     */
    data class Connected(
        val snapshot: ConnectionSnapshot,
        val hasScanPermission: Boolean,
    ) : ConnectionUiState
}
