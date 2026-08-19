package dev.enthusiastdev.netinspector.core.designsystem.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** design §7.1 - a bonded-width span within one AP's occupancy curve; two of these (with a
 * connector between them) represent an 80+80 MHz allocation. */
data class OccupancySpan(
    val lowMhz: Int,
    val centerMhz: Int,
    val highMhz: Int,
)

/** design §7.1 - one AP's curve. `colorSeed` picks a stable hue (callers derive it from
 * something identity-like, e.g. a BSSID hash) so the same AP keeps its color across
 * recompositions and scans without this module knowing what a BSSID is. */
data class OccupancyCurve(
    val primary: OccupancySpan,
    val secondary: OccupancySpan?,
    val rssiDbm: Int,
    val label: String,
    val colorSeed: Int,
)

private const val Y_MIN_DBM = -100f
private const val Y_MAX_DBM = -30f
private const val CURVE_SAMPLES = 24
private const val FILL_ALPHA = 0.28f
private const val STROKE_ALPHA = 0.9f

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
) {
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = onSurfaceVariant, fontSize = 10.sp)

    Canvas(modifier = modifier.fillMaxWidth().height(220.dp)) {
        val axisSpanMhz = (axisHighMhz - axisLowMhz).coerceAtLeast(1)
        // Reserve room top and bottom so a peak's label and the floor gridline's label both
        // have space to draw without being clipped by the canvas edge.
        val topInsetPx = 18.dp.toPx()
        val bottomInsetPx = 14.dp.toPx()
        val plotHeightPx = (size.height - topInsetPx - bottomInsetPx).coerceAtLeast(1f)

        fun xPx(mhz: Int): Float = (mhz - axisLowMhz).toFloat() / axisSpanMhz * size.width

        fun yPx(dbm: Float): Float =
            topInsetPx + plotHeightPx * (1f - ((dbm - Y_MIN_DBM) / (Y_MAX_DBM - Y_MIN_DBM)).coerceIn(0f, 1f))

        // RSSI gridlines every 20 dBm - just the lines here; their labels are drawn last (see
        // below) so a curve's translucent fill never paints over the axis text.
        val gridValuesDbm = generateSequence(Y_MIN_DBM) { it + 20f }.takeWhile { it <= Y_MAX_DBM }.toList()
        gridValuesDbm.forEach { dbm ->
            val y = yPx(dbm)
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }

        curves.forEach { curve ->
            val color = curveColor(curve.colorSeed)
            drawParabola(curve.primary, curve.rssiDbm, color, ::xPx, ::yPx)
            val secondary = curve.secondary
            if (secondary != null) {
                drawParabola(secondary, curve.rssiDbm, color, ::xPx, ::yPx)
                drawLine(
                    color = color.copy(alpha = STROKE_ALPHA),
                    start = Offset(xPx(curve.primary.centerMhz), yPx(curve.rssiDbm.toFloat())),
                    end = Offset(xPx(secondary.centerMhz), yPx(curve.rssiDbm.toFloat())),
                    strokeWidth = 1.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }

        gridValuesDbm.forEach { dbm ->
            drawText(
                textMeasurer,
                "${dbm.roundToInt()}",
                topLeft = Offset(4.dp.toPx(), yPx(dbm) - 12.dp.toPx()),
                style = labelStyle,
            )
        }

        drawCurveLabels(curves, textMeasurer, labelStyle, ::xPx, ::yPx)
    }
}

/** Strongest signal first: a curve's own outline is always drawn (above), but its label is
 * skipped if it would land on top of an already-placed one. Cramming every SSID's text into
 * a dense cluster (common - several VLANs off one radio) is less useful than a legible label
 * on the APs that actually dominate that spot. */
private fun DrawScope.drawCurveLabels(
    curves: List<OccupancyCurve>,
    textMeasurer: TextMeasurer,
    labelStyle: TextStyle,
    xPx: (Int) -> Float,
    yPx: (Float) -> Float,
) {
    val placedLabelRanges = mutableListOf<ClosedFloatingPointRange<Float>>()
    curves.sortedByDescending { it.rssiDbm }.forEach { curve ->
        val labelSpan = curve.secondary ?: curve.primary
        val x = xPx(labelSpan.centerMhz) + 4.dp.toPx()
        val labelWidthPx =
            textMeasurer
                .measure(curve.label, labelStyle)
                .size.width
                .toFloat()
        val range = x..(x + labelWidthPx)
        val collides = placedLabelRanges.any { it.start < range.endInclusive && range.start < it.endInclusive }
        if (!collides) {
            drawText(
                textMeasurer,
                curve.label,
                topLeft = Offset(x, yPx(curve.rssiDbm.toFloat()) - 14.dp.toPx()),
                style = labelStyle,
            )
            placedLabelRanges += range
        }
    }
}

private fun DrawScope.drawParabola(
    span: OccupancySpan,
    rssiDbm: Int,
    color: Color,
    xPx: (Int) -> Float,
    yPx: (Float) -> Float,
) {
    val halfWidth = ((span.highMhz - span.lowMhz) / 2f).coerceAtLeast(1f)
    val baselineY = yPx(Y_MIN_DBM)

    val path =
        Path().apply {
            moveTo(xPx(span.lowMhz), baselineY)
            for (sample in 0..CURVE_SAMPLES) {
                val mhz = span.lowMhz + (span.highMhz - span.lowMhz) * (sample.toFloat() / CURVE_SAMPLES)
                val t = (mhz - span.centerMhz) / halfWidth
                val dbm = rssiDbm - (rssiDbm - Y_MIN_DBM) * (t * t)
                lineTo(xPx(mhz.roundToInt()), yPx(dbm))
            }
            lineTo(xPx(span.highMhz), baselineY)
            close()
        }

    drawPath(path, color = color.copy(alpha = FILL_ALPHA))
    drawPath(path, color = color.copy(alpha = STROKE_ALPHA), style = Stroke(width = 1.5.dp.toPx()))
}

/** A stable, reasonably-distinct hue per curve - not tied to any particular AP identity type
 * so this module stays free of domain imports; callers pick the seed (e.g. `bssid.hashCode()`). */
private fun curveColor(seed: Int): Color {
    val hue = ((seed % 360) + 360) % 360
    return Color.hsv(hue.toFloat(), 0.65f, 0.85f)
}
