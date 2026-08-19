package dev.enthusiastdev.netinspector.core.model.diagnostics

/**
 * design §9.3 - tiering mirrors ping's (design §9.1): the socket error-queue path is tier 1,
 * validated by spike S-02 (design C-08); the `ping -t` TTL walk is the fallback used
 * automatically if the error queue proves unreachable through the `Os` API on a given device.
 */
enum class TracerouteTier { ICMP_ERROR_QUEUE, PING_BINARY }

sealed interface TracerouteProbe {
    val tier: TracerouteTier

    data class Reply(
        override val tier: TracerouteTier,
        val fromAddress: String,
        val rttMs: Double,
        /** True if this reply came from the target itself (a normal echo reply, or an error
         * whose offender address matches the target) rather than an intermediate router. */
        val reachedTarget: Boolean,
    ) : TracerouteProbe

    data class Timeout(
        override val tier: TracerouteTier,
    ) : TracerouteProbe

    data class Error(
        override val tier: TracerouteTier,
        val message: String,
    ) : TracerouteProbe
}

/** design §9.3 - 3 probes per hop. `hostname` is filled in asynchronously by reverse DNS so it
 * never blocks the hop's own RTT results from rendering. */
data class TracerouteHop(
    val ttl: Int,
    val probes: List<TracerouteProbe>,
    val hostname: String? = null,
) {
    val respondingAddress: String?
        get() = probes.filterIsInstance<TracerouteProbe.Reply>().firstOrNull()?.fromAddress

    val reachedTarget: Boolean
        get() = probes.filterIsInstance<TracerouteProbe.Reply>().any { it.reachedTarget }

    /** design §9.3 - "5 consecutive fully-timed-out hops" stops the run; a hop only counts if
     * every probe within it timed out (an error reply, even a strange one, still counts as a
     * response for that purpose). */
    val isFullyTimedOut: Boolean
        get() = probes.isNotEmpty() && probes.all { it is TracerouteProbe.Timeout }
}
