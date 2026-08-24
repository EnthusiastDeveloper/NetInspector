package dev.enthusiastdev.netinspector.core.model.wifi

import com.google.common.truth.Truth.assertThat
import org.junit.Test

private val DEFAULT_AP =
    ObservedAp(
        bssid = "AA:AA:AA:AA:AA:AA",
        ssid = "TestNet",
        rssiDbm = -50,
        band = "GHZ_5",
        centerFrequencyMhz = 5180,
        channelWidthMhz = 80,
        security = "WPA2",
        standard = "AC",
    )

private fun ap(
    bssid: String,
    mutate: ObservedAp.() -> ObservedAp = { this },
) = DEFAULT_AP.copy(bssid = bssid).mutate()

class ScanSessionDiffTest {
    @Test
    fun `an AP only in after is added`() {
        val diff = diffScanSessions(before = emptyList(), after = listOf(ap("AA:AA:AA:AA:AA:AA")))

        assertThat(diff.added.map { it.bssid }).containsExactly("AA:AA:AA:AA:AA:AA")
        assertThat(diff.removed).isEmpty()
        assertThat(diff.changed).isEmpty()
    }

    @Test
    fun `an AP only in before is removed`() {
        val diff = diffScanSessions(before = listOf(ap("AA:AA:AA:AA:AA:AA")), after = emptyList())

        assertThat(diff.removed.map { it.bssid }).containsExactly("AA:AA:AA:AA:AA:AA")
        assertThat(diff.added).isEmpty()
        assertThat(diff.changed).isEmpty()
    }

    @Test
    fun `an identical AP in both is unchanged, not added, removed, or changed`() {
        val bssid = "AA:AA:AA:AA:AA:AA"
        val diff = diffScanSessions(before = listOf(ap(bssid)), after = listOf(ap(bssid)))

        assertThat(diff.added).isEmpty()
        assertThat(diff.removed).isEmpty()
        assertThat(diff.changed).isEmpty()
        assertThat(diff.unchangedCount).isEqualTo(1)
    }

    @Test
    fun `an RSSI move under the threshold is not reported as changed`() {
        val bssid = "AA:AA:AA:AA:AA:AA"
        val diff =
            diffScanSessions(
                before = listOf(ap(bssid) { copy(rssiDbm = -50) }),
                after = listOf(ap(bssid) { copy(rssiDbm = -55) }),
                notableRssiDeltaDbm = 6,
            )

        assertThat(diff.changed).isEmpty()
        assertThat(diff.unchangedCount).isEqualTo(1)
    }

    @Test
    fun `an RSSI move at or over the threshold is reported as changed`() {
        val bssid = "AA:AA:AA:AA:AA:AA"
        val diff =
            diffScanSessions(
                before = listOf(ap(bssid) { copy(rssiDbm = -50) }),
                after = listOf(ap(bssid) { copy(rssiDbm = -56) }),
                notableRssiDeltaDbm = 6,
            )

        assertThat(diff.changed.map { it.before.bssid }).containsExactly(bssid)
        assertThat(diff.changed.single().rssiDeltaDbm).isEqualTo(-6)
    }

    @Test
    fun `a security-only change is reported even with identical RSSI`() {
        val bssid = "AA:AA:AA:AA:AA:AA"
        val diff =
            diffScanSessions(
                before = listOf(ap(bssid) { copy(security = "WPA2") }),
                after = listOf(ap(bssid) { copy(security = "WPA3") }),
            )

        val change = diff.changed.single()
        assertThat(change.securityChanged).isTrue()
        assertThat(change.standardChanged).isFalse()
        assertThat(change.channelChanged).isFalse()
    }

    @Test
    fun `a standard-only change is reported independently`() {
        val bssid = "AA:AA:AA:AA:AA:AA"
        val diff =
            diffScanSessions(
                before = listOf(ap(bssid) { copy(standard = "AC") }),
                after = listOf(ap(bssid) { copy(standard = "AX") }),
            )

        val change = diff.changed.single()
        assertThat(change.standardChanged).isTrue()
        assertThat(change.securityChanged).isFalse()
        assertThat(change.channelChanged).isFalse()
    }

    @Test
    fun `a channel-only change is reported independently`() {
        val bssid = "AA:AA:AA:AA:AA:AA"
        val diff =
            diffScanSessions(
                before = listOf(ap(bssid) { copy(centerFrequencyMhz = 5180, channelWidthMhz = 80) }),
                after = listOf(ap(bssid) { copy(centerFrequencyMhz = 5200, channelWidthMhz = 80) }),
            )

        val change = diff.changed.single()
        assertThat(change.channelChanged).isTrue()
        assertThat(change.securityChanged).isFalse()
        assertThat(change.standardChanged).isFalse()
    }
}
