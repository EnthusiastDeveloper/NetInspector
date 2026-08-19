package dev.enthusiastdev.netinspector.core.designsystem.chart

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.roundToInt

private val NICE_STEPS_MHZ = listOf(5, 10, 20, 25, 40, 50, 80, 100, 160, 200, 250, 400, 500, 800, 1000)

/** Converts between MHz/dBm domain values and this graph's pixel space, shared by the canvas
 * draw pass and its tap-gesture pass - both need the exact same mapping, so it's computed once
 * per pass rather than duplicated. */
internal class AxisMapper(
    val axisLowMhz: Int,
    val axisHighMhz: Int,
    private val widthPx: Float,
    private val topInsetPx: Float,
    private val bottomInsetPx: Float,
    private val heightPx: Float,
) {
    private val axisSpanMhz = (axisHighMhz - axisLowMhz).coerceAtLeast(1)
    private val plotHeightPx = (heightPx - topInsetPx - bottomInsetPx).coerceAtLeast(1f)
    val topPx get() = topInsetPx
    val bottomPx get() = heightPx - bottomInsetPx

    fun xPx(mhz: Int): Float = (mhz - axisLowMhz).toFloat() / axisSpanMhz * widthPx

    fun yPx(dbm: Float): Float =
        topInsetPx + plotHeightPx * (1f - ((dbm - Y_MIN_DBM) / (Y_MAX_DBM - Y_MIN_DBM)).coerceIn(0f, 1f))
}

internal data class LabelStyles(
    val default: TextStyle,
    val highlighted: TextStyle,
)

internal class GraphPaint(
    val gridColor: Color,
    val textMeasurer: TextMeasurer,
    val labelStyles: LabelStyles,
)

internal fun DrawScope.drawRssiGridlines(
    valuesDbm: List<Float>,
    mapper: AxisMapper,
    gridColor: Color,
) {
    valuesDbm.forEach { dbm ->
        val y = mapper.yPx(dbm)
        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
    }
}

internal fun DrawScope.drawXAxisGridlines(
    ticksMhz: List<Int>,
    mapper: AxisMapper,
    gridColor: Color,
) {
    ticksMhz.forEach { mhz ->
        val x = mapper.xPx(mhz)
        drawLine(gridColor, Offset(x, mapper.topPx), Offset(x, mapper.bottomPx), strokeWidth = 1.dp.toPx())
    }
}

internal fun DrawScope.drawRssiAxisLabels(
    valuesDbm: List<Float>,
    mapper: AxisMapper,
    textMeasurer: TextMeasurer,
    style: TextStyle,
) {
    valuesDbm.forEach { dbm ->
        drawText(
            textMeasurer,
            "${dbm.roundToInt()}",
            topLeft = Offset(4.dp.toPx(), mapper.yPx(dbm) - 12.dp.toPx()),
            style = style,
        )
    }
}

internal fun DrawScope.drawXAxisLabels(
    ticksMhz: List<Int>,
    mapper: AxisMapper,
    textMeasurer: TextMeasurer,
    style: TextStyle,
) {
    ticksMhz.forEach { mhz ->
        val text = if (mhz % 1000 == 0) "${mhz / 1000}GHz" else "$mhz"
        val textWidth = textMeasurer.measure(text, style).size.width
        // Centering on the tick would push a label at either axis extreme past the canvas
        // edge, wrapping it onto a second line - clamp so it always stays fully on-canvas.
        val x = (mapper.xPx(mhz) - textWidth / 2f).coerceIn(0f, (size.width - textWidth).coerceAtLeast(0f))
        drawText(
            textMeasurer,
            text,
            topLeft = Offset(x, mapper.bottomPx + 4.dp.toPx()),
            style = style,
        )
    }
}

/** design §11.2 - picks the smallest "nice" MHz step (from [NICE_STEPS_MHZ]) that keeps
 * adjacent tick labels at least [minSpacingPx] apart given the axis's actual pixel width, then
 * generates ticks at that step across the axis range. */
internal fun xAxisTicksMhz(
    axisLowMhz: Int,
    axisHighMhz: Int,
    widthPx: Float,
    minSpacingPx: Float,
): List<Int> {
    val axisSpanMhz = (axisHighMhz - axisLowMhz).coerceAtLeast(1)
    val maxTicks = (widthPx / minSpacingPx).toInt().coerceAtLeast(2)
    val rawStepMhz = axisSpanMhz.toFloat() / maxTicks
    val stepMhz = NICE_STEPS_MHZ.firstOrNull { it >= rawStepMhz } ?: NICE_STEPS_MHZ.last()
    val firstTick = (ceil(axisLowMhz.toFloat() / stepMhz) * stepMhz).roundToInt()
    return generateSequence(firstTick) { it + stepMhz }.takeWhile { it <= axisHighMhz }.toList()
}
