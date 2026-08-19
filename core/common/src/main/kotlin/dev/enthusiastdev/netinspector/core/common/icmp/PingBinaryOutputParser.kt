package dev.enthusiastdev.netinspector.core.common.icmp

import dev.enthusiastdev.netinspector.core.model.diagnostics.PingProbeResult
import dev.enthusiastdev.netinspector.core.model.diagnostics.PingTier

/**
 * Tier 2 fallback parser (design §9.1/§12) for `/system/bin/ping -c 1` output. Verified
 * against real captures from both toybox (Android 16 / OneUI 8.5, toybox 0.8.12-android) and
 * iputils (20250605) - the two formats converge on the same `icmp_seq=N ... time=X ms`
 * substring, so this matches on that numeric pattern rather than any English wording, per
 * design's tolerance requirement.
 */
object PingBinaryOutputParser {
    private val REPLY_PATTERN = Regex("""icmp_seq=(\d+).*?time[=<]([0-9.]+)\s*ms""")

    fun parse(
        output: String,
        sequence: Int,
    ): PingProbeResult {
        val match = REPLY_PATTERN.find(output) ?: return PingProbeResult.Timeout(sequence, PingTier.PING_BINARY)
        val rttMs = match.groupValues[2].toDoubleOrNull()
        return if (rttMs != null) {
            PingProbeResult.Reply(sequence, PingTier.PING_BINARY, rttMs)
        } else {
            PingProbeResult.Error(sequence, PingTier.PING_BINARY, "unparseable RTT: ${match.value}")
        }
    }
}
