package dev.enthusiastdev.netinspector.core.designsystem.chart

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.abs
import kotlin.math.roundToInt

internal fun DrawScope.drawOccupancyGraph(
    curves: List<OccupancyCurve>,
    highlightedKey: String?,
    mapper: AxisMapper,
    minTickSpacingPx: Float,
    paint: GraphPaint,
) {
    // RSSI gridlines every 20 dBm, and X ticks whose spacing derives from the measured canvas
    // width (design §11.2) - both drawn before the curves so a curve's translucent fill never
    // paints over a gridline, and their labels are drawn last so they never sit under a curve.
    val gridValuesDbm = generateSequence(Y_MIN_DBM) { it + 20f }.takeWhile { it <= Y_MAX_DBM }.toList()
    val xTicksMhz = xAxisTicksMhz(mapper.axisLowMhz, mapper.axisHighMhz, size.width, minTickSpacingPx)
    drawRssiGridlines(gridValuesDbm, mapper, paint.gridColor)
    drawXAxisGridlines(xTicksMhz, mapper, paint.gridColor)
    drawCurves(curves, highlightedKey, mapper)
    drawRssiAxisLabels(gridValuesDbm, mapper, paint.textMeasurer, paint.labelStyles.default)
    drawXAxisLabels(xTicksMhz, mapper, paint.textMeasurer, paint.labelStyles.default)
    drawCurveLabels(curves, highlightedKey, paint.textMeasurer, paint.labelStyles, mapper)
}

/** One (curve, span) pairing to hit-test against - an 80+80 MHz curve contributes two of
 * these, both carrying the same key and RSSI as their parent curve. */
private data class SpanRef(
    val key: String,
    val span: OccupancySpan,
    val rssiDbm: Int,
)

/** Hit-tests a tap against every curve's outline (primary and secondary spans), picking
 * whichever curve's drawn edge at that X is closest to the tapped Y - i.e. whichever curve
 * visually sits under the finger. */
internal fun hitTestCurve(
    curves: List<OccupancyCurve>,
    offset: Offset,
    mapper: AxisMapper,
): String? =
    curves
        .flatMap { curve -> curve.spanRefs() }
        .mapNotNull { it.distanceToOrNull(offset, mapper) }
        .minByOrNull { it.second }
        ?.first

private fun OccupancyCurve.spanRefs(): List<SpanRef> =
    listOfNotNull(primary, secondary).map { SpanRef(key, it, rssiDbm) }

private fun SpanRef.distanceToOrNull(
    offset: Offset,
    mapper: AxisMapper,
): Pair<String, Float>? {
    val mhz = axisXToMhz(offset.x, span, mapper)
    if (mhz !in span.lowMhz..span.highMhz) return null
    val curveY = mapper.yPx(dbmAt(span, rssiDbm, mhz))
    return key to abs(curveY - offset.y)
}

private fun axisXToMhz(
    xOffset: Float,
    span: OccupancySpan,
    mapper: AxisMapper,
): Int {
    // Linear-interpolate MHz from pixel X using two known reference points on the same axis.
    val lowPx = mapper.xPx(span.lowMhz)
    val highPx = mapper.xPx(span.highMhz)
    if (highPx == lowPx) return span.lowMhz
    val t = (xOffset - lowPx) / (highPx - lowPx)
    return (span.lowMhz + t * (span.highMhz - span.lowMhz)).roundToInt()
}

/** The same parabola formula the curve drawing uses, evaluated at one MHz - shared so hit
 * testing tracks the curve's actual drawn shape rather than an approximation of it. */
private fun dbmAt(
    span: OccupancySpan,
    rssiDbm: Int,
    mhz: Int,
): Float {
    val halfWidth = ((span.highMhz - span.lowMhz) / 2f).coerceAtLeast(1f)
    val t = (mhz - span.centerMhz) / halfWidth
    return rssiDbm - (rssiDbm - Y_MIN_DBM) * (t * t)
}
