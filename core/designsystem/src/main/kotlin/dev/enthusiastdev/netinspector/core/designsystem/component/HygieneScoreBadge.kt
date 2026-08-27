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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * docs/ideas.md #1 - a compact "is this okay?" readout: a colored number (expected
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
    val color = scoreColor(score)
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

/** The same number as [ScoreBadge] in a single small pill, for places where the 48dp badge and
 * its label don't fit - a collapsed toolbar, a dense row. Shares [scoreColor] so a score reads
 * the same wherever it appears. */
@Composable
fun ScoreChip(
    score: Int,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val color = scoreColor(score)
    Box(
        modifier =
            modifier
                .size(32.dp)
                .background(color.copy(alpha = 0.15f), CircleShape)
                .semantics { contentDescription?.let { description -> this.contentDescription = description } },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = score.toString(), style = MaterialTheme.typography.labelLarge, color = color)
    }
}

/**
 * The band colour for a 0..100 score - green at/above 70, amber 50..69, red below, the same
 * poor/fair/good split the RssiGauge uses. Public so callers that build their own score
 * treatment (an animated badge, a tinted container) read a score the same way [ScoreBadge] and
 * [ScoreChip] do rather than picking their own thresholds.
 */
@Composable
fun scoreColor(score: Int): Color =
    when {
        score >= GOOD_THRESHOLD -> MaterialTheme.colorScheme.primary
        score >= FAIR_THRESHOLD -> Color(0xFFB8860B) // amber - distinct from the theme's error/primary hues
        else -> MaterialTheme.colorScheme.error
    }

private const val GOOD_THRESHOLD = 70
private const val FAIR_THRESHOLD = 50
