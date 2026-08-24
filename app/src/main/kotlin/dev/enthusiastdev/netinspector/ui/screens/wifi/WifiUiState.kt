package dev.enthusiastdev.netinspector.ui.screens.wifi

import dev.enthusiastdev.netinspector.core.model.settings.RssiDisplayUnit
import dev.enthusiastdev.netinspector.core.model.wifi.AccessPoint
import dev.enthusiastdev.netinspector.core.model.wifi.ChannelSpan
import dev.enthusiastdev.netinspector.core.model.wifi.ScanBudget
import dev.enthusiastdev.netinspector.data.persistence.scan.KnownApEntity
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
        val rssiDisplayUnit: RssiDisplayUnit = RssiDisplayUnit.DBM,
        /** The BSSID and channel span this device is currently associated on, when it is - the
         * channel recommendation compares against them, and excludes this AP from its scoring
         * (see `channelAdvice`). Null when not connected to Wi-Fi, or while location access is
         * withheld and the OS redacts the connected network's BSSID (C-04). */
        val connectedBssid: String? = null,
        val connectedSpan: ChannelSpan? = null,
        /** improvement-ideas.md #11 - known APs with a detected capability change, keyed by
         * BSSID; only entries with a non-null `lastCapabilityChangeMillis` are included, so
         * this stays empty for the common case. */
        val apCapabilityChanges: Map<String, KnownApEntity> = emptyMap(),
    ) : WifiUiState
}
