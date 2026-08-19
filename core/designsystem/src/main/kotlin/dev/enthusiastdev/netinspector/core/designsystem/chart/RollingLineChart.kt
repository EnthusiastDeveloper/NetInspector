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
import androidx.compose.ui.unit.dp

/**
 * design §9.6 - the signal meter's "rolling 60-second chart," generalised: a simple polyline
 * over whatever samples the caller kept in its own rolling window (design leaves the sampling
 * cadence to the underlying stream - a `NetworkCallback`-driven RSSI flow doesn't emit on a
 * fixed timer, so the window is time-bounded by the caller, not sample-count-bounded here).
 */
@Composable
fun RollingLineChart(
    samples: List<Float>,
    minValue: Float,
    maxValue: Float,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Canvas(modifier = modifier.fillMaxWidth().height(160.dp)) {
        drawRect(color = trackColor)
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
