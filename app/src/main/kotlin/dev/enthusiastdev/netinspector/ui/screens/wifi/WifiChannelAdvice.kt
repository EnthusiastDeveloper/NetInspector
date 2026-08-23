package dev.enthusiastdev.netinspector.ui.screens.wifi

import dev.enthusiastdev.netinspector.core.model.wifi.AccessPoint
import dev.enthusiastdev.netinspector.core.model.wifi.Band
import dev.enthusiastdev.netinspector.core.model.wifi.ChannelRecommendation
import dev.enthusiastdev.netinspector.core.model.wifi.ChannelSpan
import dev.enthusiastdev.netinspector.core.model.wifi.channelCenterMhz
import dev.enthusiastdev.netinspector.core.model.wifi.channelScore
import dev.enthusiastdev.netinspector.core.model.wifi.recommendChannels
import kotlin.math.roundToInt

/** The channel this device is actually associated on, scored on the same scale as the
 * candidates so the two can be compared directly. */
internal data class CurrentChannelUsage(
    val channel: Int,
    val centerMhz: Int,
    val score: Double,
    val overlappingApCount: Int,
)

internal data class ChannelAdvice(
    /** Null when this device isn't connected on [Band] at all - then there is nothing to compare
     * a recommendation *against*, and the card says only what it can stand behind. */
    val current: CurrentChannelUsage?,
    val recommendations: List<ChannelRecommendation>,
)

/**
 * Ranked candidate channels plus, when this device is connected on the same band, the current
 * channel measured the same way.
 *
 * The connected AP is excluded from every score. Left in, it contributes its own (by definition
 * strongest-seen) signal to whichever channel it occupies, so the channel you are already on is
 * penalised hardest and the ranking recommends moving almost unconditionally. What the question
 * "should I move?" actually means is "how much *other* traffic is on each channel", which is what
 * dropping the connected BSSID measures.
 */
internal fun channelAdvice(
    band: Band,
    accessPoints: List<AccessPoint>,
    connectedBssid: String?,
    connectedSpan: ChannelSpan?,
): ChannelAdvice {
    val neighbours = accessPoints.filterNot { connectedBssid != null && it.bssid == connectedBssid }
    val recommendations = recommendChannels(band, neighbours)
    val current =
        connectedSpan
            ?.takeIf { it.band == band }
            ?.let { span ->
                val (score, overlapping) = channelScore(band, span.primaryChannel, neighbours)
                CurrentChannelUsage(
                    channel = span.primaryChannel,
                    centerMhz = channelCenterMhz(band, span.primaryChannel) ?: span.centerMhz,
                    score = score,
                    overlappingApCount = overlapping,
                )
            }
    return ChannelAdvice(current = current, recommendations = recommendations)
}

/** Below this the two channels are effectively tied and claiming an improvement would be noise
 * dressed up as advice. */
private const val MEANINGFUL_REDUCTION = 0.05

/**
 * How much less overlapping signal power a candidate carries than the current channel, as a
 * percentage, or null when there is nothing meaningful to claim - the current channel is already
 * clear, the candidate is no better, or the difference is within the noise.
 *
 * Both scores are linear power sums (design §7.2 converts out of dBm before summing), so their
 * ratio is a real proportion rather than the meaningless subtraction of two log values.
 */
internal fun interferenceReductionPercent(
    currentScore: Double,
    candidateScore: Double,
): Int? {
    if (currentScore <= 0.0) return null
    val reduction = (currentScore - candidateScore) / currentScore
    if (reduction < MEANINGFUL_REDUCTION) return null
    return (reduction * 100).roundToInt()
}

internal fun overlapPhrase(apCount: Int): String =
    when (apCount) {
        0 -> "no other networks overlap it"
        1 -> "1 other network overlaps it"
        else -> "$apCount other networks overlap it"
    }

/** Why a flagged channel isn't a free win, in the terms the flag actually costs the user. */
internal fun ChannelRecommendation.caveats(): List<String> =
    buildList {
        if (isDfs) add("DFS - the radio must pause if it detects weather radar")
        if (isNonPsc) add("not a 6 GHz preferred scanning channel - some clients will be slower to find it")
    }
