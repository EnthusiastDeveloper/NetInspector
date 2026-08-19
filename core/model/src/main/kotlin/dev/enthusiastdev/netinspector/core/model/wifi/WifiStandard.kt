package dev.enthusiastdev.netinspector.core.model.wifi

enum class WifiStandard { LEGACY, N, AC, AX, BE, UNKNOWN }

/**
 * Maps `WifiInfo.getWifiStandard()` / `ScanResult.getWifiStandard()` (API 29+/30+) raw values.
 * Takes a plain `Int` rather than the platform constant so this module stays free of
 * `android.*` imports (design §2.1) - callers in `:data:wifi` pass the raw int through.
 */
fun wifiStandardOf(platformValue: Int): WifiStandard =
    when (platformValue) {
        1 -> WifiStandard.LEGACY // WIFI_STANDARD_LEGACY
        4 -> WifiStandard.N // WIFI_STANDARD_11N
        5 -> WifiStandard.AC // WIFI_STANDARD_11AC
        6 -> WifiStandard.AX // WIFI_STANDARD_11AX
        8 -> WifiStandard.BE // WIFI_STANDARD_11BE
        else -> WifiStandard.UNKNOWN // includes WIFI_STANDARD_UNKNOWN(0) and 11AD(7, 60GHz WiGig - out of scope)
    }
