package dev.enthusiastdev.netinspector.core.model.diagnostics

/** design §9.1 tiering - surfaced in the UI so a degraded result is never silently
 * presented as if it were the real thing (design §11.3). */
enum class PingTier { ICMP_SOCKET, PING_BINARY, TCP_CONNECT }

sealed interface PingProbeResult {
    val sequence: Int
    val tier: PingTier

    data class Reply(
        override val sequence: Int,
        override val tier: PingTier,
        val rttMs: Double,
    ) : PingProbeResult

    data class Timeout(
        override val sequence: Int,
        override val tier: PingTier,
    ) : PingProbeResult

    data class Error(
        override val sequence: Int,
        override val tier: PingTier,
        val message: String,
    ) : PingProbeResult
}
