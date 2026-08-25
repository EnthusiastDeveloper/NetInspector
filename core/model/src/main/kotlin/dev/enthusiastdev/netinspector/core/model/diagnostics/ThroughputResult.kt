package dev.enthusiastdev.netinspector.core.model.diagnostics

/**
 * docs/improvement-ideas.md #31 (rescoped) - LAN-only throughput test. There is no cooperating
 * server on the target (the app has zero third-party or self-hosted service dependencies), so
 * this is a round-trip estimate from a burst of concurrent ICMP echo probes, not a one-directional
 * download/upload measurement the way an internet speed test reports one. [instantMbps] is the
 * combined send+receive byte rate over the sampling interval - see
 * docs/adr/0009-lan-throughput-icmp-burst-estimate.md for why this is the chosen mechanism and what
 * it can't tell you.
 */
data class ThroughputSample(
    val elapsedMs: Long,
    val instantMbps: Double,
    val packetsSent: Int,
    val packetsReceived: Int,
)

data class ThroughputResult(
    val packetsSent: Int,
    val packetsReceived: Int,
    val lossPercent: Double,
    val avgMbps: Double,
    val peakMbps: Double,
    val durationMs: Long,
)
