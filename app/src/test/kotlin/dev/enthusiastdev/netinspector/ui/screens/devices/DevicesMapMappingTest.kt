package dev.enthusiastdev.netinspector.ui.screens.devices

import com.google.common.truth.Truth.assertThat
import dev.enthusiastdev.netinspector.core.model.lan.Evidence
import dev.enthusiastdev.netinspector.core.model.lan.EvidenceSource
import dev.enthusiastdev.netinspector.core.model.lan.Host
import dev.enthusiastdev.netinspector.core.model.lan.HostConfidence
import dev.enthusiastdev.netinspector.core.model.lan.OpenPort
import org.junit.Test
import java.net.Inet4Address
import java.net.InetAddress
import java.time.Instant

private fun addr(host: String) = InetAddress.getByName(host) as Inet4Address

private fun host(
    address: String,
    isGateway: Boolean = false,
    isSelf: Boolean = false,
    openPorts: List<OpenPort> = emptyList(),
    hostname: String? = null,
) = Host(
    address = addr(address),
    confidence = HostConfidence.CONFIRMED,
    evidence = listOf(Evidence(EvidenceSource.ICMP, Instant.EPOCH)),
    hostnames = hostname?.let { mapOf(EvidenceSource.REVERSE_DNS to it) }.orEmpty(),
    macAddress = null,
    vendor = null,
    deviceHint = null,
    openPorts = openPorts,
    services = emptyList(),
    icmpReplyTtl = null,
    rttMedianMs = null,
    isGateway = isGateway,
    isSelf = isSelf,
)

class DevicesMapMappingTest {
    @Test
    fun `toNetworkMapData splits the gateway into the hub and everything else into spokes`() {
        val hosts =
            listOf(
                host("192.168.1.1", isGateway = true),
                host("192.168.1.10"),
                host("192.168.1.20"),
            )
        val data = hosts.toNetworkMapData()
        assertThat(data.hub?.id).isEqualTo("192.168.1.1")
        assertThat(data.spokes.map { it.id }).containsExactly("192.168.1.10", "192.168.1.20")
    }

    @Test
    fun `toNetworkMapData has no hub when no host is the gateway`() {
        val hosts = listOf(host("192.168.1.10"), host("192.168.1.20"))
        val data = hosts.toNetworkMapData()
        assertThat(data.hub).isNull()
        assertThat(data.spokes).hasSize(2)
    }

    @Test
    fun `toNetworkMapData flags a host with open ports as at risk`() {
        val hosts = listOf(host("192.168.1.10", openPorts = listOf(OpenPort(23, "telnet", null))))
        val data = hosts.toNetworkMapData()
        assertThat(data.spokes.single().isAtRisk).isTrue()
    }

    @Test
    fun `toNetworkMapData does not flag a host with no open ports as at risk`() {
        val hosts = listOf(host("192.168.1.10"))
        val data = hosts.toNetworkMapData()
        assertThat(data.spokes.single().isAtRisk).isFalse()
    }

    @Test
    fun `toNetworkMapData labels a regular host with its resolved hostname`() {
        val hosts = listOf(host("192.168.1.42", hostname = "printer.lan"))
        val data = hosts.toNetworkMapData()
        assertThat(data.spokes.single().label).isEqualTo("printer.lan")
    }

    @Test
    fun `toNetworkMapData falls back to Unknown device when nothing resolved a name`() {
        val hosts = listOf(host("192.168.1.42"))
        val data = hosts.toNetworkMapData()
        assertThat(data.spokes.single().label).isEqualTo("Unknown device")
    }

    @Test
    fun `toNetworkMapData labels the self host This device`() {
        val hosts = listOf(host("192.168.1.2", isSelf = true))
        val data = hosts.toNetworkMapData()
        assertThat(data.spokes.single().label).isEqualTo("This device")
    }

    @Test
    fun `toNetworkMapData labels the gateway with its full display name`() {
        val hosts = listOf(host("192.168.1.1", isGateway = true))
        val data = hosts.toNetworkMapData()
        assertThat(data.hub?.label).isEqualTo("Gateway")
    }
}
