package dev.enthusiastdev.netinspector.history

import dev.enthusiastdev.netinspector.core.model.diagnostics.WhoisOutcome
import kotlinx.serialization.Serializable

@Serializable
data class WhoisHopDto(
    val server: String,
    val responseText: String,
)

@Serializable
data class WhoisRunPayload(
    val hops: List<WhoisHopDto> = emptyList(),
    val errorMessage: String? = null,
)

fun WhoisOutcome.toRunPayload(): WhoisRunPayload =
    when (this) {
        is WhoisOutcome.Success -> WhoisRunPayload(hops = hops.map { WhoisHopDto(it.server, it.responseText) })
        is WhoisOutcome.Error -> WhoisRunPayload(errorMessage = message)
    }

fun WhoisOutcome.toHistorySummary(): String =
    when (this) {
        is WhoisOutcome.Success -> "${hops.size} referral hop(s)"
        is WhoisOutcome.Error -> "Error: $message"
    }
