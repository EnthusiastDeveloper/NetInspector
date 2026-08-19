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
}
