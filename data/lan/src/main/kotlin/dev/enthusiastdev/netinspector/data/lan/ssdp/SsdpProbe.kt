package dev.enthusiastdev.netinspector.data.lan.ssdp

import android.util.Xml
import dev.enthusiastdev.netinspector.core.model.lan.DiscoveredService
import dev.enthusiastdev.netinspector.core.model.lan.Evidence
import dev.enthusiastdev.netinspector.core.model.lan.EvidenceSource
import dev.enthusiastdev.netinspector.core.model.lan.HostObservation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.net.URL
import java.time.Clock
import javax.inject.Inject

/**
 * design §8.2 Stage A - SSDP M-SEARCH ×3 to the multicast address, then a plain
 * [DatagramSocket] collects the unicast HTTP-over-UDP replies (no need to join the multicast
 * group - M-SEARCH responses come back to the sender's ephemeral port directly). The
 * `LOCATION` URL, when present, is fetched for `friendlyName`/`manufacturer`/`modelName`.
 */
class SsdpProbe
    @Inject
    constructor(
        private val clock: Clock,
    ) {
        suspend fun discover(budgetMs: Long = DEFAULT_BUDGET_MS): List<HostObservation> =
            withContext(Dispatchers.IO) {
                val socket = DatagramSocket()
                try {
                    val request = buildMSearchRequest()
                    val target = InetSocketAddress(InetAddress.getByName(SSDP_MULTICAST_ADDRESS), SSDP_PORT)
                    repeat(SEARCH_REPEATS) {
                        socket.send(DatagramPacket(request, request.size, target))
                    }
                    collectResponses(socket, budgetMs)
                } finally {
                    runCatching { socket.close() }
                }
            }

        private fun collectResponses(
            socket: DatagramSocket,
            budgetMs: Long,
        ): List<HostObservation> {
            val results = mutableMapOf<Inet4Address, HostObservation>()
            val deadline = System.currentTimeMillis() + budgetMs
            val buffer = ByteArray(RECEIVE_BUFFER_SIZE)
            var timedOut = false
            while (!timedOut && System.currentTimeMillis() < deadline) {
                val remainingMs = deadline - System.currentTimeMillis()
                socket.soTimeout = remainingMs.toInt().coerceIn(1, Int.MAX_VALUE)
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                    val address = packet.address as? Inet4Address
                    if (address != null) {
                        results[address] = toObservation(address, String(packet.data, 0, packet.length, Charsets.UTF_8))
                    }
                } catch (ignored: SocketTimeoutException) {
                    timedOut = true
                }
            }
            return results.values.toList()
        }

        private fun toObservation(
            address: Inet4Address,
            rawResponse: String,
        ): HostObservation {
            val headers = parseSsdpHeaders(rawResponse)
            val server = headers["server"]
            val locationInfo = headers["location"]?.let { fetchLocationInfo(it) }
            val friendlyName = locationInfo?.friendlyName
            val detail =
                listOfNotNull(
                    locationInfo?.manufacturer,
                    locationInfo?.modelName,
                ).joinToString(" ").ifBlank { server }
            return HostObservation(
                address = address,
                evidence = listOf(Evidence(EvidenceSource.SSDP, clock.instant(), detail = server)),
                hostnames = friendlyName?.let { mapOf(EvidenceSource.SSDP to it) } ?: emptyMap(),
                services = listOf(DiscoveredService(EvidenceSource.SSDP, headers["st"], friendlyName, detail)),
            )
        }

        private fun buildMSearchRequest(): ByteArray =
            buildString {
                append("M-SEARCH * HTTP/1.1\r\n")
                append("HOST: $SSDP_MULTICAST_ADDRESS:$SSDP_PORT\r\n")
                append("MAN: \"ssdp:discover\"\r\n")
                append("MX: 2\r\n")
                append("ST: ssdp:all\r\n")
                append("\r\n")
            }.toByteArray(Charsets.UTF_8)

        private fun parseSsdpHeaders(raw: String): Map<String, String> =
            raw
                .lineSequence()
                .drop(1) // status line
                .mapNotNull { line ->
                    val separator = line.indexOf(':')
                    if (separator <= 0) return@mapNotNull null
                    line.substring(0, separator).trim().lowercase() to line.substring(separator + 1).trim()
                }.toMap()

        private fun fetchLocationInfo(locationUrl: String): UpnpDeviceInfo? =
            try {
                (URL(locationUrl).openConnection() as HttpURLConnection)
                    .apply {
                        connectTimeout = LOCATION_FETCH_TIMEOUT_MS
                        readTimeout = LOCATION_FETCH_TIMEOUT_MS
                        requestMethod = "GET"
                    }.inputStream
                    .use(::parseUpnpDeviceDescription)
            } catch (ignored: IOException) {
                null
            } catch (ignored: XmlPullParserException) {
                null
            }

        private fun parseUpnpDeviceDescription(stream: java.io.InputStream): UpnpDeviceInfo {
            val parser = Xml.newPullParser()
            parser.setInput(stream, null)
            var friendlyName: String? = null
            var manufacturer: String? = null
            var modelName: String? = null
            var currentTag: String? = null
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> currentTag = parser.name
                    XmlPullParser.TEXT ->
                        when (currentTag) {
                            "friendlyName" -> friendlyName = parser.text
                            "manufacturer" -> manufacturer = parser.text
                            "modelName" -> modelName = parser.text
                        }

                    XmlPullParser.END_TAG -> currentTag = null
                }
                event = parser.next()
            }
            return UpnpDeviceInfo(friendlyName, manufacturer, modelName)
        }

        private data class UpnpDeviceInfo(
            val friendlyName: String?,
            val manufacturer: String?,
            val modelName: String?,
        )

        private companion object {
            const val SSDP_MULTICAST_ADDRESS = "239.255.255.250"
            const val SSDP_PORT = 1900
            const val SEARCH_REPEATS = 3
            const val DEFAULT_BUDGET_MS = 3_000L
            const val RECEIVE_BUFFER_SIZE = 4096
            const val LOCATION_FETCH_TIMEOUT_MS = 1_500
        }
    }
