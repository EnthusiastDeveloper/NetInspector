package dev.enthusiastdev.netinspector.ui.screens.wifi

import dev.enthusiastdev.netinspector.core.model.wifi.AccessPoint
import dev.enthusiastdev.netinspector.core.model.wifi.ScanBudget
import java.time.Instant

/** design §6.1 - unlike the dashboard's [dev.enthusiastdev.netinspector.ui.screens.connection.LocationAccessState],
 * there's no "services disabled" analogue here: `NEARBY_WIFI_DEVICES` is the only gate (C-03). */
enum class WifiAccessState { GRANTED, PERMISSION_NEEDED }

sealed interface WifiUiState {
    data object Loading : WifiUiState

    data class Content(
        val accessPoints: List<AccessPoint>,
        val wifiAccess: WifiAccessState,
        val budget: ScanBudget,
        val lastUpdated: Instant?,
    ) : WifiUiState
}
