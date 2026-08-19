package dev.enthusiastdev.netinspector.core.model.wifi

import java.time.Instant

/** design §3 - one row per BSSID, refreshed in place on each scan rather than re-created. */
data class AccessPoint(
    val bssid: String,
    val ssid: String,
    val rssiDbm: Int,
    val span: ChannelSpan,
    val secondarySpan: ChannelSpan?,
    val security: Set<SecurityType>,
    val standard: WifiStandard,
    val vendor: String?,
    val isConnected: Boolean,
    val isDfsChannel: Boolean,
    val is6GhzPsc: Boolean,
    val firstSeen: Instant,
    val lastSeen: Instant,
)
