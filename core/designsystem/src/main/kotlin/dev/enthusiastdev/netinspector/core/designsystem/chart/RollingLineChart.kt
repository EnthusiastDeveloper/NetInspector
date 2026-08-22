package dev.enthusiastdev.netinspector.core.designsystem.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp

/**
 * design §9.6 - the signal meter's "rolling 60-second chart," generalised: a simple polyline
 * over whatever samples the caller kept in its own rolling window (design leaves the sampling
 * cadence to the underlying stream - a `NetworkCallback`-driven RSSI flow doesn't emit on a
 * fixed timer, so the window is time-bounded by the caller, not sample-count-bounded here).
 *
 * [contentDescription] is required rather than defaulted/generated here: this chart has no
 * built-in notion of units (dBm, percent, ...) or what it's a trend *of* - only the caller
 * knows that, so a generic "line chart" fallback would tell a TalkBack user nothing a sighted
 * user doesn't already get from the surrounding screen title. [valueLabel] is the same idea for
 * sighted users: three reference labels (max/mid/min) are drawn against light gridlines so a
 * raw polyline has a scale to read, formatted however the caller's unit needs (defaults to a
 * bare rounded number).
 */
@Composable
fun RollingLineChart(
    samples: List<Float>,
    minValue: Float,
    maxValue: Float,
    contentDescription: String,
    modifier: Modifier = Modifier,
    valueLabel: (Float) -> String = { it.toInt().toString() },
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val gridColor = MaterialTheme.colorScheme.outline
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val textMeasurer = rememberTextMeasurer()

    Canvas(
        modifier =
            modifier.fillMaxWidth().height(160.dp).clearAndSetSemantics {
                this.contentDescription = contentDescription
            },
    ) {
        drawRect(color = trackColor)

        // Reference lines + labels at max/mid/min so a raw polyline has a scale to read against
        // (issue reported on-device: spikes with no indication of what they measured). Drawn
        // before the sample-count guard below so the axis is still visible while a chart is
        // still collecting its first couple of samples.
        listOf(maxValue to 0f, (minValue + maxValue) / 2f to size.height / 2f, minValue to size.height)
            .forEach { (value, y) ->
                drawLine(
                    color = gridColor.copy(alpha = 0.3f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                )
                val label = textMeasurer.measure(valueLabel(value), labelStyle)
                // maxOf(0f, ...) rather than a bare coerceIn upper bound: at a large enough
                // accessibility font scale the measured label can be taller than this chart's
                // fixed height, which would otherwise make the upper bound negative and
                // coerceIn throw (its bounds must satisfy minimum <= maximum).
                val labelY = (y - label.size.height / 2f).coerceIn(0f, maxOf(0f, size.height - label.size.height))
                drawText(textLayoutResult = label, topLeft = Offset(4.dp.toPx(), labelY))
            }

        if (samples.size < 2) return@Canvas

        val range = (maxValue - minValue).coerceAtLeast(1f)
        val stepX = size.width / (samples.size - 1)
        val path =
            Path().apply {
                samples.forEachIndexed { index, value ->
                    val x = index * stepX
                    val normalized = ((value - minValue) / range).coerceIn(0f, 1f)
                    val y = size.height * (1f - normalized)
                    if (index == 0) moveTo(x, y) else lineTo(x, y)
                }
            }
        drawPath(path, color = lineColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        val lastX = (samples.size - 1) * stepX
        val lastNormalized = ((samples.last() - minValue) / range).coerceIn(0f, 1f)
        drawCircle(color = lineColor, radius = 5.dp.toPx(), center = Offset(lastX, size.height * (1f - lastNormalized)))
    }
}
