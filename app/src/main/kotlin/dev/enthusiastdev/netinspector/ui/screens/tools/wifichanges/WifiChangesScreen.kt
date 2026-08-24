package dev.enthusiastdev.netinspector.ui.screens.tools.wifichanges

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.enthusiastdev.netinspector.core.designsystem.component.InfoCard
import dev.enthusiastdev.netinspector.core.model.wifi.ApChange
import dev.enthusiastdev.netinspector.core.model.wifi.Band
import dev.enthusiastdev.netinspector.core.model.wifi.ObservedAp
import dev.enthusiastdev.netinspector.core.model.wifi.ScanSessionDiff
import dev.enthusiastdev.netinspector.core.model.wifi.SecurityType
import dev.enthusiastdev.netinspector.core.model.wifi.WifiStandard
import dev.enthusiastdev.netinspector.data.persistence.scan.ScanSessionEntity
import dev.enthusiastdev.netinspector.ui.screens.connection.label
import dev.enthusiastdev.netinspector.ui.screens.tools.history.asRelativeTime
import dev.enthusiastdev.netinspector.ui.screens.wifi.label

@Composable
fun WifiChangesRoute(
    modifier: Modifier = Modifier,
    viewModel: WifiChangesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    WifiChangesScreen(uiState, viewModel::selectBefore, viewModel::selectAfter, modifier)
}

private enum class PickerSlot { BEFORE, AFTER }

/** improvement-ideas.md #6 - one screen, not a picker destination plus a results destination:
 * both session picks are local state here, same reasoning `DevicesScreen`'s in-screen
 * navigator state uses instead of a second nav route for what's really one flow. */
@Composable
fun WifiChangesScreen(
    uiState: WifiChangesUiState,
    onSelectBefore: (ScanSessionEntity) -> Unit,
    onSelectAfter: (ScanSessionEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickerSlot by remember { mutableStateOf<PickerSlot?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "Pick two Wi-Fi scans to see what changed between them.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        item {
            SessionPickerRow("Before", uiState.before, onClick = { pickerSlot = PickerSlot.BEFORE })
        }
        item {
            SessionPickerRow("After", uiState.after, onClick = { pickerSlot = PickerSlot.AFTER })
        }
        uiState.diff?.let { diff -> diffSections(diff) }
    }

    pickerSlot?.let { slot ->
        SessionPickerDialog(
            sessions = uiState.recentSessions,
            onPick = { session ->
                when (slot) {
                    PickerSlot.BEFORE -> onSelectBefore(session)
                    PickerSlot.AFTER -> onSelectAfter(session)
                }
                pickerSlot = null
            },
            onDismiss = { pickerSlot = null },
        )
    }
}

/** design C-02 - every session row surfaces its own age rather than implying either side is
 * "current"; a scan session is a discrete snapshot in time, not a live re-scan. */
@Composable
private fun SessionPickerRow(
    label: String,
    session: ScanSessionEntity?,
    onClick: () -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(
                text = session?.let { it.timestampMillis.asRelativeTime() } ?: "Tap to choose a scan",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SessionPickerDialog(
    sessions: List<ScanSessionEntity>,
    onPick: (ScanSessionEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose a scan") },
        text = {
            if (sessions.isEmpty()) {
                Text("No scan history yet - visit the Wi-Fi tab to start collecting it.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(sessions, key = { it.id }) { session ->
                        Text(
                            text = session.timestampMillis.asRelativeTime(),
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(session) }
                                    .padding(vertical = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun LazyListScope.diffSections(diff: ScanSessionDiff) {
    val hasChanges = diff.added.isNotEmpty() || diff.removed.isNotEmpty() || diff.changed.isNotEmpty()
    if (!hasChanges) {
        item {
            Text(
                "No notable differences between these two scans" +
                    if (diff.unchangedCount > 0) " (${diff.unchangedCount} networks unchanged)." else ".",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }
    if (diff.added.isNotEmpty()) {
        item {
            InfoCard(title = "Added (${diff.added.size})") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    diff.added.forEach { ApSummaryRow(it) }
                }
            }
        }
    }
    if (diff.removed.isNotEmpty()) {
        item {
            InfoCard(title = "Removed (${diff.removed.size})") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    diff.removed.forEach { ApSummaryRow(it) }
                }
            }
        }
    }
    if (diff.changed.isNotEmpty()) {
        item {
            InfoCard(title = "Changed (${diff.changed.size})") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    diff.changed.forEach { ApChangeRow(it) }
                }
            }
        }
    }
    item {
        Text(
            "${diff.unchangedCount} network${if (diff.unchangedCount == 1) "" else "s"} unchanged.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ApSummaryRow(ap: ObservedAp) {
    Column {
        Text(ap.ssid.ifEmpty { "<hidden>" }, style = MaterialTheme.typography.bodyLarge)
        Text(
            "${ap.bandLabel()} · ${ap.rssiDbm} dBm · ${ap.securityLabel()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ApChangeRow(change: ApChange) {
    Column {
        Text(change.after.ssid.ifEmpty { "<hidden>" }, style = MaterialTheme.typography.bodyLarge)
        val parts =
            buildList {
                if (change.rssiDeltaDbm != 0) {
                    val sign = if (change.rssiDeltaDbm > 0) "+" else ""
                    add("RSSI $sign${change.rssiDeltaDbm} dBm")
                }
                if (change.securityChanged) {
                    add("security ${change.before.securityLabel()} → ${change.after.securityLabel()}")
                }
                if (change.standardChanged) {
                    add("standard ${change.before.standardLabel()} → ${change.after.standardLabel()}")
                }
                if (change.channelChanged) add("channel changed")
            }
        Text(
            parts.joinToString(", "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun ObservedAp.bandLabel(): String = runCatching { Band.valueOf(band) }.getOrNull()?.label() ?: band

private fun ObservedAp.standardLabel(): String =
    runCatching { WifiStandard.valueOf(standard) }.getOrNull()?.label() ?: standard

private fun ObservedAp.securityLabel(): String =
    security
        .split(",")
        .filter { it.isNotBlank() }
        .mapNotNull { runCatching { SecurityType.valueOf(it) }.getOrNull() }
        .toSet()
        .label()
