package dev.enthusiastdev.netinspector.core.designsystem.gauge

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

private const val START_ANGLE = 150f
private const val SWEEP_ANGLE = 240f

/**
 * Semicircular signal-strength gauge. `qualityPercent` (0..100, see `rssiToQualityPercent` in
 * `:core:common`) drives both the arc fill and its color; `rssiDbm` is the label - the gauge
 * never invents a value when the connection is unknown, that's the caller's job to gate.
 */
@Composable
fun RssiGauge(
    rssiDbm: Int,
    qualityPercent: Int,
    modifier: Modifier = Modifier,
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val fillColor =
        when {
            qualityPercent < 30 -> MaterialTheme.colorScheme.error
            qualityPercent < 60 -> Color(0xFFB8860B) // amber - distinct from the theme's error/primary hues
            else -> MaterialTheme.colorScheme.primary
        }
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Box(modifier = modifier.size(160.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(160.dp)) {
            val stroke = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
            val inset = stroke.width / 2
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)

            drawArc(
                color = trackColor,
                startAngle = START_ANGLE,
                sweepAngle = SWEEP_ANGLE,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke,
            )
            drawArc(
                color = fillColor,
                startAngle = START_ANGLE,
                sweepAngle = SWEEP_ANGLE * (qualityPercent / 100f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke,
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "$rssiDbm", style = MaterialTheme.typography.headlineMedium, color = onSurface)
            Text(text = "dBm", style = MaterialTheme.typography.labelMedium, color = onSurfaceVariant)
        }
    }
}
