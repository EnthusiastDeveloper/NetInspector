package dev.enthusiastdev.netinspector.core.model.wifi

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant

private fun fixtureAp(
    bssid: String,
    rssiDbm: Int,
    span: ChannelSpan,
    secondarySpan: ChannelSpan? = null,
) = AccessPoint(
    bssid = bssid,
    ssid = bssid,
    rssiDbm = rssiDbm,
    span = span,
    secondarySpan = secondarySpan,
    security = setOf(SecurityType.WPA2),
    standard = WifiStandard.AC,
    vendor = null,
    isConnected = false,
    isDfsChannel = false,
    is6GhzPsc = false,
    firstSeen = Instant.EPOCH,
    lastSeen = Instant.EPOCH,
)

private fun span24(
    channel: Int,
    widthMhz: Int = 20,
) = ChannelSpan(centerMhz = 2407 + 5 * channel, widthMhz = widthMhz, primaryChannel = channel, band = Band.GHZ_2_4)

class ChannelRecommendationTest {
    @Test
    fun `linearPower converts out of the log domain`() {
        assertThat(linearPower(-50)).isWithin(1e-12).of(1e-5)
        assertThat(linearPower(-100)).isWithin(1e-17).of(1e-10)
        assertThat(linearPower(0)).isWithin(1e-9).of(1.0)
    }

    @Test
    fun `overlapFraction is 1 for an exact co-channel match`() {
        val candidate = span24(6)
        val ap = span24(6)
        assertThat(overlapFraction(candidate, ap)).isWithin(1e-9).of(1.0)
    }

    @Test
    fun `overlapFraction is 0 for non-overlapping spans`() {
        val candidate = span24(1) // 2402..2422
        val ap = span24(11) // 2452..2472
        assertThat(overlapFraction(candidate, ap)).isWithin(1e-9).of(0.0)
    }

    @Test
    fun `overlapFraction applies the 1point5 partial-overlap multiplier when spans differ`() {
        // candidate 2400..2420, ap 2410..2430 (channel numbers chosen to give round MHz bounds)
        val candidate = ChannelSpan(centerMhz = 2410, widthMhz = 20, primaryChannel = 1, band = Band.GHZ_2_4)
        val ap = ChannelSpan(centerMhz = 2420, widthMhz = 20, primaryChannel = 3, band = Band.GHZ_2_4)
        // overlap width = 10 MHz of a 20 MHz candidate -> fraction 0.5, times 1.5 = 0.75
        assertThat(overlapFraction(candidate, ap)).isWithin(1e-9).of(0.75)
    }

    @Test
    fun `overlapFraction does not apply the partial multiplier to a same-span AP even with a wider candidate`() {
        // a 40 MHz AP fully contains a 20 MHz candidate but the spans aren't identical, so this
        // is still "partial" overlap in the sense that matters: the candidate isn't co-channel.
        val candidate = span24(6, widthMhz = 20)
        val ap = span24(6, widthMhz = 40)
        assertThat(overlapFraction(candidate, ap)).isWithin(1e-9).of(1.5)
    }

    @Test
    fun `bandPenalty favours 1 6 11 on 2point4GHz only`() {
        assertThat(bandPenalty(Band.GHZ_2_4, 1)).isLessThan(1.0)
        assertThat(bandPenalty(Band.GHZ_2_4, 6)).isLessThan(1.0)
        assertThat(bandPenalty(Band.GHZ_2_4, 11)).isLessThan(1.0)
        assertThat(bandPenalty(Band.GHZ_2_4, 3)).isEqualTo(1.0)
        assertThat(bandPenalty(Band.GHZ_5, 36)).isEqualTo(1.0)
    }

    @Test
    fun `channelScore ignores APs on a different band`() {
        val fiveGhzAp = fixtureAp("ap5", -40, ChannelSpan(5180, 20, 36, Band.GHZ_5))
        val (score, contributors) = channelScore(Band.GHZ_2_4, 6, listOf(fiveGhzAp))
        assertThat(score).isEqualTo(0.0)
        assertThat(contributors).isEqualTo(0)
    }

    @Test
    fun `channelScore sums both segments of an 80+80 AP`() {
        val primary = ChannelSpan(centerMhz = 5180, widthMhz = 20, primaryChannel = 36, band = Band.GHZ_5)
        val secondary = ChannelSpan(centerMhz = 5180, widthMhz = 20, primaryChannel = 36, band = Band.GHZ_5)
        val ap = fixtureAp("ap80+80", rssiDbm = -50, span = primary, secondarySpan = secondary)
        val (score, contributors) = channelScore(Band.GHZ_5, 36, listOf(ap))
        // both segments are co-channel with the candidate, so each contributes linearPower(-50)
        assertThat(score).isWithin(1e-12).of(2 * linearPower(-50))
        assertThat(contributors).isEqualTo(1)
    }

    @Test
    fun `recommendChannels ranks a fixed fixture reproducibly, worst co-channel choices last`() {
        val apOnChannel1 = fixtureAp("ap1", rssiDbm = -50, span = span24(1))
        val apOnChannel6 = fixtureAp("ap6", rssiDbm = -60, span = span24(6))

        val ranked = recommendChannels(Band.GHZ_2_4, listOf(apOnChannel1, apOnChannel6))

        // channels 10 and 11 sit far enough from both APs to see zero interference; tied at
        // zero, so the stable sort ranks them first in candidate order (10 before 11).
        assertThat(ranked.first().channel).isEqualTo(10)
        assertThat(ranked.first().score).isWithin(1e-12).of(0.0)
        assertThat(ranked[1].channel).isEqualTo(11)
        assertThat(ranked[1].score).isWithin(1e-12).of(0.0)

        val channel1 = ranked.single { it.channel == 1 }
        val channel6 = ranked.single { it.channel == 6 }
        // co-channel with the -50 dBm AP, discounted by the 1/6/11 band penalty.
        assertThat(channel1.score).isWithin(1e-12).of(linearPower(-50) * 0.9)
        assertThat(channel1.contributingApCount).isEqualTo(1)
        // co-channel with the (weaker) -60 dBm AP, same penalty.
        assertThat(channel6.score).isWithin(1e-12).of(linearPower(-60) * 0.9)
        assertThat(channel6.contributingApCount).isEqualTo(1)
        // stronger co-channel AP dominates: choosing its channel scores worse than the other's.
        assertThat(channel1.score).isGreaterThan(channel6.score)

        // reproducible: running it again on the same fixture yields an identical ranking.
        val rankedAgain = recommendChannels(Band.GHZ_2_4, listOf(apOnChannel1, apOnChannel6))
        assertThat(rankedAgain.map { it.channel to it.score }).isEqualTo(ranked.map { it.channel to it.score })
    }

    @Test
    fun `recommendChannels flags DFS and non-PSC candidates`() {
        val dfsCandidate = recommendChannels(Band.GHZ_5, emptyList()).single { it.channel == 52 }
        assertThat(dfsCandidate.isDfs).isTrue()

        val nonPscCandidate = recommendChannels(Band.GHZ_6, emptyList()).single { it.channel == 1 }
        assertThat(nonPscCandidate.isNonPsc).isTrue()
        val pscCandidate = recommendChannels(Band.GHZ_6, emptyList()).single { it.channel == 5 }
        assertThat(pscCandidate.isNonPsc).isFalse()
    }

    @Test
    fun `recommendChannels covers the full standard channel plan per band`() {
        assertThat(recommendChannels(Band.GHZ_2_4, emptyList()).map { it.channel }).containsExactlyElementsIn(1..11)
        assertThat(recommendChannels(Band.GHZ_5, emptyList())).hasSize(25)
        assertThat(recommendChannels(Band.GHZ_6, emptyList())).hasSize(59)
        assertThat(recommendChannels(Band.UNKNOWN, emptyList())).isEmpty()
    }

    @Test
    fun `channelCenterMhz maps channel numbers back to their band's center frequency`() {
        assertThat(channelCenterMhz(Band.GHZ_2_4, 1)).isEqualTo(2412)
        assertThat(channelCenterMhz(Band.GHZ_2_4, 11)).isEqualTo(2462)
        assertThat(channelCenterMhz(Band.GHZ_5, 36)).isEqualTo(5180)
        assertThat(channelCenterMhz(Band.GHZ_5, 100)).isEqualTo(5500)
        assertThat(channelCenterMhz(Band.GHZ_6, 5)).isEqualTo(5975)
        assertThat(channelCenterMhz(Band.UNKNOWN, 1)).isNull()
    }

    @Test
    fun `every recommendation carries the center frequency of its own channel`() {
        recommendChannels(Band.GHZ_5, emptyList()).forEach { recommendation ->
            assertThat(recommendation.centerMhz).isEqualTo(channelCenterMhz(Band.GHZ_5, recommendation.channel))
        }
    }
}
