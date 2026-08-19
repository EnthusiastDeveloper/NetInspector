package dev.enthusiastdev.netinspector.ui.screens.wifi

import dev.enthusiastdev.netinspector.core.model.wifi.AccessPoint
import dev.enthusiastdev.netinspector.core.model.wifi.ScanBudget
import java.time.Instant

/** design §4.1, C-03 - the same gate as the dashboard's
 * [dev.enthusiastdev.netinspector.ui.screens.connection.LocationAccessState]: `getScanResults()`/
 * `startScan()` require `ACCESS_FINE_LOCATION` plus system location mode, not
 * `NEARBY_WIFI_DEVICES` as first assumed. Kept as its own enum (not shared with the
 * dashboard's) since the two screens run independent permission checks. */
enum class WifiAccessState { GRANTED, PERMISSION_NEEDED, SERVICES_DISABLED }

sealed interface WifiUiState {
    data object Loading : WifiUiState

    data class Content(
        val accessPoints: List<AccessPoint>,
        val sampleCount: Int,
        val wifiAccess: WifiAccessState,
        val budget: ScanBudget,
        val lastUpdated: Instant?,
    ) : WifiUiState
}
