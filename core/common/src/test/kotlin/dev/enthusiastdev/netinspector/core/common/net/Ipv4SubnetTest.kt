package dev.enthusiastdev.netinspector.core.common.net

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.net.Inet4Address
import java.net.InetAddress

private fun ip(text: String) = InetAddress.getByName(text) as Inet4Address

class Ipv4SubnetTest {
    @Test
    fun `slash24 computes network, broadcast and usable range`() {
        val subnet = Ipv4Subnet(ip("192.168.1.10"), 24)

        assertThat(subnet.networkAddress).isEqualTo(ip("192.168.1.0"))
        assertThat(subnet.broadcastAddress).isEqualTo(ip("192.168.1.255"))
        assertThat(subnet.usableHostRange).isEqualTo(Ipv4Range(ip("192.168.1.1"), ip("192.168.1.254")))
        assertThat(subnet.hostCount).isEqualTo(254L)
    }

    @Test
    fun `slash30 computes a small usable range`() {
        val subnet = Ipv4Subnet(ip("10.0.0.5"), 30)

        assertThat(subnet.networkAddress).isEqualTo(ip("10.0.0.4"))
        assertThat(subnet.broadcastAddress).isEqualTo(ip("10.0.0.7"))
        assertThat(subnet.usableHostRange).isEqualTo(Ipv4Range(ip("10.0.0.5"), ip("10.0.0.6")))
        assertThat(subnet.hostCount).isEqualTo(2L)
    }

    @Test
    fun `slash31 has no broadcast and both addresses are usable (RFC 3021)`() {
        val subnet = Ipv4Subnet(ip("10.0.0.4"), 31)

        assertThat(subnet.broadcastAddress).isNull()
        assertThat(subnet.usableHostRange).isEqualTo(Ipv4Range(ip("10.0.0.4"), ip("10.0.0.5")))
        assertThat(subnet.hostCount).isEqualTo(2L)
        assertThat(subnet.hostSequence().toList()).containsExactly(ip("10.0.0.4"), ip("10.0.0.5")).inOrder()
    }

    @Test
    fun `slash32 is a single host with no broadcast`() {
        val subnet = Ipv4Subnet(ip("10.0.0.4"), 32)

        assertThat(subnet.broadcastAddress).isNull()
        assertThat(subnet.usableHostRange).isEqualTo(Ipv4Range(ip("10.0.0.4"), ip("10.0.0.4")))
        assertThat(subnet.hostCount).isEqualTo(1L)
        assertThat(subnet.hostSequence().toList()).containsExactly(ip("10.0.0.4"))
    }

    @Test
    fun `hostSequence enumerates every usable address in order for a small subnet`() {
        val subnet = Ipv4Subnet(ip("192.168.5.1"), 29) // 6 usable hosts

        assertThat(subnet.hostSequence().toList())
            .containsExactly(
                ip("192.168.5.1"),
                ip("192.168.5.2"),
                ip("192.168.5.3"),
                ip("192.168.5.4"),
                ip("192.168.5.5"),
                ip("192.168.5.6"),
            ).inOrder()
    }

    @Test
    fun `rejects invalid prefix lengths`() {
        try {
            Ipv4Subnet(ip("10.0.0.1"), 33)
            throw AssertionError("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `toUInt32 and toInet4Address round-trip`() {
        val original = ip("203.0.113.42")
        assertThat(original.toUInt32().toInet4Address()).isEqualTo(original)
    }
}
