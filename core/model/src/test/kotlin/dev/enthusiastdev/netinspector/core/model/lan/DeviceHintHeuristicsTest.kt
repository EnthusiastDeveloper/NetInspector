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
    fun `mdnsServiceHint returns null for an unrecognized service type`() {
        assertThat(mdnsServiceHint("_unknown._tcp", emptyMap())).isNull()
        assertThat(mdnsServiceHint(null, emptyMap())).isNull()
    }
}
