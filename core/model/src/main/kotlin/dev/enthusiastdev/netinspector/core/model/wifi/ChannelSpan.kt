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
