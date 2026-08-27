package dev.enthusiastdev.netinspector.data.lan.upnp

import dev.enthusiastdev.netinspector.core.common.vendor.VendorLookup
import dev.enthusiastdev.netinspector.core.model.lan.Evidence
import dev.enthusiastdev.netinspector.core.model.lan.EvidenceSource
import dev.enthusiastdev.netinspector.core.model.lan.HostObservation
import java.io.IOException
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.URL
import java.time.Clock
import javax.inject.Inject

/**
 * docs/ideas.md C1 - once [dev.enthusiastdev.netinspector.data.lan.ssdp
 * .SsdpProbe] finds a router advertising `urn:schemas-upnp-org:service:Hosts:1` (or a later
 * revision) in its UPnP device description, this SOAP-queries that service for
 * `GetHostNumberOfEntries` then `GetGenericHostEntry` per index - the router's own DHCP/ARP
 * table, MAC *and* hostname for every LAN host it knows about, all from one router rather than
 * A3's narrower per-host NetBIOS exception. Coverage depends entirely on the router's firmware
 * exposing this optional service.
 */
class UpnpHostsProbe
    @Inject
    constructor(
        private val clock: Clock,
    ) {
        fun fetchHostEntries(
            controlUrl: String,
            serviceType: String,
            timeoutMs: Int,
        ): List<HostObservation> {
            val entryCount = getHostNumberOfEntries(controlUrl, serviceType, timeoutMs) ?: return emptyList()
            return (0 until entryCount.coerceIn(0, MAX_HOST_ENTRIES)).mapNotNull { index ->
                getGenericHostEntry(controlUrl, serviceType, index, timeoutMs)
            }
        }

        private fun getHostNumberOfEntries(
            controlUrl: String,
            serviceType: String,
            timeoutMs: Int,
        ): Int? {
            val response =
                soapCall(controlUrl, serviceType, "GetHostNumberOfEntries", emptyMap(), timeoutMs) ?: return null
            return soapField(response, "NewHostNumberOfEntries")?.toIntOrNull()
        }

        private fun getGenericHostEntry(
            controlUrl: String,
            serviceType: String,
            index: Int,
            timeoutMs: Int,
        ): HostObservation? {
            val arguments = mapOf("NewIndex" to index.toString())
            val response = soapCall(controlUrl, serviceType, "GetGenericHostEntry", arguments, timeoutMs)
            val ipAddress = response?.let { soapField(it, "NewIPAddress") }?.takeIf { it.isNotBlank() }
            val address = ipAddress?.let { runCatching { InetAddress.getByName(it) as? Inet4Address }.getOrNull() }
            val macAddress = response?.let { soapField(it, "NewMACAddress") }?.uppercase()?.takeIf { it.isNotBlank() }
            val hostName = response?.let { soapField(it, "NewHostName") }?.takeIf { it.isNotBlank() }
            if (address == null || (macAddress == null && hostName == null)) return null
            return HostObservation(
                address = address,
                evidence = listOf(Evidence(EvidenceSource.UPNP_HOSTS, clock.instant(), detail = hostName)),
                hostnames = hostName?.let { mapOf(EvidenceSource.UPNP_HOSTS to it) }.orEmpty(),
                macAddress = macAddress,
                vendor = macAddress?.let(VendorLookup::vendorFor),
            )
        }

        private fun soapCall(
            controlUrl: String,
            serviceType: String,
            action: String,
            arguments: Map<String, String>,
            timeoutMs: Int,
        ): String? =
            try {
                val body = buildSoapEnvelope(serviceType, action, arguments)
                val connection =
                    (URL(controlUrl).openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        connectTimeout = timeoutMs
                        readTimeout = timeoutMs
                        doOutput = true
                        setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
                        setRequestProperty("SOAPACTION", "\"$serviceType#$action\"")
                    }
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            } catch (ignored: IOException) {
                null
            }

        private fun buildSoapEnvelope(
            serviceType: String,
            action: String,
            arguments: Map<String, String>,
        ): String {
            val args = arguments.entries.joinToString(separator = "") { (key, value) -> "<$key>$value</$key>" }
            return "<?xml version=\"1.0\"?>" +
                "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
                "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
                "<s:Body><u:$action xmlns:u=\"$serviceType\">$args</u:$action></s:Body>" +
                "</s:Envelope>"
        }

        /** `internal` (rather than `private`) so [UpnpHostsProbeTest] can exercise the
         * SOAP-response parsing directly - same rationale as [dev.enthusiastdev.netinspector
         * .data.lan.netbios.NetBiosProbe.parseNbstatResponse]. */
        internal companion object {
            // A malformed or hostile response could report an enormous entry count; each entry
            // costs its own HTTP round trip, so this is capped well above any real home LAN's
            // device count rather than trusting the router not to stall the whole sweep.
            const val MAX_HOST_ENTRIES = 512

            /** A flat regex scan rather than a full pull-parser: every field this probe reads
             * back (`NewIPAddress`, `NewMACAddress`, `NewHostName`, `NewHostNumberOfEntries`) is
             * a single leaf element with no nested markup, and `android.util.Xml`'s pull parser
             * is an unmocked Android stub in a plain JVM unit test - this stays pure-JVM
             * testable the same way [dev.enthusiastdev.netinspector.data.lan.netbios
             * .NetBiosProbe]'s NBSTAT parsing is. */
            fun soapField(
                xml: String,
                tagName: String,
            ): String? =
                Regex("<(?:\\w+:)?$tagName>(.*?)</(?:\\w+:)?$tagName>", RegexOption.DOT_MATCHES_ALL)
                    .find(xml)
                    ?.groupValues
                    ?.get(1)
                    ?.let(::unescapeXml)
                    ?.trim()
                    ?.ifBlank { null }

            fun unescapeXml(value: String) =
                value
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("&quot;", "\"")
                    .replace("&apos;", "'")
                    .replace("&amp;", "&")
        }
    }
