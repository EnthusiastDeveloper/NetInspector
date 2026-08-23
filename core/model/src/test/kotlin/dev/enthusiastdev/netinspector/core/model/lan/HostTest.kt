package dev.enthusiastdev.netinspector.core.model.lan

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.net.Inet4Address
import java.net.InetAddress

private fun addr(host: String) = InetAddress.getByName(host) as Inet4Address

@Suppress("LongParameterList") // test fixture builder - every field is a named, defaulted knob
private fun host(
    address: String,
    macAddress: String? = null,
    hostname: String? = null,
) = Host(
    address = addr(address),
    confidence = HostConfidence.CONFIRMED,
    evidence = emptyList(),
    hostnames = hostname?.let { mapOf(EvidenceSource.REVERSE_DNS to it) }.orEmpty(),
    macAddress = macAddress,
    vendor = null,
    deviceHint = null,
    openPorts = emptyList(),
    services = emptyList(),
    icmpReplyTtl = null,
    rttMedianMs = null,
    isGateway = false,
    isSelf = false,
)

class HostTest {
    @Test
    fun `nicknameKey uses the MAC address when one is present`() {
        val host = host("192.168.1.5", macAddress = "AA:BB:CC:DD:EE:FF", hostname = "printer")
        assertThat(host.nicknameKey()).isEqualTo("mac:AA:BB:CC:DD:EE:FF")
    }

    @Test
    fun `nicknameKey falls back to address plus hostname without a MAC`() {
        val host = host("192.168.1.5", hostname = "printer")
        assertThat(host.nicknameKey()).isEqualTo("addr:192.168.1.5|printer")
    }

    @Test
    fun `nicknameKey falls back to address alone when neither MAC nor hostname is known`() {
        val host = host("192.168.1.5")
        assertThat(host.nicknameKey()).isEqualTo("addr:192.168.1.5|")
    }

    @Test
    fun `nicknameKey is stable across two hosts with the same identity`() {
        val first = host("192.168.1.5", macAddress = "AA:BB:CC:DD:EE:FF")
        val second = host("192.168.1.5", macAddress = "AA:BB:CC:DD:EE:FF", hostname = "printer")
        assertThat(first.nicknameKey()).isEqualTo(second.nicknameKey())
    }
}
