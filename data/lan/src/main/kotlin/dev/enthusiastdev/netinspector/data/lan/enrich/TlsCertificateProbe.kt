package dev.enthusiastdev.netinspector.data.lan.enrich

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

/**
 * docs/device-identification-ideas.md B3 - a self-signed admin-UI TLS certificate on 443/8443
 * frequently sets its CN to the product name outright (`Synology Inc.`, `ubnt`, `RT-AX88U`).
 * This is a handshake-only client: it never validates the chain (self-signed certs would
 * always fail that anyway) and never sends application data - it exists purely to read what
 * the server already hands over unprompted during the TLS handshake itself, same "connect,
 * don't validate, just read the chain" pattern a standalone TLS inspector (improvement-ideas.md
 * #14) would also need.
 */
class TlsCertificateProbe
    @Inject
    constructor() {
        suspend fun subjectCommonName(
            address: Inet4Address,
            timeoutMs: Int,
        ): String? =
            withContext(Dispatchers.IO) {
                TLS_PORTS.firstNotNullOfOrNull { port ->
                    runCatching { fetchCommonName(address, port, timeoutMs) }.getOrNull()
                }
            }

        private fun fetchCommonName(
            address: Inet4Address,
            port: Int,
            timeoutMs: Int,
        ): String? {
            val context = SSLContext.getInstance("TLS").apply { init(null, arrayOf(TRUST_ALL_CERTIFICATES), null) }
            Socket().use { plainSocket ->
                plainSocket.connect(InetSocketAddress(address, port), timeoutMs)
                val sslSocket =
                    context.socketFactory.createSocket(plainSocket, address.hostAddress, port, true) as SSLSocket
                sslSocket.use {
                    it.soTimeout = timeoutMs
                    it.startHandshake()
                    val cert = it.session.peerCertificates.firstOrNull() as? X509Certificate ?: return null
                    return commonNameOf(cert.subjectX500Principal.name)
                }
            }
        }

        private fun commonNameOf(distinguishedName: String): String? =
            distinguishedName.split(",").firstNotNullOfOrNull { component ->
                val trimmed = component.trim()
                if (!trimmed.startsWith(CN_PREFIX, ignoreCase = true)) return@firstNotNullOfOrNull null
                trimmed.removePrefix(CN_PREFIX).ifBlank { null }
            }

        private companion object {
            val TLS_PORTS = listOf(443, 8443)
            const val CN_PREFIX = "CN="

            // A self-signed device certificate will never chain to a trusted root, so the
            // usual validation is deliberately skipped here - this probe reads the certificate
            // the peer presents, it never treats the connection as secure or exchanges data
            // over it beyond the handshake itself.
            @Suppress("CustomX509TrustManager", "TrustAllX509TrustManager")
            val TRUST_ALL_CERTIFICATES =
                object : X509TrustManager {
                    override fun checkClientTrusted(
                        chain: Array<X509Certificate>,
                        authType: String,
                    ) = Unit

                    override fun checkServerTrusted(
                        chain: Array<X509Certificate>,
                        authType: String,
                    ) = Unit

                    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                }
        }
    }
