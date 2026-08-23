package dev.enthusiastdev.netinspector.ui.screens.wifi

import com.google.common.truth.Truth.assertThat
import dev.enthusiastdev.netinspector.core.model.wifi.AccessPoint
import dev.enthusiastdev.netinspector.core.model.wifi.Band
import dev.enthusiastdev.netinspector.core.model.wifi.ChannelSpan
import dev.enthusiastdev.netinspector.core.model.wifi.SecurityType
import dev.enthusiastdev.netinspector.core.model.wifi.WifiStandard
import org.junit.Test
import java.time.Instant

private fun span24(channel: Int) =
    ChannelSpan(centerMhz = 2407 + 5 * channel, widthMhz = 20, primaryChannel = channel, band = Band.GHZ_2_4)

private fun ap(
    bssid: String,
    channel: Int,
    rssiDbm: Int = -50,
) = AccessPoint(
    bssid = bssid,
    ssid = bssid,
    rssiDbm = rssiDbm,
    span = span24(channel),
    secondarySpan = null,
    security = setOf(SecurityType.WPA2),
    standard = WifiStandard.AC,
    vendor = null,
    isConnected = false,
    isDfsChannel = false,
    is6GhzPsc = false,
    firstSeen = Instant.EPOCH,
    lastSeen = Instant.EPOCH,
)

class WifiChannelAdviceTest {
    @Test
    fun `the connected AP is excluded from every candidate's score`() {
        val self = ap("self", channel = 1, rssiDbm = -40)
        val advice = channelAdvice(Band.GHZ_2_4, listOf(self), connectedBssid = "self", connectedSpan = self.span)

        // With the only visible AP being our own, nothing overlaps anything.
        assertThat(advice.current).isNotNull()
        assertThat(advice.current!!.overlappingApCount).isEqualTo(0)
        assertThat(advice.recommendations.first().contributingApCount).isEqualTo(0)
    }

    @Test
    fun `the current channel is scored on the same basis as the candidates`() {
        val self = ap("self", channel = 1, rssiDbm = -40)
        val neighbour = ap("neighbour", channel = 1, rssiDbm = -45)
        val advice =
            channelAdvice(
                Band.GHZ_2_4,
                listOf(self, neighbour),
                connectedBssid = "self",
                connectedSpan = self.span,
            )

        val current = advice.current!!
        assertThat(current.channel).isEqualTo(1)
        assertThat(current.centerMhz).isEqualTo(2412)
        assertThat(current.overlappingApCount).isEqualTo(1)
        // Channel 11 is far enough away from the neighbour on 1 that nothing overlaps it.
        val eleven = advice.recommendations.single { it.channel == 11 }
        assertThat(eleven.contributingApCount).isEqualTo(0)
        assertThat(eleven.score).isLessThan(current.score)
    }

    @Test
    fun `there is no current channel when this device is not on the band being shown`() {
        val self = ap("self", channel = 1)
        val advice = channelAdvice(Band.GHZ_5, listOf(self), connectedBssid = "self", connectedSpan = self.span)
        assertThat(advice.current).isNull()
        assertThat(advice.recommendations).isNotEmpty()
    }

    @Test
    fun `there is no current channel when the BSSID is unknown`() {
        val neighbour = ap("neighbour", channel = 6)
        val advice = channelAdvice(Band.GHZ_2_4, listOf(neighbour), connectedBssid = null, connectedSpan = null)
        assertThat(advice.current).isNull()
        assertThat(advice.recommendations.single { it.channel == 6 }.contributingApCount).isEqualTo(1)
    }

    @Test
    fun `interference reduction is reported as a proportion of the current score`() {
        assertThat(interferenceReductionPercent(currentScore = 100.0, candidateScore = 25.0)).isEqualTo(75)
        assertThat(interferenceReductionPercent(currentScore = 100.0, candidateScore = 0.0)).isEqualTo(100)
    }

    @Test
    fun `no improvement is claimed when there is nothing to improve on`() {
        // Already clear, so any candidate is at best a tie.
        assertThat(interferenceReductionPercent(currentScore = 0.0, candidateScore = 0.0)).isNull()
        // Worse than where we are.
        assertThat(interferenceReductionPercent(currentScore = 10.0, candidateScore = 20.0)).isNull()
        // Within the noise - a 2% difference is not advice.
        assertThat(interferenceReductionPercent(currentScore = 100.0, candidateScore = 98.0)).isNull()
    }

    @Test
    fun `overlap is phrased for the count it describes`() {
        assertThat(overlapPhrase(0)).isEqualTo("no other networks overlap it")
        assertThat(overlapPhrase(1)).isEqualTo("1 other network overlaps it")
        assertThat(overlapPhrase(4)).isEqualTo("4 other networks overlap it")
    }
}
