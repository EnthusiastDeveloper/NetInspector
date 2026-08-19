package dev.enthusiastdev.netinspector.core.common.wifi

/**
 * Linear dBm-to-percent mapping over the same −100…−30 dBm range the channel graph's Y axis
 * uses (design §7.1), so the dashboard gauge and the future channel graph read consistently.
 */
fun rssiToQualityPercent(rssiDbm: Int): Int = (((rssiDbm + 100) * 100) / 70).coerceIn(0, 100)
