package dev.enthusiastdev.netinspector.ui.screens.connection

import dev.enthusiastdev.netinspector.core.model.connection.ConnectionSnapshot
import dev.enthusiastdev.netinspector.core.model.settings.RssiDisplayUnit

/**
 * Two independent gates on the dashboard's SSID/BSSID fields (design §4.1a, C-04) - kept
 * distinct rather than collapsed into one "unknown" state, since the fix differs: request the
 * permission, or deep-link to system location settings.
 */
enum class LocationAccessState { GRANTED, PERMISSION_NEEDED, SERVICES_DISABLED }

sealed interface ConnectionUiState {
    data object Loading : ConnectionUiState

    data object Disconnected : ConnectionUiState

    data class Connected(
        val snapshot: ConnectionSnapshot,
        val locationAccess: LocationAccessState,
        val rssiDisplayUnit: RssiDisplayUnit = RssiDisplayUnit.DBM,
    ) : ConnectionUiState
}
