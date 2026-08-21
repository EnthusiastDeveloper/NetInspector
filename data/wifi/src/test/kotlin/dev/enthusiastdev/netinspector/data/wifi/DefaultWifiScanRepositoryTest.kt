package dev.enthusiastdev.netinspector.data.wifi

import com.google.common.truth.Truth.assertThat
import dev.enthusiastdev.netinspector.core.model.wifi.AccessPoint
import dev.enthusiastdev.netinspector.core.model.wifi.Band
import dev.enthusiastdev.netinspector.core.model.wifi.ChannelSpan
import dev.enthusiastdev.netinspector.core.model.wifi.ScanSnapshot
import dev.enthusiastdev.netinspector.core.model.wifi.SecurityType
import dev.enthusiastdev.netinspector.core.model.wifi.WifiStandard
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant

class DefaultWifiScanRepositoryTest {
    @Test
    fun `a BSSID re-seen in a later scan generation is refreshed in place, keeping its earliest firstSeen`() =
        runTest {
            val firstSeen = Instant.parse("2026-01-01T00:00:00Z")
            val secondScanAt = Instant.parse("2026-01-01T00:01:00Z")
            val scanGovernor =
                fakeScanGovernor(
                    ScanSnapshot(listOf(accessPoint("AA:BB:CC:00:00:01", firstSeen, rssiDbm = -70)), firstSeen),
                    ScanSnapshot(
                        listOf(accessPoint("AA:BB:CC:00:00:01", secondScanAt, rssiDbm = -55)),
                        secondScanAt,
                    ),
                )

            val states = DefaultWifiScanRepository(scanGovernor).scanState.toList()

            val merged = states.last().accessPoints.single { it.bssid == "AA:BB:CC:00:00:01" }
            assertThat(merged.firstSeen).isEqualTo(firstSeen)
            assertThat(merged.lastSeen).isEqualTo(secondScanAt)
            assertThat(merged.rssiDbm).isEqualTo(-55)
            assertThat(states.last().sampleCount).isEqualTo(2)
        }

    @Test
    fun `an access point absent from a later scan generation stays visible at its last-known state`() =
        runTest {
            // design §3, §7.2 - unlike DefaultLanDiscoveryRepository's host map, a missed AP
            // is never dropped or greyed here; it's a Wi-Fi channel-planning tool, not a "what's
            // on my LAN right now" view, so a neighbor's AP that drops out of one scan still
            // belongs in the channel picture.
            val t1 = Instant.parse("2026-01-01T00:00:00Z")
            val t2 = Instant.parse("2026-01-01T00:01:00Z")
            val neighbor = accessPoint("AA:BB:CC:00:00:02", t1)
            val scanGovernor =
                fakeScanGovernor(
                    ScanSnapshot(listOf(accessPoint("AA:BB:CC:00:00:01", t1), neighbor), t1),
                    ScanSnapshot(listOf(accessPoint("AA:BB:CC:00:00:01", t2)), t2),
                )

            val states = DefaultWifiScanRepository(scanGovernor).scanState.toList()

            assertThat(states.last().accessPoints.map { it.bssid })
                .containsExactly("AA:BB:CC:00:00:01", "AA:BB:CC:00:00:02")
            assertThat(states.last().accessPoints.single { it.bssid == neighbor.bssid }).isEqualTo(neighbor)
        }

    private fun fakeScanGovernor(vararg snapshots: ScanSnapshot): ScanGovernor =
        mockk<ScanGovernor>().apply { every { results } returns flowOf(*snapshots) }

    private fun accessPoint(
        bssid: String,
        seenAt: Instant,
        rssiDbm: Int = -60,
    ): AccessPoint =
        AccessPoint(
            bssid = bssid,
            ssid = "Test Network",
            rssiDbm = rssiDbm,
            span = ChannelSpan(centerMhz = 2437, widthMhz = 20, primaryChannel = 6, band = Band.GHZ_2_4),
            secondarySpan = null,
            security = setOf(SecurityType.WPA2),
            standard = WifiStandard.AX,
            vendor = null,
            isConnected = false,
            isDfsChannel = false,
            is6GhzPsc = false,
            firstSeen = seenAt,
            lastSeen = seenAt,
        )
}
