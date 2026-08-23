package dev.enthusiastdev.netinspector.data.lan.ssdp

import android.util.Xml
import dev.enthusiastdev.netinspector.core.model.lan.DiscoveredService
import dev.enthusiastdev.netinspector.core.model.lan.Evidence
import dev.enthusiastdev.netinspector.core.model.lan.EvidenceSource
import dev.enthusiastdev.netinspector.core.model.lan.HostObservation
import dev.enthusiastdev.netinspector.core.model.lan.upnpDeviceHint
import dev.enthusiastdev.netinspector.data.lan.upnp.UpnpHostsProbe
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
import java.net.URI
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
        private val upnpHostsProbe: UpnpHostsProbe,
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
                    val collected = collectResponses(socket, budgetMs)
                    // docs/device-identification-ideas.md C1 - the router's Hosts-service SOAP
                    // round-trip runs after the receive loop closes, not inside it, so it never
                    // steals time from the socket's own receive budget and risks missing another
                    // responder's UDP reply while it's blocked doing HTTP.
                    collected.observations + fetchHostsServiceObservations(collected.hostsService)
                } finally {
                    runCatching { socket.close() }
                }
            }

        private data class CollectedResponses(
            val observations: List<HostObservation>,
            val hostsService: HostsServiceEndpoint?,
        )

        private data class HostsServiceEndpoint(
            val controlUrl: String,
            val serviceType: String,
        )

        private fun collectResponses(
            socket: DatagramSocket,
            budgetMs: Long,
        ): CollectedResponses {
            val results = mutableMapOf<Inet4Address, HostObservation>()
            var hostsService: HostsServiceEndpoint? = null
            val deadline = System.currentTimeMillis() + budgetMs
            val buffer = ByteArray(RECEIVE_BUFFER_SIZE)
            var timedOut = false
            while (!timedOut && System.currentTimeMillis() < deadline) {
                val remainingMs = deadline - System.currentTimeMillis()
                socket.soTimeout = remainingMs.toInt().coerceIn(1, Int.MAX_VALUE)
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                    val endpoint = recordResponse(packet, results)
                    // First responder that advertises the service wins - in practice this is
                    // the router, and there's normally only one on a home LAN anyway.
                    if (hostsService == null) hostsService = endpoint
                } catch (ignored: SocketTimeoutException) {
                    timedOut = true
                }
            }
            return CollectedResponses(results.values.toList(), hostsService)
        }

        private fun recordResponse(
            packet: DatagramPacket,
            results: MutableMap<Inet4Address, HostObservation>,
        ): HostsServiceEndpoint? {
            val address = packet.address as? Inet4Address ?: return null
            val (observation, endpoint) = toObservation(address, String(packet.data, 0, packet.length, Charsets.UTF_8))
            results[address] = observation
            return endpoint
        }

        private fun fetchHostsServiceObservations(endpoint: HostsServiceEndpoint?): List<HostObservation> =
            endpoint
                ?.let {
                    runCatching {
                        upnpHostsProbe.fetchHostEntries(it.controlUrl, it.serviceType, HOSTS_FETCH_TIMEOUT_MS)
                    }.getOrDefault(emptyList())
                }.orEmpty()

        private fun toObservation(
            address: Inet4Address,
            rawResponse: String,
        ): Pair<HostObservation, HostsServiceEndpoint?> {
            val headers = parseSsdpHeaders(rawResponse)
            val server = headers["server"]
            val locationInfo = headers["location"]?.let { fetchLocationInfo(it) }
            val friendlyName = locationInfo?.friendlyName
            val detail =
                listOfNotNull(
                    locationInfo?.manufacturer,
                    locationInfo?.modelName,
                ).joinToString(" ").ifBlank { server }
            val observation =
                HostObservation(
                    address = address,
                    evidence = listOf(Evidence(EvidenceSource.SSDP, clock.instant(), detail = server)),
                    hostnames = friendlyName?.let { mapOf(EvidenceSource.SSDP to it) } ?: emptyMap(),
                    services =
                        listOf(
                            DiscoveredService(
                                source = EvidenceSource.SSDP,
                                serviceType = headers["st"],
                                name = friendlyName,
                                detail = detail,
                                manufacturer = locationInfo?.manufacturer,
                                modelName = locationInfo?.modelName,
                            ),
                        ),
                    deviceHint = upnpDeviceHint(locationInfo?.manufacturer, locationInfo?.modelName),
                )
            val hostsEndpoint =
                if (locationInfo?.hostsControlUrl != null && locationInfo.hostsServiceType != null) {
                    HostsServiceEndpoint(locationInfo.hostsControlUrl, locationInfo.hostsServiceType)
                } else {
                    null
                }
            return observation to hostsEndpoint
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
                val info =
                    (URL(locationUrl).openConnection() as HttpURLConnection)
                        .apply {
                            connectTimeout = LOCATION_FETCH_TIMEOUT_MS
                            readTimeout = LOCATION_FETCH_TIMEOUT_MS
                            requestMethod = "GET"
                        }.inputStream
                        .use(::parseUpnpDeviceDescription)
                info.copy(hostsControlUrl = resolveControlUrl(locationUrl, info.hostsControlUrl))
            } catch (ignored: IOException) {
                null
            } catch (ignored: XmlPullParserException) {
                null
            }

        /** docs/device-identification-ideas.md C1 - `controlURL` in the device description is
         * commonly relative (e.g. `/upnp/control/hosts1`); it's resolved against the `LOCATION`
         * URL's own base, same as a browser resolving a relative link. */
        private fun resolveControlUrl(
            locationUrl: String,
            controlUrl: String?,
        ): String? = controlUrl?.let { runCatching { URI(locationUrl).resolve(it).toString() }.getOrNull() }

        private fun parseUpnpDeviceDescription(stream: java.io.InputStream): UpnpDeviceInfo {
            val parser = Xml.newPullParser()
            parser.setInput(stream, null)
            val state = DeviceDescriptionState()
            var currentTag: String? = null
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> currentTag = parser.name
                    XmlPullParser.TEXT -> state.applyText(currentTag, parser.text)
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "service") state.applyServiceEnd()
                        currentTag = null
                    }
                }
                event = parser.next()
            }
            return state.toDeviceInfo()
        }

        /** Mutable scratch state for [parseUpnpDeviceDescription]'s single pass over the
         * device description - kept as its own small class so the parse loop's own nesting
         * stays shallow. */
        private class DeviceDescriptionState {
            private var friendlyName: String? = null
            private var manufacturer: String? = null
            private var modelName: String? = null
            private var currentServiceType: String? = null
            private var currentControlUrl: String? = null
            private var hostsServiceType: String? = null
            private var hostsControlUrl: String? = null

            fun applyText(
                tag: String?,
                text: String,
            ) {
                when (tag) {
                    "friendlyName" -> friendlyName = text
                    "manufacturer" -> manufacturer = text
                    "modelName" -> modelName = text
                    "serviceType" -> currentServiceType = text
                    "controlURL" -> currentControlUrl = text
                }
            }

            /** docs/device-identification-ideas.md C1 - `<service>` blocks aren't nested, so
             * tracking just the current one (reset at its own closing tag) is enough without
             * building a full element stack. The first service matching [HOSTS_SERVICE_PREFIX]
             * wins - in practice there's only ever one Hosts service per device description. */
            fun applyServiceEnd() {
                val serviceType = currentServiceType
                if (hostsControlUrl == null && serviceType?.startsWith(HOSTS_SERVICE_PREFIX) == true) {
                    hostsServiceType = serviceType
                    hostsControlUrl = currentControlUrl
                }
                currentServiceType = null
                currentControlUrl = null
            }

            fun toDeviceInfo() =
                UpnpDeviceInfo(friendlyName, manufacturer, modelName, hostsServiceType, hostsControlUrl)
        }

        private data class UpnpDeviceInfo(
            val friendlyName: String?,
            val manufacturer: String?,
            val modelName: String?,
            val hostsServiceType: String?,
            val hostsControlUrl: String?,
        )

        private companion object {
            const val SSDP_MULTICAST_ADDRESS = "239.255.255.250"
            const val SSDP_PORT = 1900
            const val SEARCH_REPEATS = 3
            const val DEFAULT_BUDGET_MS = 3_000L
            const val RECEIVE_BUFFER_SIZE = 4096
            const val LOCATION_FETCH_TIMEOUT_MS = 1_500
            const val HOSTS_FETCH_TIMEOUT_MS = 2_000
            const val HOSTS_SERVICE_PREFIX = "urn:schemas-upnp-org:service:Hosts:"
        }
    }
