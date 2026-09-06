package dev.enthusiastdev.netinspector.core.model.lan

import com.google.common.truth.Truth.assertThat
import org.junit.Test

private fun port(
    port: Int,
    serviceGuess: String? = null,
    banner: String? = null,
) = OpenPort(port, serviceGuess, banner)

class DeviceHintHeuristicsTest {
    @Test
    fun `ttlDeviceHint classifies an observed TTL up to 64 as the unix family`() {
        val hint = ttlDeviceHint(58)
        assertThat(hint?.label).isEqualTo("Linux/Android/iOS/macOS family")
        assertThat(hint?.certainty).isEqualTo(Certainty.POSSIBLE)
        assertThat(hint?.basis).contains("58")
    }

    @Test
    fun `ttlDeviceHint classifies an observed TTL between 65 and 128 as windows`() {
        val hint = ttlDeviceHint(118)
        assertThat(hint?.label).isEqualTo("Windows family")
        assertThat(hint?.basis).isEqualTo("IP TTL 118 (~128) → Windows family")
    }

    @Test
    fun `ttlDeviceHint classifies an observed TTL between 129 and 255 as network equipment`() {
        val hint = ttlDeviceHint(250)
        assertThat(hint?.label).isEqualTo("Network equipment")
    }

    @Test
    fun `ttlDeviceHint returns null for a TTL beyond every known initial value`() {
        assertThat(ttlDeviceHint(300)).isNull()
    }

    @Test
    fun `deviceHintFor prefers a port signature over a TTL fingerprint`() {
        val hint = deviceHintFor(openPorts = listOf(port(5555)), icmpReplyTtl = 64)
        assertThat(hint?.certainty).isEqualTo(Certainty.LIKELY)
        assertThat(hint?.label).isEqualTo("Android debug bridge (ADB)")
    }

    @Test
    fun `deviceHintFor requires every port in a multi-port signature to be open`() {
        assertThat(deviceHintFor(openPorts = listOf(port(445)), icmpReplyTtl = null)).isNull()
        val hint = deviceHintFor(openPorts = listOf(port(445), port(139)), icmpReplyTtl = null)
        assertThat(hint?.label).isEqualTo("Windows/Samba file sharing")
    }

    @Test
    fun `deviceHintFor falls back to the TTL fingerprint when no port signature matches`() {
        val hint = deviceHintFor(openPorts = listOf(port(80)), icmpReplyTtl = 128)
        assertThat(hint?.certainty).isEqualTo(Certainty.POSSIBLE)
        assertThat(hint?.label).isEqualTo("Windows family")
    }

    @Test
    fun `deviceHintFor returns null when nothing matched`() {
        assertThat(deviceHintFor(openPorts = emptyList(), icmpReplyTtl = null)).isNull()
    }

    @Test
    fun `deviceHintFor prefers a domain controller signature over the plain Windows-Samba signature`() {
        val hint =
            deviceHintFor(
                openPorts = listOf(port(88), port(636), port(445), port(139)),
                icmpReplyTtl = null,
            )
        assertThat(hint?.label).isEqualTo("Windows domain controller (Active Directory)")
    }

    @Test
    fun `deviceHintFor falls back to plain Windows-Samba when the domain controller ports are absent`() {
        val hint = deviceHintFor(openPorts = listOf(port(445), port(139)), icmpReplyTtl = null)
        assertThat(hint?.label).isEqualTo("Windows/Samba file sharing")
    }

    @Test
    fun `deviceHintFor recognizes an RTSP camera signature`() {
        val hint = deviceHintFor(openPorts = listOf(port(554)), icmpReplyTtl = null)
        assertThat(hint?.label).isEqualTo("IP camera / streaming device")
    }

    @Test
    fun `deviceHintFor recognizes a telnet signature`() {
        val hint = deviceHintFor(openPorts = listOf(port(23)), icmpReplyTtl = null)
        assertThat(hint?.label).isEqualTo("Telnet-enabled device (legacy/insecure)")
    }

    @Test
    fun `upnpDeviceHint combines manufacturer and model at CONFIRMED certainty`() {
        val hint = upnpDeviceHint("Synology Inc.", "DS220+")
        assertThat(hint?.label).isEqualTo("Synology Inc. DS220+")
        assertThat(hint?.certainty).isEqualTo(Certainty.CONFIRMED)
    }

    @Test
    fun `upnpDeviceHint falls back to whichever field is present`() {
        assertThat(upnpDeviceHint("Sonos", null)?.label).isEqualTo("Sonos")
        assertThat(upnpDeviceHint(null, "One SL")?.label).isEqualTo("One SL")
    }

    @Test
    fun `upnpDeviceHint returns null when both fields are absent`() {
        assertThat(upnpDeviceHint(null, null)).isNull()
    }

    @Test
    fun `mdnsServiceHint prefers an explicit TXT model over the generic service-type label`() {
        val hint = mdnsServiceHint("_googlecast._tcp", mapOf("md" to "Chromecast"))
        assertThat(hint?.label).isEqualTo("Chromecast")
        assertThat(hint?.certainty).isEqualTo(Certainty.CONFIRMED)
    }

    @Test
    fun `mdnsServiceHint reads the Apple device-info model TXT key`() {
        val hint = mdnsServiceHint("_device-info._tcp", mapOf("model" to "J274AP"))
        assertThat(hint?.label).isEqualTo("Apple device (J274AP)")
        assertThat(hint?.certainty).isEqualTo(Certainty.CONFIRMED)
    }

    @Test
    fun `mdnsServiceHint falls back to a generic LIKELY label when no TXT model is present`() {
        val hint = mdnsServiceHint("_googlecast._tcp", emptyMap())
        assertThat(hint?.label).isEqualTo("Chromecast / Google Cast device")
        assertThat(hint?.certainty).isEqualTo(Certainty.LIKELY)
    }

    @Test
    fun `mdnsServiceHint tolerates a trailing dot on the service type`() {
        val hint = mdnsServiceHint("_hap._tcp.", emptyMap())
        assertThat(hint?.label).isEqualTo("HomeKit accessory")
    }

    @Test
    fun `mdnsServiceHint tolerates the stray leading dot NsdServiceInfo sometimes returns`() {
        val hint = mdnsServiceHint("._hap._tcp", emptyMap())
        assertThat(hint?.label).isEqualTo("HomeKit accessory")
    }

    @Test
    fun `mdnsServiceHint returns null for an unrecognized service type`() {
        assertThat(mdnsServiceHint("_unknown._tcp", emptyMap())).isNull()
        assertThat(mdnsServiceHint(null, emptyMap())).isNull()
    }

    @Test
    fun `snmpDeviceHint reports the sysDescr string at CONFIRMED certainty`() {
        val hint = snmpDeviceHint("Cisco IOS Software, C2960 Software")
        assertThat(hint?.label).isEqualTo("Cisco IOS Software, C2960 Software")
        assertThat(hint?.certainty).isEqualTo(Certainty.CONFIRMED)
    }

    @Test
    fun `snmpDeviceHint returns null for a blank or absent sysDescr`() {
        assertThat(snmpDeviceHint(null)).isNull()
        assertThat(snmpDeviceHint("  ")).isNull()
    }

    @Test
    fun `tlsCertificateDeviceHint reports the certificate CN at CONFIRMED certainty`() {
        val hint = tlsCertificateDeviceHint("Synology Inc.")
        assertThat(hint?.label).isEqualTo("Synology Inc.")
        assertThat(hint?.certainty).isEqualTo(Certainty.CONFIRMED)
    }

    @Test
    fun `tlsCertificateDeviceHint returns null for a blank or absent CN`() {
        assertThat(tlsCertificateDeviceHint(null)).isNull()
        assertThat(tlsCertificateDeviceHint(" ")).isNull()
    }

    @Test
    fun `deviceHintFor prefers a self-reported SNMP sysDescr over a port signature`() {
        val hint = deviceHintFor(openPorts = listOf(port(9100)), icmpReplyTtl = null, snmpSysDescr = "HP LaserJet 4050")
        assertThat(hint?.label).isEqualTo("HP LaserJet 4050")
        assertThat(hint?.certainty).isEqualTo(Certainty.CONFIRMED)
    }

    @Test
    fun `deviceHintFor prefers a self-reported TLS certificate CN over a port signature`() {
        val hint =
            deviceHintFor(openPorts = listOf(port(443)), icmpReplyTtl = null, tlsCertificateCommonName = "RT-AX88U")
        assertThat(hint?.label).isEqualTo("RT-AX88U")
        assertThat(hint?.certainty).isEqualTo(Certainty.CONFIRMED)
    }

    @Test
    fun `deviceHintFor prefers SNMP over a TLS certificate when both are present`() {
        val hint =
            deviceHintFor(
                openPorts = emptyList(),
                icmpReplyTtl = null,
                snmpSysDescr = "Synology DSM 7",
                tlsCertificateCommonName = "Synology Inc.",
            )
        assertThat(hint?.label).isEqualTo("Synology DSM 7")
    }

    @Test
    fun `httpServerHint reads Apache's parenthetical OS suffix at CONFIRMED certainty`() {
        val hint = httpServerHint(listOf(port(80, "http", "Server: Apache/2.4.52 (Ubuntu); Title: It works!")))
        assertThat(hint?.label).isEqualTo("Ubuntu")
        assertThat(hint?.certainty).isEqualTo(Certainty.CONFIRMED)
    }

    @Test
    fun `httpServerHint maps an IIS version to its Windows release at LIKELY certainty`() {
        val hint = httpServerHint(listOf(port(80, "http", "Server: Microsoft-IIS/10.0")))
        assertThat(hint?.label).isEqualTo("Windows 10 / Server 2016+")
        assertThat(hint?.certainty).isEqualTo(Certainty.LIKELY)
    }

    @Test
    fun `httpServerHint ignores an unrecognized Apache OS suffix and an unmapped IIS version`() {
        assertThat(httpServerHint(listOf(port(80, "http", "Server: Apache/2.4.52 (Custom Build)")))).isNull()
        assertThat(httpServerHint(listOf(port(80, "http", "Server: Microsoft-IIS/99.9")))).isNull()
    }

    @Test
    fun `httpServerHint returns null when no port has a banner`() {
        assertThat(httpServerHint(listOf(port(80, "http", null)))).isNull()
        assertThat(httpServerHint(emptyList())).isNull()
    }

    @Test
    fun `ssdpServerHint reads the OS token from the Microsoft-Windows convention`() {
        val hint = ssdpServerHint("Microsoft-Windows/10.0 UPnP/1.0 UPnP-Device-Host/1.0")
        assertThat(hint?.label).isEqualTo("Windows 10.0")
        assertThat(hint?.certainty).isEqualTo(Certainty.CONFIRMED)
    }

    @Test
    fun `ssdpServerHint reads the OS token from the plain Linux convention`() {
        val hint = ssdpServerHint("Linux/3.10.0 UPnP/1.0 MiniDLNA/1.2.1")
        assertThat(hint?.label).isEqualTo("Linux 3.10.0")
    }

    @Test
    fun `ssdpServerHint returns null for an unrecognized or absent SERVER header`() {
        assertThat(ssdpServerHint("MiniUPnPd/2.1 UPnP/1.1")).isNull()
        assertThat(ssdpServerHint(null)).isNull()
    }

    @Test
    fun `smbDialectHint maps a negotiated dialect to a Windows-version range at LIKELY certainty`() {
        val hint = smbDialectHint(0x0302)
        assertThat(hint?.label).isEqualTo("Windows 8.1+ / Server 2012 R2+ era")
        assertThat(hint?.certainty).isEqualTo(Certainty.LIKELY)
        assertThat(hint?.basis).contains("Samba")
    }

    @Test
    fun `smbDialectHint returns null for an unmapped dialect revision`() {
        assertThat(smbDialectHint(0x0311)).isNull()
    }

    @Test
    fun `deviceHintFor prefers a specific SMB dialect hint over the generic port signature`() {
        val hint =
            deviceHintFor(
                openPorts = listOf(port(445), port(139)),
                icmpReplyTtl = null,
                smbDialectRevision = 0x0210,
            )
        assertThat(hint?.label).isEqualTo("Windows 7 / Server 2008 R2 era")
    }

    @Test
    fun `WELL_KNOWN_MDNS_SERVICE_TYPES covers every type this file can turn into a hint`() {
        assertThat(WELL_KNOWN_MDNS_SERVICE_TYPES)
            .containsAtLeastElementsIn(
                listOf(
                    "_airplay._tcp",
                    "_raop._tcp",
                    "_googlecast._tcp",
                    "_esphome._tcp",
                    "_ipp._tcp",
                    "_device-info._tcp",
                ),
            )
    }
}
