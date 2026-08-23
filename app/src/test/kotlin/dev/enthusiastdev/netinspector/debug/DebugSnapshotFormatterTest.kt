package dev.enthusiastdev.netinspector.debug

import com.google.common.truth.Truth.assertThat
import dev.enthusiastdev.netinspector.core.model.connection.ConnectionSnapshot
import dev.enthusiastdev.netinspector.core.model.lan.Evidence
import dev.enthusiastdev.netinspector.core.model.lan.EvidenceSource
import dev.enthusiastdev.netinspector.core.model.lan.Host
import dev.enthusiastdev.netinspector.core.model.lan.HostConfidence
import dev.enthusiastdev.netinspector.core.model.lan.SweepProgress
import dev.enthusiastdev.netinspector.core.model.wifi.AccessPoint
import dev.enthusiastdev.netinspector.core.model.wifi.Band
import dev.enthusiastdev.netinspector.core.model.wifi.ChannelSpan
import dev.enthusiastdev.netinspector.core.model.wifi.SecurityType
import dev.enthusiastdev.netinspector.core.model.wifi.WifiStandard
import org.junit.Test
import java.net.Inet4Address
import java.net.InetAddress
import java.time.Instant

private fun addr(host: String) = InetAddress.getByName(host) as Inet4Address

private fun host(
    address: String,
    isGateway: Boolean = false,
    isSelf: Boolean = false,
) = Host(
    address = addr(address),
    confidence = HostConfidence.CONFIRMED,
    evidence = listOf(Evidence(EvidenceSource.ICMP, Instant.EPOCH)),
    hostnames = emptyMap(),
    macAddress = null,
    vendor = null,
    deviceHint = null,
    openPorts = emptyList(),
    services = emptyList(),
    icmpReplyTtl = null,
    rttMedianMs = null,
    isGateway = isGateway,
    isSelf = isSelf,
)

private fun emptyProgress() = SweepProgress(isRunning = false, addressesProbed = 0, addressesTotal = 0)

class DebugSnapshotFormatterTest {
    @Test
    fun `empty inputs produce a stable not-connected, no-data shape`() {
        val text = formatDebugSnapshot(null, emptyList(), emptyProgress(), emptyList(), emptyList())

        assertThat(text).contains("Not connected to Wi-Fi.")
        assertThat(text).contains("No hosts discovered yet.")
        assertThat(text).contains("No scan results.")
        assertThat(text).contains("No diagnostic runs recorded.")
    }

    @Test
    fun `formats connection, hosts, scan and diagnostics sections when populated`() {
        val connection =
            ConnectionSnapshot(
                ssid = "Home 5G",
                bssid = "aa:bb:cc:dd:ee:ff",
                rssiDbm = -55,
                txLinkSpeedMbps = 300,
                rxLinkSpeedMbps = 300,
                span = null,
                standard = WifiStandard.AX,
                ipv4 = null,
                ipv6 = emptyList(),
                gateway = addr("192.168.1.1"),
                dnsServers = emptyList(),
                domains = null,
                hasInternet = true,
                isCaptivePortal = false,
                isMetered = false,
            )
        val hosts = listOf(host("192.168.1.1", isGateway = true), host("192.168.1.50"))
        val progress = SweepProgress(isRunning = true, addressesProbed = 40, addressesTotal = 254)
        val accessPoints =
            listOf(
                AccessPoint(
                    bssid = "aa:bb:cc:dd:ee:ff",
                    ssid = "Home 5G",
                    rssiDbm = -55,
                    span = ChannelSpan(centerMhz = 5180, widthMhz = 80, primaryChannel = 36, band = Band.GHZ_5),
                    secondarySpan = null,
                    security = setOf(SecurityType.WPA2),
                    standard = WifiStandard.AX,
                    vendor = null,
                    isConnected = true,
                    isDfsChannel = false,
                    is6GhzPsc = false,
                    firstSeen = Instant.EPOCH,
                    lastSeen = Instant.EPOCH,
                ),
            )
        val diagnosticRuns =
            listOf(DiagnosticRunSummary("PING", "192.168.1.1", 1_000L, "4/4 replies, avg 5ms"))

        val text = formatDebugSnapshot(connection, hosts, progress, accessPoints, diagnosticRuns)

        assertThat(text).contains("SSID: Home 5G")
        assertThat(text).contains("192.168.1.1")
        assertThat(text).contains("[gateway]")
        assertThat(text).contains("40/254 probed")
        assertThat(text).contains("Home 5G (aa:bb:cc:dd:ee:ff)")
        assertThat(text).contains("PING 192.168.1.1: 4/4 replies, avg 5ms")
    }
}
