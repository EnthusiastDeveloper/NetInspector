package dev.enthusiastdev.netinspector.core.designsystem.chart

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

// Shared with the axis/hit-testing files: the RSSI domain both the curves and the Y axis are
// drawn against, and the small visual-styling constants that make a highlighted curve read as
// selected.
internal const val Y_MIN_DBM = -100f
internal const val Y_MAX_DBM = -30f
private const val CURVE_SAMPLES = 24
private const val FILL_ALPHA = 0.28f
private const val STROKE_ALPHA = 0.9f
private const val DIMMED_ALPHA_FACTOR = 0.35f
private const val DEFAULT_STROKE_WIDTH_DP = 1.5f
private const val HIGHLIGHT_STROKE_WIDTH_DP = 3f

/** Only reachable if a curve list and its resolved color map ever disagree, which they can't as
 * long as both are derived from the same list - a neutral grey beats a crash if that changes. */
private val FALLBACK_CURVE_COLOR = Color(0xFF8A8A8A)

private data class CurveStyle(
    val fillAlpha: Float,
    val strokeAlpha: Float,
    val strokeWidth: Float,
)

internal fun DrawScope.drawCurves(
    curves: List<OccupancyCurve>,
    highlightedKey: String?,
    mapper: AxisMapper,
    curveColors: Map<String, Color>,
) {
    curves.forEach { curve -> drawCurve(curve, highlightedKey, mapper, curveColors[curve.key] ?: FALLBACK_CURVE_COLOR) }
}

private fun DrawScope.drawCurve(
    curve: OccupancyCurve,
    highlightedKey: String?,
    mapper: AxisMapper,
    color: Color,
) {
    val isDimmed = highlightedKey != null && curve.key != highlightedKey
    val isHighlighted = curve.key == highlightedKey
    val style =
        CurveStyle(
            fillAlpha = if (isDimmed) FILL_ALPHA * DIMMED_ALPHA_FACTOR else FILL_ALPHA,
            strokeAlpha = if (isDimmed) STROKE_ALPHA * DIMMED_ALPHA_FACTOR else STROKE_ALPHA,
            strokeWidth = if (isHighlighted) HIGHLIGHT_STROKE_WIDTH_DP.dp.toPx() else DEFAULT_STROKE_WIDTH_DP.dp.toPx(),
        )
    drawParabola(curve.primary, curve.rssiDbm, color, style, mapper)
    val secondary = curve.secondary
    if (secondary != null) {
        drawParabola(secondary, curve.rssiDbm, color, style, mapper)
        drawLine(
            color = color.copy(alpha = style.strokeAlpha),
            start = Offset(mapper.xPx(curve.primary.centerMhz), mapper.yPx(curve.rssiDbm.toFloat())),
            end = Offset(mapper.xPx(secondary.centerMhz), mapper.yPx(curve.rssiDbm.toFloat())),
            strokeWidth = 1.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawParabola(
    span: OccupancySpan,
    rssiDbm: Int,
    color: Color,
    style: CurveStyle,
    mapper: AxisMapper,
) {
    val halfWidth = ((span.highMhz - span.lowMhz) / 2f).coerceAtLeast(1f)
    val baselineY = mapper.yPx(Y_MIN_DBM)

    val path =
        Path().apply {
            moveTo(mapper.xPx(span.lowMhz), baselineY)
            for (sample in 0..CURVE_SAMPLES) {
                val mhz = span.lowMhz + (span.highMhz - span.lowMhz) * (sample.toFloat() / CURVE_SAMPLES)
                val t = (mhz - span.centerMhz) / halfWidth
                val dbm = rssiDbm - (rssiDbm - Y_MIN_DBM) * (t * t)
                lineTo(mapper.xPx(mhz.roundToInt()), mapper.yPx(dbm))
            }
            lineTo(mapper.xPx(span.highMhz), baselineY)
            close()
        }

    drawPath(path, color = color.copy(alpha = style.fillAlpha))
    drawPath(path, color = color.copy(alpha = style.strokeAlpha), style = Stroke(width = style.strokeWidth))
}

/** Strongest signal first: a curve's own outline is always drawn (above), but its label is
 * skipped if it would land on top of an already-placed one. Cramming every SSID's text into
 * a dense cluster (common - several VLANs off one radio) is less useful than a legible label
 * on the APs that actually dominate that spot. */
internal fun DrawScope.drawCurveLabels(
    curves: List<OccupancyCurve>,
    highlightedKey: String?,
    textMeasurer: TextMeasurer,
    labelStyles: LabelStyles,
    mapper: AxisMapper,
) {
    val placedLabelRanges = mutableListOf<ClosedFloatingPointRange<Float>>()
    curves.sortedByDescending { it.rssiDbm }.forEach { curve ->
        val style = if (curve.key == highlightedKey) labelStyles.highlighted else labelStyles.default
        val labelSpan = curve.secondary ?: curve.primary
        val x = mapper.xPx(labelSpan.centerMhz) + 4.dp.toPx()
        val labelWidthPx =
            textMeasurer
                .measure(curve.label, style)
                .size.width
                .toFloat()
        val range = x..(x + labelWidthPx)
        val collides = placedLabelRanges.any { it.start < range.endInclusive && range.start < it.endInclusive }
        // A highlighted curve's label is always worth showing, even over another's spot.
        if (!collides || curve.key == highlightedKey) {
            drawText(
                textMeasurer,
                curve.label,
                topLeft = Offset(x, mapper.yPx(curve.rssiDbm.toFloat()) - 14.dp.toPx()),
                style = style,
            )
            placedLabelRanges += range
        }
    }
}
