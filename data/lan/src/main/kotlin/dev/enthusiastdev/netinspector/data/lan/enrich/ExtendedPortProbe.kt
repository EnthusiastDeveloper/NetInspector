package dev.enthusiastdev.netinspector.data.lan.enrich

import dev.enthusiastdev.netinspector.core.model.lan.OpenPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject

/**
 * design §8.2 Stage C - the extended ~30-port service set, connect-scan only (design §9.5:
 * SYN scanning needs raw sockets, which are unavailable - design C-07). Unlike Stage B's
 * fallback sweep (design §8.2 pass 3), a refused connection here means "closed," not "the host
 * is up" - Stage C only runs against already-CONFIRMED hosts, so it exists to enumerate which
 * *services* are actually listening, not to prove liveness a second time.
 */
class ExtendedPortProbe
    @Inject
    constructor() {
        suspend fun probe(
            address: Inet4Address,
            port: Int,
            timeoutMs: Int,
        ): OpenPort? =
            withContext(Dispatchers.IO) {
                val serviceGuess = SERVICE_NAMES[port]
                // Banner grabs open their own socket, so they double as the open/closed probe
                // for the ports that support one - no need to connect twice.
                val banner =
                    when (port) {
                        in HTTP_PORTS -> grabHttpBanner(address, port, timeoutMs)
                        PORT_SSH -> grabLineBanner(address, port, timeoutMs)
                        else -> null
                    }
                when {
                    banner != null -> OpenPort(port, serviceGuess, banner)
                    connect(address, port, timeoutMs) -> OpenPort(port, serviceGuess, null)
                    else -> null
                }
            }

        private fun connect(
            address: Inet4Address,
            port: Int,
            timeoutMs: Int,
        ): Boolean =
            try {
                Socket().use { it.connect(InetSocketAddress(address, port), timeoutMs) }
                true
            } catch (ignored: IOException) {
                false
            }

        /** design §9.2/§8.2 - SSH (and similar line-oriented protocols) send their identification
         * banner unprompted immediately after the TCP handshake. */
        private fun grabLineBanner(
            address: Inet4Address,
            port: Int,
            timeoutMs: Int,
        ): String? =
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(address, port), timeoutMs)
                    socket.soTimeout = timeoutMs
                    socket
                        .getInputStream()
                        .bufferedReader(Charsets.US_ASCII)
                        .readLine()
                        ?.trim()
                        ?.take(MAX_BANNER_CHARS)
                }
            } catch (ignored: IOException) {
                null
            }

        /** design §8.2 - "HTTP `Server` header and `<title>`." A bare `HttpURLConnection` GET
         * would work too, but this needs `Connection: close` and a bounded read regardless, so a
         * raw socket keeps the timeout and byte cap explicit rather than relying on defaults. */
        private fun grabHttpBanner(
            address: Inet4Address,
            port: Int,
            timeoutMs: Int,
        ): String? =
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(address, port), timeoutMs)
                    socket.soTimeout = timeoutMs
                    val request = "GET / HTTP/1.0\r\nHost: ${address.hostAddress}\r\nConnection: close\r\n\r\n"
                    socket.getOutputStream().write(request.toByteArray(Charsets.US_ASCII))
                    val response =
                        socket
                            .getInputStream()
                            .bufferedReader(Charsets.US_ASCII)
                            .readText()
                            .take(MAX_HTTP_READ_CHARS)
                    formatHttpBanner(response)
                }
            } catch (ignored: IOException) {
                null
            }

        private fun formatHttpBanner(response: String): String? {
            val server =
                SERVER_HEADER_PATTERN
                    .find(response)
                    ?.groupValues
                    ?.get(1)
                    ?.trim()
            val title =
                TITLE_PATTERN
                    .find(response)
                    ?.groupValues
                    ?.get(1)
                    ?.trim()
            val parts = listOfNotNull(server?.let { "Server: $it" }, title?.let { "Title: $it" })
            return parts.joinToString("; ").takeIf { it.isNotEmpty() }?.take(MAX_BANNER_CHARS)
        }

        companion object {
            /** design §8.2 - the ~30-port extended set Stage C fans out over concurrently, per
             * confirmed host. */
            val PORTS: List<Int> get() = SERVICE_NAMES.keys.sorted()

            private const val PORT_SSH = 22
            private const val MAX_BANNER_CHARS = 200
            private const val MAX_HTTP_READ_CHARS = 8_192

            private val HTTP_PORTS = setOf(80, 81, 8000, 8008, 8080, 8443, 8888)

            private val SERVER_HEADER_PATTERN = Regex("""(?im)^Server:\s*(.+)$""")
            private val TITLE_PATTERN = Regex("""(?is)<title[^>]*>(.*?)</title>""")

            // design §8.2 - the ~30-port extended set: common LAN device services beyond
            // Stage B's fallback set (design §8.2 pass 3), for enumeration rather than liveness.
            private val SERVICE_NAMES: Map<Int, String> =
                mapOf(
                    21 to "ftp",
                    22 to "ssh",
                    23 to "telnet",
                    25 to "smtp",
                    53 to "dns",
                    80 to "http",
                    81 to "http-alt",
                    88 to "kerberos",
                    110 to "pop3",
                    135 to "msrpc",
                    139 to "netbios-ssn",
                    143 to "imap",
                    443 to "https",
                    445 to "microsoft-ds",
                    548 to "afp",
                    554 to "rtsp",
                    631 to "ipp",
                    636 to "ldaps",
                    993 to "imaps",
                    995 to "pop3s",
                    1723 to "pptp",
                    3306 to "mysql",
                    3389 to "rdp",
                    5000 to "upnp",
                    5357 to "wsdapi",
                    5555 to "adb",
                    5900 to "vnc",
                    8000 to "http-alt",
                    8008 to "http-alt",
                    8009 to "chromecast",
                    8080 to "http-proxy",
                    8443 to "https-alt",
                    8888 to "http-alt",
                    9100 to "printer",
                    32400 to "plex",
                    62078 to "ios-sync",
                )
        }
    }
