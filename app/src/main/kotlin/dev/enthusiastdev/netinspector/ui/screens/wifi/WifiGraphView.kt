package dev.enthusiastdev.netinspector.ui.screens.wifi

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.enthusiastdev.netinspector.core.designsystem.chart.ChannelOccupancyGraph
import dev.enthusiastdev.netinspector.core.designsystem.chart.OccupancyCurve
import dev.enthusiastdev.netinspector.core.designsystem.chart.OccupancySpan
import dev.enthusiastdev.netinspector.core.model.wifi.AccessPoint
import dev.enthusiastdev.netinspector.core.model.wifi.Band
import dev.enthusiastdev.netinspector.core.model.wifi.ChannelSpan
import dev.enthusiastdev.netinspector.ui.screens.connection.label

private val BAND_TABS = listOf(Band.GHZ_2_4, Band.GHZ_5, Band.GHZ_6)

/** design §7.1 - one band per tab, frequency-based occupancy graph for whichever is selected. */
@Composable
internal fun WifiGraphView(
    accessPoints: List<AccessPoint>,
    modifier: Modifier = Modifier,
) {
    var selectedBand by remember { mutableStateOf(Band.GHZ_5) }
    val selectedIndex = BAND_TABS.indexOf(selectedBand)

    Column(modifier = modifier.fillMaxWidth()) {
        TabRow(selectedTabIndex = selectedIndex) {
            BAND_TABS.forEach { band ->
                Tab(
                    selected = band == selectedBand,
                    onClick = { selectedBand = band },
                    text = { Text(band.label()) },
                )
            }
        }

        val curves = remember(accessPoints, selectedBand) { accessPoints.toOccupancyCurves(selectedBand) }
        val axisRange = selectedBand.axisRangeMhz()

        if (curves.isEmpty()) {
            Text(
                "No networks visible on this band",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            ChannelOccupancyGraph(
                curves = curves,
                axisLowMhz = axisRange.first,
                axisHighMhz = axisRange.last,
                modifier = Modifier.padding(16.dp),
            )
        }

        Text(
            "Curve width shows channel width; height shows signal strength.",
            modifier = Modifier.padding(horizontal = 16.dp).align(Alignment.Start),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Matches the ranges `bandOf()` classifies (core:model) - kept local since that function
 * classifies a frequency, it doesn't expose the band's own bounds. */
private fun Band.axisRangeMhz(): IntRange =
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
            )
        }

private fun ChannelSpan.toOccupancySpan() = OccupancySpan(lowMhz = lowMhz, centerMhz = centerMhz, highMhz = highMhz)
