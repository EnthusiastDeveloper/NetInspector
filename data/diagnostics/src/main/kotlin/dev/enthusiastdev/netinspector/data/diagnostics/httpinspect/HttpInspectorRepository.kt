package dev.enthusiastdev.netinspector.data.diagnostics.httpinspect

import dev.enthusiastdev.netinspector.core.model.diagnostics.HttpHop
import dev.enthusiastdev.netinspector.core.model.diagnostics.HttpInspectionOutcome
import dev.enthusiastdev.netinspector.core.model.diagnostics.TlsCertificateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.net.ssl.HttpsURLConnection

interface HttpInspectorRepository {
    /** design §9.6 - "redirects disabled," followed by hand-walking any `Location` header so
     * each hop's own status and headers stay visible instead of only the final destination's. */
    suspend fun inspect(
        url: String,
        maxRedirects: Int = 10,
        timeoutMs: Int = 5_000,
    ): HttpInspectionOutcome
}

class DefaultHttpInspectorRepository
    @Inject
    constructor() : HttpInspectorRepository {
        override suspend fun inspect(
            url: String,
            maxRedirects: Int,
            timeoutMs: Int,
        ): HttpInspectionOutcome =
            withContext(Dispatchers.IO) {
                val chain = mutableListOf<HttpHop>()
                var tls: TlsCertificateInfo? = null
                var currentUrl = normalize(url) ?: return@withContext HttpInspectionOutcome.Error("Invalid URL: $url")

                repeat(maxRedirects + 1) {
                    val connection =
                        try {
                            (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                                instanceFollowRedirects = false
                                connectTimeout = timeoutMs
                                readTimeout = timeoutMs
                                requestMethod = "GET"
                            }
                        } catch (e: IOException) {
                            return@withContext errorOrPartial(chain, tls, e.message ?: "connection failed")
                        }

                    try {
                        connection.connect()
                        val statusCode = connection.responseCode
                        val headers =
                            connection.headerFields
                                .flatMap { (key, values) -> values.map { (key ?: "") to it } }
                                .filter { it.first.isNotEmpty() }
                        chain += HttpHop(currentUrl, statusCode, "$statusCode ${connection.responseMessage}", headers)

                        if (tls == null && connection is HttpsURLConnection) {
                            tls = extractTlsInfo(connection)
                        }

                        val location = headers.firstOrNull { it.first.equals("Location", ignoreCase = true) }?.second
                        if (statusCode !in REDIRECT_CODES || location == null) {
                            return@withContext HttpInspectionOutcome.Success(chain, tls)
                        }
                        currentUrl =
                            resolveLocation(currentUrl, location)
                                ?: return@withContext HttpInspectionOutcome.Success(chain, tls)
                    } catch (e: IOException) {
                        return@withContext errorOrPartial(chain, tls, e.message ?: "request failed")
                    } finally {
                        connection.disconnect()
                    }
                }
                HttpInspectionOutcome.Success(chain, tls)
            }

        private fun errorOrPartial(
            chain: List<HttpHop>,
            tls: TlsCertificateInfo?,
            message: String,
        ): HttpInspectionOutcome =
            if (chain.isEmpty()) HttpInspectionOutcome.Error(message) else HttpInspectionOutcome.Success(chain, tls)

        private fun extractTlsInfo(connection: HttpsURLConnection): TlsCertificateInfo? {
            val cert = connection.serverCertificates.firstOrNull() as? X509Certificate ?: return null
            return TlsCertificateInfo(
                subject = cert.subjectX500Principal.name,
                issuer = cert.issuerX500Principal.name,
                validFrom = cert.notBefore.toString(),
                validUntil = cert.notAfter.toString(),
            )
        }

        private fun normalize(url: String): String? {
            val trimmed = url.trim()
            val hasScheme = trimmed.startsWith("http://") || trimmed.startsWith("https://")
            val withScheme = if (hasScheme) trimmed else "https://$trimmed"
            return runCatching { URI(withScheme).toURL().toString() }.getOrNull()
        }

        private fun resolveLocation(
            baseUrl: String,
            location: String,
        ): String? = runCatching { URI(baseUrl).resolve(location).toURL().toString() }.getOrNull()

        private companion object {
            val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
        }
    }
