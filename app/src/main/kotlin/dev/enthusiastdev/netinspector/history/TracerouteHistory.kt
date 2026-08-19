package dev.enthusiastdev.netinspector.history

import dev.enthusiastdev.netinspector.core.model.diagnostics.TracerouteHop
import dev.enthusiastdev.netinspector.core.model.diagnostics.TracerouteProbe
import kotlinx.serialization.Serializable

@Serializable
data class TracerouteProbeDto(
    val kind: String,
    val tier: String,
    val fromAddress: String? = null,
    val rttMs: Double? = null,
    val reachedTarget: Boolean = false,
    val message: String? = null,
)

@Serializable
data class TracerouteHopDto(
    val ttl: Int,
    val hostname: String? = null,
    val probes: List<TracerouteProbeDto>,
)

@Serializable
data class TracerouteRunPayload(
    val hops: List<TracerouteHopDto>,
)

private fun TracerouteProbe.toDto(): TracerouteProbeDto =
    when (this) {
        is TracerouteProbe.Reply ->
            TracerouteProbeDto(
                kind = "REPLY",
                tier = tier.name,
                fromAddress = fromAddress,
                rttMs = rttMs,
                reachedTarget = reachedTarget,
            )
        is TracerouteProbe.Timeout -> TracerouteProbeDto("TIMEOUT", tier.name)
        is TracerouteProbe.Error -> TracerouteProbeDto("ERROR", tier.name, message = message)
    }

fun List<TracerouteHop>.toRunPayload(): TracerouteRunPayload =
    TracerouteRunPayload(map { hop -> TracerouteHopDto(hop.ttl, hop.hostname, hop.probes.map { it.toDto() }) })

fun List<TracerouteHop>.toHistorySummary(): String {
    val reached = any { it.reachedTarget }
    return "$size hops${if (reached) ", target reached" else ""}"
}
