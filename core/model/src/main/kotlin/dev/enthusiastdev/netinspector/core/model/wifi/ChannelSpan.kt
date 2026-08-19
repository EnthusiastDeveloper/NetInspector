package dev.enthusiastdev.netinspector.core.model.wifi

data class ChannelSpan(
    val centerMhz: Int,
    val widthMhz: Int, // 20, 40, 80, 160, 320
    val primaryChannel: Int,
    val band: Band,
) {
    val lowMhz get() = centerMhz - widthMhz / 2
    val highMhz get() = centerMhz + widthMhz / 2
}

/** design §6.2 - width constant 5 (320 MHz, Wi-Fi 7) postdates this minSdk, so it's compared
 * as a raw int rather than referencing `ScanResult.CHANNEL_WIDTH_320MHZ`. */
fun channelWidthMhz(platformValue: Int): Int =
    when (platformValue) {
        0 -> 20 // CHANNEL_WIDTH_20MHZ
        1 -> 40 // CHANNEL_WIDTH_40MHZ
        2, 4 -> 80 // CHANNEL_WIDTH_80MHZ and _80MHZ_PLUS_MHZ (80+80 - each segment is 80 MHz)
        3 -> 160 // CHANNEL_WIDTH_160MHZ
        5 -> 320 // CHANNEL_WIDTH_320MHZ
        else -> 20
    }
