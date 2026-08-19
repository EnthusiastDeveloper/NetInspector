package dev.enthusiastdev.netinspector.core.model.diagnostics

/** design §9.6 - one hop of a redirect chain, since redirects are followed manually rather
 * than by the HTTP client (`HttpURLConnection` with redirects disabled) so each hop's own
 * status and headers are visible, not just the final destination's. */
data class HttpHop(
    val url: String,
    val statusCode: Int,
    val statusLine: String,
    val headers: List<Pair<String, String>>,
)

data class TlsCertificateInfo(
    val subject: String,
    val issuer: String,
    val validFrom: String,
    val validUntil: String,
)

sealed interface HttpInspectionOutcome {
    data class Success(
        val chain: List<HttpHop>,
        val tls: TlsCertificateInfo?,
    ) : HttpInspectionOutcome

    data class Error(
        val message: String,
    ) : HttpInspectionOutcome
}
