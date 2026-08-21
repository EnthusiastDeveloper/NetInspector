package dev.enthusiastdev.netinspector.ui.screens.devices

import com.google.common.truth.Truth.assertThat
import dev.enthusiastdev.netinspector.core.model.lan.Certainty
import dev.enthusiastdev.netinspector.core.model.lan.DeviceHint
import dev.enthusiastdev.netinspector.core.model.lan.Evidence
import dev.enthusiastdev.netinspector.core.model.lan.EvidenceSource
import dev.enthusiastdev.netinspector.core.model.lan.Host
import dev.enthusiastdev.netinspector.core.model.lan.HostConfidence
import org.junit.Test
import java.net.Inet4Address
import java.net.InetAddress
import java.time.Instant

private fun addr(host: String) = InetAddress.getByName(host) as Inet4Address

@Suppress("LongParameterList") // test fixture builder - every field is a named, defaulted knob
private fun host(
    address: String,
    confidence: HostConfidence = HostConfidence.CONFIRMED,
    hostname: String? = null,
    deviceHint: DeviceHint? = null,
    rttMedianMs: Double? = null,
    lastSeen: Instant = Instant.EPOCH,
    isGateway: Boolean = false,
    isSelf: Boolean = false,
) = Host(
    address = addr(address),
    confidence = confidence,
    evidence = listOf(Evidence(EvidenceSource.ICMP, lastSeen)),
    hostnames = hostname?.let { mapOf(EvidenceSource.REVERSE_DNS to it) }.orEmpty(),
    macAddress = null,
    vendor = null,
    deviceHint = deviceHint,
    openPorts = emptyList(),
    services = emptyList(),
    icmpReplyTtl = null,
    rttMedianMs = rttMedianMs,
    isGateway = isGateway,
    isSelf = isSelf,
)

private fun hint(label: String) = DeviceHint(label, basis = "test", certainty = Certainty.LIKELY)

class DevicesFormattingTest {
    @Test
    fun `sortedForDisplay GROUP pins self and gateway first, then confidence tier, then address`() {
        val hosts =
            listOf(
                host("192.168.1.50", confidence = HostConfidence.ANNOUNCED),
                host("192.168.1.1", isGateway = true),
                host("192.168.1.10"),
                host("192.168.1.2", isSelf = true),
            )
        val sorted = hosts.sortedForDisplay(DevicesSortOrder.GROUP)
        assertThat(sorted.map { it.address.hostAddress })
            .containsExactly("192.168.1.1", "192.168.1.2", "192.168.1.10", "192.168.1.50")
            .inOrder()
    }

    @Test
    fun `sortedForDisplay IP_ADDRESS sorts numerically including self and gateway`() {
        val hosts =
            listOf(
                host("192.168.1.100"),
                host("192.168.1.1", isGateway = true),
                host("192.168.1.9"),
            )
        val sorted = hosts.sortedForDisplay(DevicesSortOrder.IP_ADDRESS)
        assertThat(sorted.map { it.address.hostAddress })
            .containsExactly("192.168.1.1", "192.168.1.9", "192.168.1.100")
            .inOrder()
    }

    @Test
    fun `sortedForDisplay NAME sorts alphabetically by display name`() {
        val hosts = listOf(host("192.168.1.1", hostname = "zeta"), host("192.168.1.2", hostname = "alpha"))
        val sorted = hosts.sortedForDisplay(DevicesSortOrder.NAME)
        assertThat(sorted.map { it.displayName() }).containsExactly("alpha", "zeta").inOrder()
    }

    @Test
    fun `sortedForDisplay DEVICE_TYPE puts hosts without a hint last`() {
        val hosts =
            listOf(
                host("192.168.1.1", deviceHint = null),
                host("192.168.1.2", deviceHint = hint("Network printer")),
            )
        val sorted = hosts.sortedForDisplay(DevicesSortOrder.DEVICE_TYPE)
        assertThat(sorted.map { it.address.hostAddress }).containsExactly("192.168.1.2", "192.168.1.1").inOrder()
    }

    @Test
    fun `sortedForDisplay LATENCY sorts ascending with no-sample hosts last`() {
        val hosts =
            listOf(
                host("192.168.1.1", rttMedianMs = null),
                host("192.168.1.2", rttMedianMs = 50.0),
                host("192.168.1.3", rttMedianMs = 5.0),
            )
        val sorted = hosts.sortedForDisplay(DevicesSortOrder.LATENCY)
        assertThat(sorted.map { it.address.hostAddress })
            .containsExactly("192.168.1.3", "192.168.1.2", "192.168.1.1")
            .inOrder()
    }

    @Test
    fun `sortedForDisplay LAST_SEEN sorts most recently observed first`() {
        val hosts =
            listOf(
                host("192.168.1.1", lastSeen = Instant.ofEpochSecond(100)),
                host("192.168.1.2", lastSeen = Instant.ofEpochSecond(300)),
            )
        val sorted = hosts.sortedForDisplay(DevicesSortOrder.LAST_SEEN)
        assertThat(sorted.map { it.address.hostAddress }).containsExactly("192.168.1.2", "192.168.1.1").inOrder()
    }

    @Test
    fun `filteredByConfidence keeps only the requested tiers`() {
        val hosts =
            listOf(
                host("192.168.1.1", confidence = HostConfidence.CONFIRMED),
                host("192.168.1.2", confidence = HostConfidence.ANNOUNCED),
                host("192.168.1.3", confidence = HostConfidence.STALE),
            )
        val filtered = hosts.filteredByConfidence(setOf(HostConfidence.CONFIRMED))
        assertThat(filtered.map { it.address.hostAddress }).containsExactly("192.168.1.1")
    }
}
