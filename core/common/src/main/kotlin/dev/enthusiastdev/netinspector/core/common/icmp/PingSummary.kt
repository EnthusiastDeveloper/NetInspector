package dev.enthusiastdev.netinspector.core.common.icmp

import dev.enthusiastdev.netinspector.core.model.diagnostics.PingTier
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * design §9.2 - median is reported alongside the mean because a single power-save-induced
 * outlier badly distorts the mean on Wi-Fi, and this app ranks accuracy first. Jitter is the
 * mean absolute successive difference (RFC 3550 §6.4.1 style, not the RFC's running estimate).
 */
data class PingSummary(
    val tier: PingTier,
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

fun summarizePing(
    tier: PingTier,
    sent: Int,
    rttsMs: List<Double>,
): PingSummary {
    val loss = if (sent == 0) 0.0 else 100.0 * (sent - rttsMs.size) / sent
    if (rttsMs.isEmpty()) {
        return PingSummary(tier, sent, 0, null, null, null, null, null, null, loss)
    }

    val sorted = rttsMs.sorted()
    val avg = rttsMs.average()

    return PingSummary(
        tier = tier,
        sent = sent,
        received = rttsMs.size,
        minMs = sorted.first(),
        avgMs = avg,
        maxMs = sorted.last(),
        medianMs = median(sorted),
        stddevMs = stddev(rttsMs, avg),
        jitterMs = meanAbsoluteSuccessiveDifference(rttsMs),
        lossPercent = loss,
    )
}

private fun median(sorted: List<Double>): Double {
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2.0 else sorted[mid]
}

private fun stddev(
    values: List<Double>,
    mean: Double,
): Double? {
    if (values.size < 2) return null
    val variance = values.sumOf { (it - mean) * (it - mean) } / (values.size - 1)
    return sqrt(variance)
}

private fun meanAbsoluteSuccessiveDifference(values: List<Double>): Double? {
    if (values.size < 2) return null
    val diffs = (1 until values.size).map { abs(values[it] - values[it - 1]) }
    return diffs.average()
}
