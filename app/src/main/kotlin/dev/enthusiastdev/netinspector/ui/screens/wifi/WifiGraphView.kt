package dev.enthusiastdev.netinspector.ui.screens.wifi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.enthusiastdev.netinspector.core.designsystem.chart.ChannelOccupancyGraph
import dev.enthusiastdev.netinspector.core.designsystem.chart.OccupancyCurve
import dev.enthusiastdev.netinspector.core.designsystem.chart.OccupancySpan
import dev.enthusiastdev.netinspector.core.designsystem.component.InfoCard
import dev.enthusiastdev.netinspector.core.model.wifi.AccessPoint
import dev.enthusiastdev.netinspector.core.model.wifi.Band
import dev.enthusiastdev.netinspector.core.model.wifi.ChannelRecommendation
import dev.enthusiastdev.netinspector.core.model.wifi.ChannelSpan
import dev.enthusiastdev.netinspector.core.model.wifi.recommendChannels
import dev.enthusiastdev.netinspector.ui.screens.connection.label

internal val BAND_TABS = listOf(Band.GHZ_2_4, Band.GHZ_5, Band.GHZ_6)

/** design §7.2 - "refuses to recommend from a single scan": below this many samples the
 * recommendation card explains why it's withholding a ranking rather than guessing. */
internal const val MIN_RECOMMENDATION_SAMPLES = 2
private const val TOP_RECOMMENDATIONS_SHOWN = 3

/** design §7.1/§7.2 - one band per tab, the frequency-based occupancy graph for whichever is
 * selected plus its channel recommendation. Tapping a curve highlights that AP everywhere
 * (graph and label) until tapped again or the band changes. */
@Composable
internal fun WifiGraphView(
    accessPoints: List<AccessPoint>,
    sampleCount: Int,
    modifier: Modifier = Modifier,
) {
    var selectedBand by rememberSaveable { mutableStateOf(Band.GHZ_5) }
    var highlightedBssid by rememberSaveable { mutableStateOf<String?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        WifiBandTabs(selectedBand) { band ->
            selectedBand = band
            highlightedBssid = null
        }
        WifiGraphCanvas(
            accessPoints = accessPoints,
            band = selectedBand,
            highlightedBssid = highlightedBssid,
            onCurveTap = { bssid -> highlightedBssid = if (highlightedBssid == bssid) null else bssid },
        )
        WifiGraphLegend()
        WifiChannelRecommendationCard(
            band = selectedBand,
            accessPoints = accessPoints,
            sampleCount = sampleCount,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
internal fun WifiBandTabs(
    selectedBand: Band,
    onBandSelected: (Band) -> Unit,
) {
    val selectedIndex = BAND_TABS.indexOf(selectedBand)
    TabRow(selectedTabIndex = selectedIndex) {
        BAND_TABS.forEach { band ->
            Tab(
                selected = band == selectedBand,
                onClick = { onBandSelected(band) },
                text = { Text(band.label()) },
            )
        }
    }
}

@Composable
internal fun WifiGraphCanvas(
    accessPoints: List<AccessPoint>,
    band: Band,
    highlightedBssid: String?,
    onCurveTap: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val curves = remember(accessPoints, band) { accessPoints.toOccupancyCurves(band) }
    val axisRange = band.axisRangeMhz()

    if (curves.isEmpty()) {
        Text(
            "No networks visible on this band",
            modifier = modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    } else {
        ChannelOccupancyGraph(
            curves = curves,
            axisLowMhz = axisRange.first,
            axisHighMhz = axisRange.last,
            modifier = modifier.padding(16.dp),
            highlightedKey = highlightedBssid,
            onCurveTap = onCurveTap,
        )
    }
}

@Composable
internal fun WifiGraphLegend(modifier: Modifier = Modifier) {
    Text(
        "Curve width shows channel width; height shows signal strength. Tap a curve to highlight it.",
        modifier = modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** design §7.2 - the recommendation card: ranked candidate channels, contributing AP count and
 * plain-language rationale, gated on having more than a single sample. */
@Composable
internal fun WifiChannelRecommendationCard(
    band: Band,
    accessPoints: List<AccessPoint>,
    sampleCount: Int,
    modifier: Modifier = Modifier,
) {
    val recommendations = remember(accessPoints, band) { recommendChannels(band, accessPoints) }

    InfoCard(title = "Recommended channel", modifier = modifier) {
        if (sampleCount < MIN_RECOMMENDATION_SAMPLES) {
            Text(
                "Scan again to get a recommendation - based on $sampleCount of $MIN_RECOMMENDATION_SAMPLES " +
                    "samples so far.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Based on $sampleCount scans from this device's vantage point - another AP's view may differ.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                recommendations.take(TOP_RECOMMENDATIONS_SHOWN).forEachIndexed { index, recommendation ->
                    // A long rationale ("No competing networks · DFS") next to a long label
                    // ("#1 - Channel 100") can exceed a single row's width, so this stacks the
                    // two lines instead of using InfoRow's side-by-side layout, which was built
                    // for short label/value pairs and overlaps when both are long.
                    Column {
                        Text(
                            "#${index + 1} - Channel ${recommendation.channel}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            recommendation.rationale(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun ChannelRecommendation.rationale(): String {
    val flags =
        buildList {
            if (isDfs) add("DFS")
            if (isNonPsc) add("non-PSC")
        }
    val base =
        when (contributingApCount) {
            0 -> "No competing networks"
            1 -> "1 network overlaps here"
            else -> "$contributingApCount networks overlap here"
        }
    return if (flags.isEmpty()) base else "$base · ${flags.joinToString()}"
}

/** Matches the ranges `bandOf()` classifies (core:model) - kept local since that function
 * classifies a frequency, it doesn't expose the band's own bounds. */
internal fun Band.axisRangeMhz(): IntRange =
    when (this) {
        Band.GHZ_2_4 -> 2400..2500
        Band.GHZ_5 -> 5150..5900
        Band.GHZ_6 -> 5925..7125
        Band.UNKNOWN -> 0..0
    }

private fun List<AccessPoint>.toOccupancyCurves(band: Band): List<OccupancyCurve> =
    filter { it.span.band == band }
        .map { ap ->
            OccupancyCurve(
                primary = ap.span.toOccupancySpan(),
                secondary = ap.secondarySpan?.toOccupancySpan(),
                rssiDbm = ap.rssiDbm,
                label = ap.ssid.ifEmpty { "<hidden>" },
                colorSeed = ap.bssid.hashCode(),
                key = ap.bssid,
            )
        }

private fun ChannelSpan.toOccupancySpan() = OccupancySpan(lowMhz = lowMhz, centerMhz = centerMhz, highMhz = highMhz)
