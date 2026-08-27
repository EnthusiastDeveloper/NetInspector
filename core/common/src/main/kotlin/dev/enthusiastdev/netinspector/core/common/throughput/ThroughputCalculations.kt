package dev.enthusiastdev.netinspector.core.common.throughput

import dev.enthusiastdev.netinspector.core.model.diagnostics.ThroughputResult
import dev.enthusiastdev.netinspector.core.model.wifi.ChannelSpan

/** Bytes-over-nanoseconds to megabits-per-second, shared by the live sampler and the final
 * summary below so the two numbers are computed the same way. */
fun mbpsFrom(
    bytes: Long,
    elapsedNanos: Long,
): Double {
    if (elapsedNanos <= 0) return 0.0
    val elapsedSeconds = elapsedNanos / 1_000_000_000.0
    return (bytes * 8) / 1_000_000.0 / elapsedSeconds
}

/**
 * docs/adr/0009-lan-throughput-icmp-burst-estimate.md - [roundTripBytes] is the combined
 * request+reply payload for every probe that got a reply, so [avgMbps] reflects both
 * directions at once rather than either one alone. [sent] of `0` (the run was cancelled before
 * a single probe went out) reports zero loss rather than dividing by zero - there's nothing to
 * call "lost" yet.
 */
fun summarizeThroughput(
    sent: Int,
    received: Int,
    roundTripBytes: Long,
    durationMs: Long,
    peakMbps: Double,
): ThroughputResult {
    val lossPercent = if (sent == 0) 0.0 else 100.0 * (sent - received) / sent
    val avgMbps = mbpsFrom(roundTripBytes, durationMs * 1_000_000L)
    return ThroughputResult(
        packetsSent = sent,
        packetsReceived = received,
        lossPercent = lossPercent,
        avgMbps = avgMbps,
        peakMbps = peakMbps.coerceAtLeast(avgMbps),
        durationMs = durationMs,
    )
}

/**
 * design §9.x / ideas.md #31 - the correlation differentiator: how many other
 * currently-visible access points share (part of) the connected network's channel, sourced from
 * `WifiScanRepository`'s scan state rather than the `NetworkCallback` RSSI stream alone (design
 * §5.1's RSSI stream has no notion of *other* APs). Two spans overlap when their frequency
 * ranges intersect - a wide 80/160 MHz span can share air with a neighbour's narrow 20 MHz one
 * even on a different nominal channel number, which a bare "same primary channel" check would
 * miss.
 */
fun overlappingChannelCount(
    connected: ChannelSpan,
    others: List<ChannelSpan>,
): Int = others.count { it.lowMhz < connected.highMhz && it.highMhz > connected.lowMhz }
