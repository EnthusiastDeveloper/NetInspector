package dev.enthusiastdev.netinspector.ui.screens.devices

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.enthusiastdev.netinspector.core.designsystem.component.ScoreBadge
import dev.enthusiastdev.netinspector.core.designsystem.component.ScoreChip
import dev.enthusiastdev.netinspector.core.designsystem.component.scoreColor
import dev.enthusiastdev.netinspector.core.model.lan.HygieneFinding
import dev.enthusiastdev.netinspector.core.model.lan.HygieneScore
import dev.enthusiastdev.netinspector.core.model.lan.allFlaggedPorts
import dev.enthusiastdev.netinspector.core.model.lan.portRisk
import kotlin.math.roundToInt

/**
 * docs/ideas.md #1 - the network-wide read, compressed to a tap-through badge that
 * rides in [DevicesSummaryRow] next to the device count. The full score / rating / per-host
 * remediation detail lives one tap away in [NetworkHygieneDetailsDialog].
 *
 * Three visual states:
 * - **Checking** ([scanning] with no prior result): a neutral pill that breathes, so "ports not
 *   looked at yet" reads differently from "looked, found nothing" - previously both were blank.
 * - **Resolved**: a pill tinted by the score band (green / amber / red at 70 / 50), the number
 *   counting up to its value and the tint crossfading to match.
 * - **Just resolved** ([expanded], the ~1.8s after a scan finishes): the pill widens to show
 *   the rating and a one-line summary with a deeper wash and a slight pop, then folds back.
 *   [DevicesSummaryRow] owns that timer and hides the device count for its duration so the
 *   wider pill has room on a narrow window.
 *
 * A rescan keeps showing the previous resolved score (gently pulsing) and animates it to the
 * new value when the scan lands, so a newly opened risky port visibly drags the number down.
 *
 * Scored over *every* discovered host, not the confidence-filtered view (see
 * [networkHygieneScore]) - hygiene is a property of the network, not the current view of it.
 */
@Composable
internal fun HygieneBadge(
    score: HygieneScore?,
    scanning: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var lastResolved by remember { mutableStateOf(score.takeUnless { scanning }) }
    LaunchedEffect(scanning, score) {
        if (!scanning) lastResolved = score
    }

    val shown = if (scanning) lastResolved else score
    when {
        scanning && lastResolved == null -> CheckingHygieneBadge(modifier)
        shown != null -> ResolvedHygieneBadge(shown, scanning, expanded, onClick, modifier)
        else -> Unit
    }
}

@Composable
private fun CheckingHygieneBadge(modifier: Modifier = Modifier) {
    val breathe by
        rememberInfiniteTransition(label = "hygiene-checking").animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "hygiene-checking-alpha",
        )
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.semantics { contentDescription = "Checking network hygiene" },
    ) {
        Row(
            modifier = Modifier.heightIn(min = 36.dp).padding(horizontal = 10.dp).alpha(breathe),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                Icons.Filled.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Text(
                "Checking ports…",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ResolvedHygieneBadge(
    score: HygieneScore,
    scanning: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val counter = remember { Animatable(0f) }
    LaunchedEffect(score.value) {
        counter.animateTo(score.value.toFloat(), tween(600, easing = FastOutSlowInEasing))
    }
    val shownValue = counter.value.roundToInt()

    val band by animateColorAsState(scoreColor(shownValue), tween(500), label = "hygiene-band")
    val pop by animateFloatAsState(if (expanded) 1.04f else 1f, tween(400), label = "hygiene-pop")
    val pulse by
        rememberInfiniteTransition(label = "hygiene-pulse").animateFloat(
            initialValue = if (scanning) 0.6f else 1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(950), RepeatMode.Reverse),
            label = "hygiene-pulse-alpha",
        )
    val description =
        "Network hygiene ${score.value}, ${score.rating.label()}. ${score.findingsSummary()}. Tap for details."

    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = band.copy(alpha = if (expanded) 0.26f else 0.14f),
        modifier = modifier.scale(pop).semantics { contentDescription = description },
    ) {
        Row(
            modifier =
                Modifier
                    .heightIn(min = 36.dp)
                    .padding(start = 4.dp, end = 8.dp)
                    .alpha(pulse)
                    .animateContentSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ScoreChip(score = shownValue)
            Text(
                text =
                    if (expanded) {
                        "${score.rating.label()} · ${score.badgeSummary()}"
                    } else {
                        score.rating.label()
                    },
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** A shorter summary than [HygieneScore.findingsSummary] for the brief expanded badge, where
 * the row is competing with the device count and the scan button for width. */
private fun HygieneScore.badgeSummary(): String =
    if (findings.isEmpty()) "no risks found" else "${findings.size} to review"

/** The full checklist, on demand - the score, its summary and the per-host remediation rows
 * that the compact [HygieneBadge] leaves out. [onHostClick] (docs/ideas.md #3) lets
 * each remediation row jump straight to the host it's about, reusing the same host-address
 * navigation [DevicesScreen] already uses for the list/detail pane. The methodology explainer
 * (docs/ideas.md #2) hangs off the title here now that the badge has no room for its
 * own info button. */
@Composable
internal fun NetworkHygieneDetailsDialog(
    score: HygieneScore,
    onHostClick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Network hygiene")
                HygieneScoreInfoButton()
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ScoreBadge(score = score.value, label = score.rating.label())
                Text(text = score.findingsSummary(), style = MaterialTheme.typography.bodySmall)
                RemediationList(score.findings, onHostClick = onHostClick)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/** docs/ideas.md #2 - the score's methodology was otherwise opaque: a number and a
 * rating with no visible explanation of what's being measured or why. Same tap-to-expand
 * pattern as `DevicesDetailCards.kt`'s "why no MAC address?" dialog, for the same "explain a
 * non-obvious value rather than leave it unexplained" reason. Shared by [DevicesNetworkHygieneCard]
 * and `DevicesDetailHygieneScoreCard`, so the explanation can't drift between the two. */
@Composable
internal fun HygieneScoreInfoButton() {
    var showExplanation by remember { mutableStateOf(false) }
    IconButton(onClick = { showExplanation = true }) {
        Icon(Icons.Filled.Info, contentDescription = "How is this score calculated?")
    }
    if (showExplanation) {
        AlertDialog(
            onDismissRequest = { showExplanation = false },
            title = { Text("How the hygiene score works") },
            text = { HygieneMethodology() },
            confirmButton = { TextButton(onClick = { showExplanation = false }) { Text("Got it") } },
        )
    }
}

@Composable
private fun HygieneMethodology() {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Starts at 100 and loses points for open ports matching a fixed list of " +
                "well-known unencrypted or historically vulnerable protocols - nothing else " +
                "(firmware versions, password strength, anything not on this list) factors in.",
        )
        Text(
            "CRITICAL findings (-40 each) are protocols commonly reachable with no real " +
                "authentication barrier at all. HIGH (-20 each) expose credentials or traffic " +
                "in the clear, or have a known cryptographic break. MODERATE (-10 each) are " +
                "conditional on misconfiguration or on already-valid credentials. The network " +
                "score pools every host's open ports into one calculation rather than " +
                "averaging per-host scores.",
        )
        Text("Currently flagged:", style = MaterialTheme.typography.labelLarge)
        allFlaggedPorts().forEach { (port, risk) ->
            Text(
                "• $port (${risk.protocol}, ${risk.severity.name.lowercase()}) - ${risk.reason}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** docs/ideas.md #3 - turns a `HygieneScore`'s findings into a "what to fix"
 * checklist instead of leaving the user to infer action items from the score alone. When
 * [onHostClick] is given (the network-wide card), each row is prefixed with the offending
 * host's address and tappable through to that host's own detail pane, reusing the same
 * host-address-string navigation key [DevicesScreen] already keys its list/detail pane on. */
@Composable
internal fun RemediationList(
    findings: List<HygieneFinding>,
    onHostClick: ((String) -> Unit)? = null,
) {
    if (findings.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        findings.sortedBy { it.severity }.forEach { finding -> RemediationRow(finding, onHostClick) }
    }
}

@Composable
private fun RemediationRow(
    finding: HygieneFinding,
    onHostClick: ((String) -> Unit)?,
) {
    val risk = portRisk(finding.port) ?: return
    val hostAddress = finding.hostAddress
    val label =
        if (onHostClick != null && hostAddress != null) {
            "$hostAddress - ${risk.protocol} (${finding.port})"
        } else {
            "${risk.protocol} (${finding.port})"
        }
    val rowModifier =
        if (onHostClick != null && hostAddress != null) {
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onHostClick(hostAddress) }
        } else {
            Modifier.fillMaxWidth()
        }
    Column(modifier = rowModifier) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            risk.remediation,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
