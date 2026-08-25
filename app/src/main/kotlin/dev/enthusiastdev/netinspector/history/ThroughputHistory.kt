package dev.enthusiastdev.netinspector.history

import dev.enthusiastdev.netinspector.core.model.diagnostics.ThroughputResult
import kotlinx.serialization.Serializable

@Serializable
data class ThroughputResultDto(
    val packetsSent: Int,
    val packetsReceived: Int,
    val lossPercent: Double,
    val avgMbps: Double,
    val peakMbps: Double,
    val durationMs: Long,
)

/** improvement-ideas.md #31's correlation differentiator, snapshotted at test start and end -
 * `null` fields mean the app wasn't connected to Wi-Fi (e.g. running over a different transport)
 * rather than an omitted measurement. */
@Serializable
data class WifiCorrelationDto(
    val ssid: String? = null,
    val bssid: String? = null,
    val rssiDbm: Int? = null,
    val channel: Int? = null,
    val widthMhz: Int? = null,
    val overlappingApCount: Int? = null,
)

@Serializable
data class ThroughputRunPayload(
    val result: ThroughputResultDto,
    val correlationAtStart: WifiCorrelationDto? = null,
    val correlationAtEnd: WifiCorrelationDto? = null,
)

fun ThroughputResult.toDto(): ThroughputResultDto =
    ThroughputResultDto(packetsSent, packetsReceived, lossPercent, avgMbps, peakMbps, durationMs)

fun ThroughputResult.toHistorySummary(): String {
    val avg = "%.1f".format(avgMbps)
    val loss = "%.0f".format(lossPercent)
    return "$avg Mbps avg, $loss% loss"
}
