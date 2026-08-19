package dev.enthusiastdev.netinspector.ui.screens.connection

import dev.enthusiastdev.netinspector.core.model.connection.ConnectionSnapshot
import dev.enthusiastdev.netinspector.core.model.wifi.Band
import dev.enthusiastdev.netinspector.core.model.wifi.WifiStandard

internal fun ConnectionSnapshot.ssidLabel(locationAccess: LocationAccessState): String =
    ssid ?: locationAccess.unknownLabel("Hidden network")

internal fun ConnectionSnapshot.bssidLabel(locationAccess: LocationAccessState): String =
    bssid ?: locationAccess.unknownLabel("Unknown")

private fun LocationAccessState.unknownLabel(whenGranted: String): String =
    when (this) {
        LocationAccessState.GRANTED -> whenGranted
        LocationAccessState.PERMISSION_NEEDED -> "<permission required>"
        LocationAccessState.SERVICES_DISABLED -> "<location services off>"
    }

internal fun Band.label(): String =
    when (this) {
        Band.GHZ_2_4 -> "2.4 GHz"
        Band.GHZ_5 -> "5 GHz"
        Band.GHZ_6 -> "6 GHz"
        Band.UNKNOWN -> "Unknown"
    }

internal fun WifiStandard.label(): String =
    when (this) {
        WifiStandard.LEGACY -> "Legacy (802.11a/b/g)"
        WifiStandard.N -> "Wi-Fi 4 (802.11n)"
        WifiStandard.AC -> "Wi-Fi 5 (802.11ac)"
        WifiStandard.AX -> "Wi-Fi 6 (802.11ax)"
        WifiStandard.BE -> "Wi-Fi 7 (802.11be)"
        WifiStandard.UNKNOWN -> "Unknown"
    }
