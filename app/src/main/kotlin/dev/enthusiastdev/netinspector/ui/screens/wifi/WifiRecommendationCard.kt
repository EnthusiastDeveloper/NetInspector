package dev.enthusiastdev.netinspector.ui.screens.wifi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.enthusiastdev.netinspector.core.designsystem.component.InfoCard
import dev.enthusiastdev.netinspector.core.model.wifi.AccessPoint
import dev.enthusiastdev.netinspector.core.model.wifi.Band
import dev.enthusiastdev.netinspector.core.model.wifi.ChannelRecommendation
import dev.enthusiastdev.netinspector.core.model.wifi.ChannelSpan

private const val TOP_RECOMMENDATIONS_SHOWN = 3

/**
 * design §7.2 - the recommendation card: ranked candidate channels, gated on having more than a
 * single sample.
 *
 * The card used to name a channel and a bare AP count and stop there, which left the two obvious
 * questions unanswered: better than *what*, and where on the graph above is that? So each entry
 * now carries its center frequency - the same units as the graph's X axis, so "channel 100" can
 * be located on the picture the user is looking at - and states the improvement against the
 * channel this device is currently on, in the same terms ("N networks overlap", "~x% less
 * overlapping signal") for both, so the comparison is like-for-like rather than a ranking the
 * user has to take on faith.
 */
@Composable
internal fun WifiChannelRecommendationCard(
    band: Band,
    accessPoints: List<AccessPoint>,
    sampleCount: Int,
    modifier: Modifier = Modifier,
    connectedBssid: String? = null,
    connectedSpan: ChannelSpan? = null,
) {
    InfoCard(title = "Recommended channel", modifier = modifier) {
        if (sampleCount < MIN_RECOMMENDATION_SAMPLES) {
            Text(
                "Scan again to get a recommendation - based on $sampleCount of $MIN_RECOMMENDATION_SAMPLES " +
                    "samples so far.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@InfoCard
        }
        val advice =
            remember(accessPoints, band, connectedBssid, connectedSpan) {
                channelAdvice(band, accessPoints, connectedBssid, connectedSpan)
            }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MethodologyLine(sampleCount, isComparing = advice.current != null)
            advice.current?.let { CurrentChannelLine(it, advice.recommendations.firstOrNull()) }
            advice.recommendations.take(TOP_RECOMMENDATIONS_SHOWN).forEachIndexed { index, recommendation ->
                RecommendationRow(index, recommendation, advice.current)
            }
        }
    }
}

@Composable
private fun MethodologyLine(
    sampleCount: Int,
    isComparing: Boolean,
) {
    val ownNetworkNote = if (isComparing) " Your own network is left out of the comparison." else ""
    Text(
        "Ranked by how much overlapping signal power each 20 MHz channel carries at this spot, over " +
            "$sampleCount scans.$ownNetworkNote Another AP's view of the same room may differ.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun CurrentChannelLine(
    current: CurrentChannelUsage,
    best: ChannelRecommendation?,
) {
    val alreadyBest = best != null && best.channel == current.channel
    val suffix = if (alreadyBest) " Nothing here is measurably clearer - staying put is fine." else ""
    Text(
        "You're on channel ${current.channel} (center ${current.centerMhz} MHz): " +
            "${overlapPhrase(current.overlappingApCount)}.$suffix",
        style = MaterialTheme.typography.bodyMedium,
    )
}

/** A long rationale next to a long label can exceed a single row's width, so this stacks the two
 * lines instead of using InfoRow's side-by-side layout, which was built for short label/value
 * pairs and overlaps when both are long. */
@Composable
private fun RecommendationRow(
    index: Int,
    recommendation: ChannelRecommendation,
    current: CurrentChannelUsage?,
) {
    val isCurrent = current != null && current.channel == recommendation.channel
    Column {
        Text(
            "#${index + 1} · Channel ${recommendation.channel} - center ${recommendation.centerMhz} MHz" +
                if (isCurrent) " (your channel)" else "",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            recommendation.rationale(current, isCurrent),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun ChannelRecommendation.rationale(
    current: CurrentChannelUsage?,
    isCurrent: Boolean,
): String {
    val sentences = mutableListOf(overlapPhrase(contributingApCount).replaceFirstChar { it.uppercase() })
    val reduction = current?.let { interferenceReductionPercent(it.score, score) }
    when {
        isCurrent -> Unit
        reduction != null -> sentences += "about $reduction% less overlapping signal than channel ${current?.channel}"
        current != null && current.overlappingApCount == 0 && contributingApCount == 0 ->
            sentences += "as clear as channel ${current.channel}, so moving buys nothing"
    }
    sentences += caveats()
    return sentences.joinToString(" · ")
}
