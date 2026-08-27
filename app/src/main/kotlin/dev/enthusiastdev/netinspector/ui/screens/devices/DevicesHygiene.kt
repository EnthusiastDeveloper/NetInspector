package dev.enthusiastdev.netinspector.ui.screens.devices

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.enthusiastdev.netinspector.core.designsystem.component.ScoreBadge
import dev.enthusiastdev.netinspector.core.designsystem.component.ScoreChip
import dev.enthusiastdev.netinspector.core.model.lan.HygieneFinding
import dev.enthusiastdev.netinspector.core.model.lan.HygieneScore
import dev.enthusiastdev.netinspector.core.model.lan.allFlaggedPorts
import dev.enthusiastdev.netinspector.core.model.lan.portRisk

/**
 * docs/ideas.md #1 - the network-wide read, compressed to a tap-through badge that
 * rides in [DevicesSummaryRow] next to the device count.
 *
 * It used to be a full card in a second column beside the list controls (score, rating, summary,
 * "tap for what to fix"). All of that detail now lives one tap away in
 * [NetworkHygieneDetailsDialog]; the badge itself carries just the coloured [ScoreChip], the
 * rating word and a chevron. The chevron plus the [Surface] `onClick` ripple and button role are
 * what mark it as tappable rather than a static readout.
 *
 * It is still scored over *every* discovered host, not just the ones the confidence filter is
 * currently showing (see [networkHygieneScore]) - network hygiene is a property of the network,
 * not of the current view of it.
 */
@Composable
internal fun HygieneBadge(
    score: HygieneScore,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description =
        "Network hygiene ${score.value}, ${score.rating.label()}. ${score.findingsSummary()}. Tap for details."
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.semantics { contentDescription = description },
    ) {
        Row(
            modifier = Modifier.heightIn(min = 36.dp).padding(start = 4.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ScoreChip(score = score.value)
            Text(text = score.rating.label(), style = MaterialTheme.typography.labelLarge)
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

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
