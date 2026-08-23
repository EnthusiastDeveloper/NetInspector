package dev.enthusiastdev.netinspector.core.designsystem.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
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

/** design §7.1 - one AP's curve. `colorSeed` picks a palette slot (callers derive it from
 * something identity-like, e.g. a BSSID hash) so the same AP tends to keep its color across
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
 *
 * Pinch to zoom the frequency axis and drag to pan; pinching back out returns to the full band.
 * On 2.4 GHz especially, a dozen APs stacked into 100 MHz is unreadable at full-band scale and no
 * amount of layout tuning fixes that - being able to pull the axis apart does. See [AxisViewport]
 * for why this remaps the axis instead of scaling the canvas.
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
    val curveColors = rememberCurveColors(curves)

    // Reset when the band changes - a 5 GHz viewport means nothing on the 2.4 GHz axis.
    var viewport by remember(axisLowMhz, axisHighMhz) { mutableStateOf(AxisViewport()) }
    val visibleLowMhz = viewport.lowMhz(axisLowMhz, axisHighMhz)
    val visibleHighMhz = viewport.highMhz(axisLowMhz, axisHighMhz)

    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .height(GRAPH_HEIGHT_DP.dp)
                // Curves outside the zoomed-in range are still drawn; without this they spill
                // over whatever the graph is sitting next to.
                .clipToBounds()
                .curveTapGestures(curves, visibleLowMhz, visibleHighMhz, onCurveTap)
                .axisZoomGestures(axisLowMhz, axisHighMhz, { viewport }, { viewport = it })
                // The tap-to-highlight interaction has no TalkBack equivalent (a raw gesture
                // detector, not a `clickable`) - out of scope to build one here (it'd need
                // per-curve focusable nodes). This at least gives every curve's data - the
                // information the highlight interaction can only ever narrow down to one curve
                // at a time anyway - a single accessible summary of the whole chart.
                .clearAndSetSemantics { contentDescription = describeCurves(curves) },
    ) {
        val mapper =
            AxisMapper(
                axisLowMhz = visibleLowMhz,
                axisHighMhz = visibleHighMhz,
                widthPx = size.width,
                topInsetPx = TOP_INSET_DP.dp.toPx(),
                bottomInsetPx = BOTTOM_INSET_DP.dp.toPx(),
                heightPx = size.height,
            )
        val paint = GraphPaint(gridColor, textMeasurer, labelStyles, curveColors)
        drawOccupancyGraph(curves, highlightedKey, mapper, minTickSpacingPx, paint)
    }
}

/**
 * Tap-to-highlight. Deliberately no `onDoubleTap`: registering one makes every *single* tap wait
 * out the double-tap timeout before it fires, and this is the interaction that has to feel
 * immediate. Pinching back out restores the full-band view exactly (the zoom floor is 1x and the
 * center re-clamps to the middle), so there is nothing a reset gesture would add.
 */
private fun Modifier.curveTapGestures(
    curves: List<OccupancyCurve>,
    visibleLowMhz: Int,
    visibleHighMhz: Int,
    onCurveTap: (String) -> Unit,
): Modifier =
    pointerInput(curves, visibleLowMhz, visibleHighMhz) {
        val topInsetPx = TOP_INSET_DP.dp.toPx()
        val bottomInsetPx = BOTTOM_INSET_DP.dp.toPx()
        detectTapGestures { offset ->
            val mapper =
                AxisMapper(
                    axisLowMhz = visibleLowMhz,
                    axisHighMhz = visibleHighMhz,
                    widthPx = size.width.toFloat(),
                    topInsetPx = topInsetPx,
                    bottomInsetPx = bottomInsetPx,
                    heightPx = size.height.toFloat(),
                )
            hitTestCurve(curves, offset, mapper)?.let(onCurveTap)
        }
    }

/**
 * Pinch to zoom the frequency axis, drag to pan along it.
 *
 * [viewport] is read through a lambda rather than passed by value: this modifier is keyed on the
 * band alone, so the gesture coroutine survives across zoom steps and must see the current
 * viewport each time it fires rather than the one captured when the gesture began.
 */
private fun Modifier.axisZoomGestures(
    axisLowMhz: Int,
    axisHighMhz: Int,
    viewport: () -> AxisViewport,
    onViewportChange: (AxisViewport) -> Unit,
): Modifier =
    pointerInput(axisLowMhz, axisHighMhz) {
        detectTransformGestures { centroid, pan, gestureZoom, _ ->
            val widthPx = size.width.toFloat().coerceAtLeast(1f)
            onViewportChange(
                viewport().transformedBy(
                    fullLowMhz = axisLowMhz,
                    fullHighMhz = axisHighMhz,
                    focusFraction = centroid.x / widthPx,
                    zoomFactor = gestureZoom,
                    panFraction = pan.x / widthPx,
                ),
            )
        }
    }

/** One color per curve key, de-collided across the curves currently on screen (see
 * [assignPaletteSlots]) and picked from whichever palette suits the current theme. */
@Composable
private fun rememberCurveColors(curves: List<OccupancyCurve>): Map<String, Color> {
    val palette =
        if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) CURVE_PALETTE_DARK else CURVE_PALETTE_LIGHT
    return remember(curves, palette) {
        val slots = assignPaletteSlots(curves.map { it.colorSeed }, palette.size)
        curves.mapIndexed { index, curve -> curve.key to palette[slots[index]] }.toMap()
    }
}

private fun describeCurves(curves: List<OccupancyCurve>): String {
    if (curves.isEmpty()) return "Channel occupancy graph, no networks on this band"
    val entries =
        curves.joinToString("; ") { curve ->
            "${curve.label}, centered at ${curve.primary.centerMhz} megahertz, ${curve.rssiDbm} dBm"
        }
    return "Channel occupancy graph, ${curves.size} networks: $entries"
}
