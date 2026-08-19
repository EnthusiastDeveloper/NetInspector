package dev.enthusiastdev.netinspector.core.common.icmp

import dev.enthusiastdev.netinspector.core.model.diagnostics.TracerouteProbe
import dev.enthusiastdev.netinspector.core.model.diagnostics.TracerouteTier

/**
 * Fallback parser (design §9.3/C-08) for `/system/bin/ping -c 1 -W <timeout> -t <ttl> <target>`,
 * used only if spike S-02 finds the socket error queue unreachable through the `Os` API. Verified
 * against real captures from toybox (Android 15, One UI 7.0) for both the TTL-exceeded and
 * ordinary-reply cases; the iputils fixture is a reference capture from a second device, not yet
 * confirmed on this app's matrix (mirrors the ping tool's own tier-2 parser, C-07).
 *
 * Unlike a normal reply, "Time to live exceeded" carries no `time=` field in either ping build,
 * so its RTT has to come from wall-clock timing around the whole process instead of the binary's
 * own report - [measuredRttMs] is that caller-supplied fallback, used only when the matched line
 * has no timing of its own.
 */
object TracerouteBinaryOutputParser {
    private val REPLY_PATTERN =
        Regex("""bytes from ([0-9.]+):.*?icmp_seq=(\d+).*?time[=<]([0-9.]+)\s*ms""")
    private val TTL_EXCEEDED_PATTERN =
        Regex("""From ([0-9.]+):?\s*icmp_seq=(\d+)\s*Time to live exceeded""")
    private val UNREACHABLE_PATTERN =
        Regex("""From ([0-9.]+):?\s*icmp_seq=(\d+)\s*Destination (?:Host|Net|Port) Unreachable""")

    fun parse(
        output: String,
        measuredRttMs: Double,
    ): TracerouteProbe {
        REPLY_PATTERN.find(output)?.let { match ->
            val rttMs = match.groupValues[3].toDoubleOrNull()
            return if (rttMs != null) {
                TracerouteProbe.Reply(TracerouteTier.PING_BINARY, match.groupValues[1], rttMs, reachedTarget = true)
            } else {
                TracerouteProbe.Error(TracerouteTier.PING_BINARY, "unparseable RTT: ${match.value}")
            }
        }
        TTL_EXCEEDED_PATTERN.find(output)?.let { match ->
            return TracerouteProbe.Reply(
                TracerouteTier.PING_BINARY,
                match.groupValues[1],
                measuredRttMs,
                reachedTarget = false,
            )
        }
        UNREACHABLE_PATTERN.find(output)?.let { match ->
            return TracerouteProbe.Reply(
                TracerouteTier.PING_BINARY,
                match.groupValues[1],
                measuredRttMs,
                reachedTarget = false,
            )
        }
        return TracerouteProbe.Timeout(TracerouteTier.PING_BINARY)
    }
}
