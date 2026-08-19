package dev.enthusiastdev.netinspector.core.model.wifi

enum class SecurityType { OPEN, OWE, WEP, WPA2, WPA3, EAP, UNKNOWN }

/**
 * Maps `WifiInfo.SECURITY_TYPE_*` / `ScanResult.getSecurityTypes()` (API 33+) raw values.
 * Takes a plain `Int` rather than the platform constant so this module stays free of
 * `android.*` imports (design §2.1) - callers in `:data:wifi` pass the raw int through.
 */
fun securityTypeOf(platformValue: Int): SecurityType =
    when (platformValue) {
        0 -> SecurityType.OPEN // SECURITY_TYPE_OPEN
        1 -> SecurityType.WEP // SECURITY_TYPE_WEP
        2 -> SecurityType.WPA2 // SECURITY_TYPE_PSK
        3 -> SecurityType.EAP // SECURITY_TYPE_EAP
        4 -> SecurityType.WPA3 // SECURITY_TYPE_SAE
        5 -> SecurityType.EAP // SECURITY_TYPE_EAP_WPA3_ENTERPRISE_192_BIT
        6 -> SecurityType.OWE // SECURITY_TYPE_OWE
        9 -> SecurityType.EAP // SECURITY_TYPE_EAP_WPA3_ENTERPRISE
        11, 12 -> SecurityType.EAP // SECURITY_TYPE_PASSPOINT_R1_R2 / _R3 - EAP-based
        else -> SecurityType.UNKNOWN // WAPI_*, OSEN, DPP, and -1 (unknown)
    }

/**
 * `getSecurityTypes()` can return more than one value for transition networks (e.g. both
 * PSK and SAE for WPA2/WPA3 transition mode) - never collapsed to a single value here; the
 * UI decides how to label the combination (design §6.2).
 */
fun securityTypesOf(platformValues: IntArray): Set<SecurityType> = platformValues.map(::securityTypeOf).toSet()
