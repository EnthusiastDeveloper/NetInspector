package dev.enthusiastdev.netinspector.history

import dev.enthusiastdev.netinspector.core.model.diagnostics.DnsQueryOutcome
import kotlinx.serialization.Serializable

@Serializable
data class DnsRecordDto(
    val name: String,
    val type: String? = null,
    val rawTypeCode: Int,
    val ttlSeconds: Long,
    val data: String,
)

@Serializable
data class DnsRunPayload(
    val queryTimeMs: Double? = null,
    val answers: List<DnsRecordDto> = emptyList(),
    val errorMessage: String? = null,
)

fun DnsQueryOutcome.toRunPayload(): DnsRunPayload =
    when (this) {
        is DnsQueryOutcome.Success ->
            DnsRunPayload(
                queryTimeMs = queryTimeMs,
                answers = answers.map { DnsRecordDto(it.name, it.type?.name, it.rawTypeCode, it.ttlSeconds, it.data) },
            )
        is DnsQueryOutcome.Error -> DnsRunPayload(errorMessage = message)
    }

fun DnsQueryOutcome.toHistorySummary(): String =
    when (this) {
        is DnsQueryOutcome.Success -> "${answers.size} record(s)"
        is DnsQueryOutcome.Error -> "Error: $message"
    }
