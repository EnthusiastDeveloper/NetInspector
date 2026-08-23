package dev.enthusiastdev.netinspector.data.lan.upnp

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * [UpnpHostsProbe]'s SOAP round-trips themselves need a live router to exercise end to end, so
 * this covers the pure response-parsing logic (`Companion.soapField`) against synthetic SOAP
 * bodies shaped like real `GetHostNumberOfEntries`/`GetGenericHostEntry` responses - same
 * approach as `NetBiosProbeTest` building a synthetic NBSTAT packet by hand.
 */
class UpnpHostsProbeTest {
    @Test
    fun `soapField extracts an unprefixed leaf element`() {
        val response =
            "<s:Envelope><s:Body><u:GetGenericHostEntryResponse xmlns:u=\"urn:schemas-upnp-org:service:Hosts:1\">" +
                "<NewIPAddress>192.168.1.42</NewIPAddress>" +
                "<NewMACAddress>AA:BB:CC:DD:EE:FF</NewMACAddress>" +
                "<NewHostName>printer</NewHostName>" +
                "</u:GetGenericHostEntryResponse></s:Body></s:Envelope>"

        assertThat(UpnpHostsProbe.soapField(response, "NewIPAddress")).isEqualTo("192.168.1.42")
        assertThat(UpnpHostsProbe.soapField(response, "NewMACAddress")).isEqualTo("AA:BB:CC:DD:EE:FF")
        assertThat(UpnpHostsProbe.soapField(response, "NewHostName")).isEqualTo("printer")
    }

    @Test
    fun `soapField tolerates a namespace-prefixed leaf element`() {
        val response = "<Body><u:NewHostNumberOfEntries>7</u:NewHostNumberOfEntries></Body>"
        assertThat(UpnpHostsProbe.soapField(response, "NewHostNumberOfEntries")).isEqualTo("7")
    }

    @Test
    fun `soapField unescapes XML entities`() {
        val response = "<NewHostName>Bob&apos;s &amp; Alice&apos;s NAS</NewHostName>"
        assertThat(UpnpHostsProbe.soapField(response, "NewHostName")).isEqualTo("Bob's & Alice's NAS")
    }

    @Test
    fun `soapField returns null for a blank or missing element`() {
        assertThat(UpnpHostsProbe.soapField("<NewHostName></NewHostName>", "NewHostName")).isNull()
        assertThat(UpnpHostsProbe.soapField("<Other>value</Other>", "NewHostName")).isNull()
    }
}
