package dev.enthusiastdev.netinspector.ui.screens.devices

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.enthusiastdev.netinspector.core.designsystem.component.InfoCard
import dev.enthusiastdev.netinspector.core.designsystem.component.ScoreBadge
import dev.enthusiastdev.netinspector.core.model.lan.Host
import dev.enthusiastdev.netinspector.core.model.lan.HygieneFinding
import dev.enthusiastdev.netinspector.core.model.lan.allFlaggedPorts
import dev.enthusiastdev.netinspector.core.model.lan.networkHygieneScore
import dev.enthusiastdev.netinspector.core.model.lan.portRisk

/** docs/improvement-ideas.md #1 - the network-wide read above the host list, aggregated over
 * exactly the hosts currently visible in [hosts] (already confidence-filtered by the caller -
 * design §Phase 8's "$hostCount devices" header count above this card reflects the same
 * filtered list, so this stays consistent with it rather than silently scoring a different
 * set). [onHostClick] (docs/improvement-ideas.md #3) lets each remediation row jump straight to
 * the host it's about, reusing the same host-address navigation [DevicesScreen] already uses
 * for the list/detail pane. */
@Composable
internal fun DevicesNetworkHygieneCard(
    hosts: List<Host>,
    onHostClick: (String) -> Unit,
) {
    val score = networkHygieneScore(hosts)
    InfoCard(title = "Network hygiene", trailingContent = { HygieneScoreInfoButton() }) {
        ScoreBadge(score = score.value, label = score.rating.label())
        Text(
            text = score.findingsSummary(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        RemediationList(score.findings, onHostClick = onHostClick)
    }
}

/** docs/improvement-ideas.md #2 - the score's methodology was otherwise opaque: a number and a
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
            text = {
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
            },
            confirmButton = { TextButton(onClick = { showExplanation = false }) { Text("Got it") } },
        )
    }
}

/** docs/improvement-ideas.md #3 - turns a `HygieneScore`'s findings into a "what to fix"
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
