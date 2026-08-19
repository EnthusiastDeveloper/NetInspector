package dev.enthusiastdev.netinspector.core.designsystem.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** design §7.1 - a bonded-width span within one AP's occupancy curve; two of these (with a
 * connector between them) represent an 80+80 MHz allocation. */
data class OccupancySpan(
    val lowMhz: Int,
    val centerMhz: Int,
    val highMhz: Int,
)

/** design §7.1 - one AP's curve. `colorSeed` picks a stable hue (callers derive it from
 * something identity-like, e.g. a BSSID hash) so the same AP keeps its color across
 * recompositions and scans without this module knowing what a BSSID is. `key` identifies the
 * curve for tap-to-highlight - kept separate from `label` since a display label (SSID) can
 * collide across BSSIDs sharing one network. */
data class OccupancyCurve(
    val primary: OccupancySpan,
    val secondary: OccupancySpan?,
    val rssiDbm: Int,
    val label: String,
    val colorSeed: Int,
    val key: String = label,
)

private const val MIN_TICK_SPACING_DP = 56f
private const val GRAPH_HEIGHT_DP = 220
private const val TOP_INSET_DP = 18f
private const val BOTTOM_INSET_DP = 28f

/**
 * design §7.1 - "the classic overlapping-parabola chart." Each curve is a true parabola
 * (not an approximated bell shape): it peaks at `rssiDbm` over `centerMhz` and returns exactly
 * to the axis floor at `lowMhz`/`highMhz`, so a wide (e.g. 160 MHz) AP visibly spans more of
 * the X axis than a narrow one - width is the whole point of this chart, not just RSSI.
 *
 * Drawn with `Canvas` rather than a charting library per design §7.1: the shape is unusual
 * enough that fighting a general-purpose chart API costs more than ~100 lines of direct
 * drawing.
 */
@Composable
fun ChannelOccupancyGraph(
    curves: List<OccupancyCurve>,
    axisLowMhz: Int,
    axisHighMhz: Int,
    modifier: Modifier = Modifier,
    highlightedKey: String? = null,
    onCurveTap: (String) -> Unit = {},
) {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = labelColor, fontSize = 10.sp)
    val labelStyles = LabelStyles(default = labelStyle, highlighted = labelStyle.copy(fontWeight = FontWeight.Bold))
    val minTickSpacingPx = with(LocalDensity.current) { MIN_TICK_SPACING_DP.dp.toPx() }

    Canvas(
        modifier =
            modifier.fillMaxWidth().height(GRAPH_HEIGHT_DP.dp).pointerInput(curves, axisLowMhz, axisHighMhz) {
                val topInsetPx = TOP_INSET_DP.dp.toPx()
                val bottomInsetPx = BOTTOM_INSET_DP.dp.toPx()
                detectTapGestures { offset ->
                    val mapper =
                        AxisMapper(
                            axisLowMhz = axisLowMhz,
                            axisHighMhz = axisHighMhz,
                            widthPx = size.width.toFloat(),
                            topInsetPx = topInsetPx,
                            bottomInsetPx = bottomInsetPx,
                            heightPx = size.height.toFloat(),
                        )
                    hitTestCurve(curves, offset, mapper)?.let(onCurveTap)
                }
            },
    ) {
        val mapper =
            AxisMapper(
                axisLowMhz = axisLowMhz,
                axisHighMhz = axisHighMhz,
                widthPx = size.width,
                topInsetPx = TOP_INSET_DP.dp.toPx(),
                bottomInsetPx = BOTTOM_INSET_DP.dp.toPx(),
                heightPx = size.height,
            )
        val paint = GraphPaint(gridColor, textMeasurer, labelStyles)
        drawOccupancyGraph(curves, highlightedKey, mapper, minTickSpacingPx, paint)
    }
}
