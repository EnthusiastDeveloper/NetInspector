package dev.enthusiastdev.netinspector.history

import dev.enthusiastdev.netinspector.core.model.diagnostics.HttpInspectionOutcome
import kotlinx.serialization.Serializable

@Serializable
data class HttpHopDto(
    val url: String,
    val statusCode: Int,
    val statusLine: String,
    val headers: List<Pair<String, String>>,
)

@Serializable
data class TlsCertificateDto(
    val subject: String,
    val issuer: String,
    val validFrom: String,
    val validUntil: String,
)

@Serializable
data class HttpInspectionRunPayload(
    val chain: List<HttpHopDto> = emptyList(),
    val tls: TlsCertificateDto? = null,
    val errorMessage: String? = null,
)

fun HttpInspectionOutcome.toRunPayload(): HttpInspectionRunPayload =
    when (this) {
        is HttpInspectionOutcome.Success ->
            HttpInspectionRunPayload(
                chain = chain.map { HttpHopDto(it.url, it.statusCode, it.statusLine, it.headers) },
                tls = tls?.let { TlsCertificateDto(it.subject, it.issuer, it.validFrom, it.validUntil) },
            )
        is HttpInspectionOutcome.Error -> HttpInspectionRunPayload(errorMessage = message)
    }

fun HttpInspectionOutcome.toHistorySummary(): String =
    when (this) {
        is HttpInspectionOutcome.Success -> "${chain.size} hop(s), final ${chain.lastOrNull()?.statusCode ?: "-"}"
        is HttpInspectionOutcome.Error -> "Error: $message"
    }
