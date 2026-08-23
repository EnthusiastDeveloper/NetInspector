package dev.enthusiastdev.netinspector.core.model.wifi

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * design §7.2 - one ranked candidate primary (20 MHz) channel for [band], sorted ascending by
 * [score]: lower means less interference at this device's vantage point right now.
 */
data class ChannelRecommendation(
    val band: Band,
    val channel: Int,
    val centerMhz: Int,
    val score: Double,
    val contributingApCount: Int,
    val isDfs: Boolean,
    val isNonPsc: Boolean,
)

/**
 * design §6.2's freqToChannel, inverted: the center frequency of a channel *number* on [band].
 *
 * Public because the UI needs it for the same reason it exists here - an AP's settings page talks
 * in channel numbers while the occupancy graph's axis is in MHz, so a channel recommendation has
 * to be able to name both if the user is to see where on the graph it is pointing.
 *
 * Note this is the center of the standard 20 MHz channel, which is not the same as a wide AP's
 * `ChannelSpan.centerMhz`: an 80 MHz AP with primary channel 44 is centered at 5210 MHz, while
 * channel 44 itself is centered at 5220 MHz.
 */
fun channelCenterMhz(
    band: Band,
    channel: Int,
): Int? =
    when (band) {
        Band.GHZ_2_4 -> 2407 + 5 * channel
        Band.GHZ_5 -> 5000 + 5 * channel
        Band.GHZ_6 -> 5950 + 5 * channel
        Band.UNKNOWN -> null
    }

/** design §7.2 - RSSI must leave the log domain before it is summed; averaging dBm values
 * directly is the common bug this exists to prevent. */
fun linearPower(dbm: Int): Double = 10.0.pow(dbm / 10.0)

/**
 * design §7.2 - the fraction of [candidate]'s span covered by [apSpan], weighted by the
 * counter-intuitive partial-overlap multiplier: a *co-channel* neighbour (identical span) is
 * less harmful than a *partially*-overlapping one, because co-channel stations share the medium
 * through CSMA/CA while partial overlap is raw noise that can't be decoded or deferred to.
 * An exact-span match keeps multiplier 1.0; any other non-zero overlap gets
 * [PARTIAL_OVERLAP_MULTIPLIER].
 */
fun overlapFraction(
    candidate: ChannelSpan,
    apSpan: ChannelSpan,
): Double {
    val overlapWidthMhz =
        (min(candidate.highMhz, apSpan.highMhz) - max(candidate.lowMhz, apSpan.lowMhz)).coerceAtLeast(0)
    if (overlapWidthMhz == 0) return 0.0
    val fraction = overlapWidthMhz.toDouble() / candidate.widthMhz
    val isCoChannel = candidate.lowMhz == apSpan.lowMhz && candidate.highMhz == apSpan.highMhz
    return fraction * if (isCoChannel) 1.0 else PARTIAL_OVERLAP_MULTIPLIER
}

/** design §7.2 - "slightly favours the 1/6/11 non-overlapping set on 2.4 GHz"; neutral (1.0)
 * on 5/6 GHz, where channels are already non-overlapping by regulation. */
fun bandPenalty(
    band: Band,
    channel: Int,
): Double = if (band == Band.GHZ_2_4 && channel in NON_OVERLAPPING_2_4_CHANNELS) NON_OVERLAPPING_BAND_PENALTY else 1.0

/**
 * design §7.2 - interference score for one candidate primary channel: the sum, over every
 * visible AP on [band], of its overlap with the candidate weighted by its linear power and the
 * band penalty. An AP with a secondary span (80+80 MHz) contributes both segments
 * independently, each at the AP's one reported RSSI. Returns the score alongside the count of
 * APs that contributed a non-zero amount, since the recommendation card shows both.
 */
fun channelScore(
    band: Band,
    candidateChannel: Int,
    accessPoints: List<AccessPoint>,
): Pair<Double, Int> {
    val candidateCenterMhz = candidateCenterMhz(band, candidateChannel) ?: return 0.0 to 0
    val candidate =
        ChannelSpan(
            centerMhz = candidateCenterMhz,
            widthMhz = CANDIDATE_WIDTH_MHZ,
            primaryChannel = candidateChannel,
            band = band,
        )
    val penalty = bandPenalty(band, candidateChannel)

    var score = 0.0
    var contributingApCount = 0
    for (ap in accessPoints) {
        if (ap.span.band != band) continue
        var contribution = 0.0
        for (span in listOfNotNull(ap.span, ap.secondarySpan)) {
            contribution += overlapFraction(candidate, span) * linearPower(ap.rssiDbm) * penalty
        }
        if (contribution > 0.0) contributingApCount++
        score += contribution
    }
    return score to contributingApCount
}

/**
 * design §7.2 - ranked candidate primary channels for [band], best (lowest score) first.
 * Candidates are the band's standard 20 MHz channel plan regardless of whether anything is
 * currently seen there, so an empty channel can still be recommended.
 */
fun recommendChannels(
    band: Band,
    accessPoints: List<AccessPoint>,
): List<ChannelRecommendation> =
    candidateChannels(band)
        .map { channel ->
            val (score, contributingApCount) = channelScore(band, channel, accessPoints)
            ChannelRecommendation(
                band = band,
                channel = channel,
                centerMhz = candidateCenterMhz(band, channel) ?: 0,
                score = score,
                contributingApCount = contributingApCount,
                isDfs = isDfsChannel(band, channel),
                isNonPsc = band == Band.GHZ_6 && !is6GhzPsc(band, channel),
            )
        }.sortedBy { it.score }

private const val PARTIAL_OVERLAP_MULTIPLIER = 1.5
private const val NON_OVERLAPPING_BAND_PENALTY = 0.9
private const val CANDIDATE_WIDTH_MHZ = 20
private val NON_OVERLAPPING_2_4_CHANNELS = setOf(1, 6, 11)

// design §6.2's freqToChannel inverted for each band's standard 20 MHz channel numbers - the
// 5 GHz list has the UNII-2 (52-64) / UNII-3 (149-165) gap, so it can't be a simple range.
private val CHANNELS_2_4 = (1..11).toList()
private val CHANNELS_5 =
    listOf(
        36,
        40,
        44,
        48,
        52,
        56,
        60,
        64,
        100,
        104,
        108,
        112,
        116,
        120,
        124,
        128,
        132,
        136,
        140,
        144,
        149,
        153,
        157,
        161,
        165,
    )
private val CHANNELS_6 = (1..233 step 4).toList()

private fun candidateChannels(band: Band): List<Int> =
    when (band) {
        Band.GHZ_2_4 -> CHANNELS_2_4
        Band.GHZ_5 -> CHANNELS_5
        Band.GHZ_6 -> CHANNELS_6
        Band.UNKNOWN -> emptyList()
    }

private fun candidateCenterMhz(
    band: Band,
    channel: Int,
): Int? = channelCenterMhz(band, channel)
