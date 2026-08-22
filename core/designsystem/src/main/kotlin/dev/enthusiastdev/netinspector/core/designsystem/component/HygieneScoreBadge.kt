package dev.enthusiastdev.netinspector.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * docs/improvement-ideas.md #1 - a compact "is this okay?" readout: a colored number (expected
 * 0..100, but not clamped here) plus a short label describing what it means. Like [InfoRow], it
 * only renders what it's given - [score]'s scale and [label]'s wording are entirely the
 * caller's concern, so this stays reusable for any 0..100 score, not just the hygiene one.
 * The fill color bands at a 70/50 split, the same poor/fair/good pattern the RssiGauge in the
 * `gauge` package uses, so a score reads at a glance without parsing [label].
 */
@Composable
fun ScoreBadge(
    score: Int,
    label: String,
    modifier: Modifier = Modifier,
) {
    val color =
        when {
            score >= GOOD_THRESHOLD -> MaterialTheme.colorScheme.primary
            score >= FAIR_THRESHOLD -> Color(0xFFB8860B) // amber - distinct from the theme's error/primary hues
            else -> MaterialTheme.colorScheme.error
        }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(48.dp).background(color.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = score.toString(), style = MaterialTheme.typography.titleMedium, color = color)
        }
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

private const val GOOD_THRESHOLD = 70
private const val FAIR_THRESHOLD = 50
