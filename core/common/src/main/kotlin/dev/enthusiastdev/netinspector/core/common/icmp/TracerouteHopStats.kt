package dev.enthusiastdev.netinspector.core.common.icmp

import dev.enthusiastdev.netinspector.core.model.diagnostics.TracerouteProbe

/** design §9.3 - "Per-hop min/avg/max shown." `null` fields mean every probe in the hop
 * timed out or errored, rendered as `*` rather than a fabricated number (design §11.3). */
data class TracerouteHopStats(
    val minMs: Double?,
    val avgMs: Double?,
    val maxMs: Double?,
)

fun summarizeHop(probes: List<TracerouteProbe>): TracerouteHopStats {
    val rtts = probes.filterIsInstance<TracerouteProbe.Reply>().map { it.rttMs }
    if (rtts.isEmpty()) return TracerouteHopStats(null, null, null)
    return TracerouteHopStats(minMs = rtts.min(), avgMs = rtts.average(), maxMs = rtts.max())
}
