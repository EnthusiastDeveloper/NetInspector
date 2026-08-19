package dev.enthusiastdev.netinspector.history

import dev.enthusiastdev.netinspector.core.common.icmp.PingSummary
import dev.enthusiastdev.netinspector.core.model.diagnostics.PingProbeResult
import kotlinx.serialization.Serializable

@Serializable
data class PingProbeDto(
    val sequence: Int,
    val tier: String,
    val kind: String,
    val rttMs: Double? = null,
    val message: String? = null,
)

@Serializable
data class PingSummaryDto(
    val sent: Int,
    val received: Int,
    val minMs: Double?,
    val avgMs: Double?,
    val maxMs: Double?,
    val medianMs: Double?,
    val stddevMs: Double?,
    val jitterMs: Double?,
    val lossPercent: Double,
)

@Serializable
data class PingRunPayload(
    val probes: List<PingProbeDto>,
    val summary: PingSummaryDto,
)

fun PingProbeResult.toDto(): PingProbeDto =
    when (this) {
        is PingProbeResult.Reply -> PingProbeDto(sequence, tier.name, "REPLY", rttMs = rttMs)
        is PingProbeResult.Timeout -> PingProbeDto(sequence, tier.name, "TIMEOUT")
        is PingProbeResult.Error -> PingProbeDto(sequence, tier.name, "ERROR", message = message)
    }

fun PingSummary.toDto(): PingSummaryDto =
    PingSummaryDto(sent, received, minMs, avgMs, maxMs, medianMs, stddevMs, jitterMs, lossPercent)

fun PingSummary.toHistorySummary(): String {
    val avg = avgMs?.let { "%.1f".format(it) } ?: "-"
    val loss = "%.0f".format(lossPercent)
    return "$received/$sent replies, avg $avg ms, $loss% loss"
}
