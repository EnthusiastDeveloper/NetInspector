package dev.enthusiastdev.netinspector.data.lan

import android.net.wifi.WifiManager
import com.google.common.truth.Truth.assertThat
import dev.enthusiastdev.netinspector.core.common.net.Ipv4Subnet
import dev.enthusiastdev.netinspector.core.model.lan.HostConfidence
import dev.enthusiastdev.netinspector.data.lan.mdns.MdnsProbe
import dev.enthusiastdev.netinspector.data.lan.netbios.NetBiosProbe
import dev.enthusiastdev.netinspector.data.lan.ssdp.SsdpProbe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.net.Inet4Address
import java.net.InetAddress
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class LanDiscoveryRepositoryTest {
    private val wifiManager = mockk<WifiManager>()
    private val mdnsProbe = mockk<MdnsProbe>()
    private val ssdpProbe = mockk<SsdpProbe>()
    private val netBiosProbe = mockk<NetBiosProbe>()
    private val sweepPipeline = mockk<LanSweepPipeline>()
    private val clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)

    private lateinit var repository: DefaultLanDiscoveryRepository

    @Before
    fun setUp() {
        every { wifiManager.createMulticastLock(any()) } returns mockk<WifiManager.MulticastLock>(relaxed = true)
        every { wifiManager.createWifiLock(any(), any()) } returns mockk<WifiManager.WifiLock>(relaxed = true)
        coEvery { mdnsProbe.discover() } returns emptyList()
        coEvery { ssdpProbe.discover() } returns emptyList()
        coEvery { netBiosProbe.discover(any()) } returns emptyList()
        coEvery { sweepPipeline.run(any(), any(), any(), any()) } just runs

        repository =
            DefaultLanDiscoveryRepository(
                wifiManager = wifiManager,
                mdnsProbe = mdnsProbe,
                ssdpProbe = ssdpProbe,
                netBiosProbe = netBiosProbe,
                sweepPipeline = sweepPipeline,
                clock = clock,
            )
    }

    @Test
    fun `switching network drops previous hosts even when the new network reuses the same subnet`() =
        runTest {
            // A neighbor's router that happens to hand out the same 192.168.1.0/24 range - the
            // BSSID is the only thing that actually distinguishes the two networks.
            val subnet = Ipv4Subnet(ip("192.168.1.0"), 24)

            repository.sweep(
                subnet = subnet,
                gateway = ip("192.168.1.1"),
                selfAddress = ip("192.168.1.42"),
                bssid = "AA:BB:CC:DD:EE:01",
            )

            repository.sweep(
                subnet = subnet,
                gateway = ip("192.168.1.1"),
                selfAddress = ip("192.168.1.99"),
                bssid = "AA:BB:CC:DD:EE:02",
            )

            val hosts = repository.hosts.first()
            assertThat(hosts.map { it.address }).containsExactly(ip("192.168.1.1"), ip("192.168.1.99"))
            assertThat(hosts.none { it.confidence == HostConfidence.STALE }).isTrue()
        }

    @Test
    fun `re-sweeping the same network keeps an absent host visible as stale instead of dropping it`() =
        runTest {
            val subnet = Ipv4Subnet(ip("192.168.1.0"), 24)
            val bssid = "AA:BB:CC:DD:EE:01"

            val selfAddress = ip("192.168.1.42")
            val gateway = ip("192.168.1.1")
            repository.sweep(subnet = subnet, gateway = gateway, selfAddress = selfAddress, bssid = bssid)
            repository.sweep(subnet = subnet, gateway = null, selfAddress = selfAddress, bssid = bssid)

            val hosts = repository.hosts.first()
            val gatewayHost = hosts.single { it.address == ip("192.168.1.1") }
            assertThat(gatewayHost.confidence).isEqualTo(HostConfidence.STALE)
        }

    private fun ip(text: String): Inet4Address = InetAddress.getByName(text) as Inet4Address
}
