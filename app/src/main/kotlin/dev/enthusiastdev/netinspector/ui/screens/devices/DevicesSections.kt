package dev.enthusiastdev.netinspector.ui.screens.devices

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import dev.enthusiastdev.netinspector.core.model.lan.Host
import dev.enthusiastdev.netinspector.core.model.lan.HostConfidence
import dev.enthusiastdev.netinspector.core.model.lan.SweepProgress

@Composable
internal fun DevicesHeader(
    hostCount: Int,
    progress: SweepProgress,
    isConnected: Boolean,
    onScan: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "$hostCount devices", style = MaterialTheme.typography.titleMedium)
            if (progress.isRunning) {
                OutlinedButton(onClick = onCancel) { Text("Cancel") }
            } else {
                Button(onClick = onScan, enabled = isConnected) { Text("Scan") }
            }
        }
        if (!isConnected) {
            Text(
                "Connect to Wi-Fi to discover devices on the network.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (progress.isRunning) {
            val fraction =
                if (progress.addressesTotal > 0) progress.addressesProbed / progress.addressesTotal.toFloat() else 0f
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            Text(
                "Scanning - ${progress.addressesProbed} of ${progress.addressesTotal} addresses probed",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A dropdown for [DevicesSortOrder] plus one [FilterChip] per [HostConfidence] tier - the chip
 * row scrolls horizontally rather than wrapping, so three chips' worth of label text never
 * pushes into a second line on a narrow/rotated screen (design §Phase 8's adaptive pass). */
@Composable
internal fun DevicesSortFilterBar(
    sortOrder: DevicesSortOrder,
    confidenceFilter: Set<HostConfidence>,
    onSortOrderChange: (DevicesSortOrder) -> Unit,
    onToggleConfidence: (HostConfidence) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        var sortMenuExpanded by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(onClick = { sortMenuExpanded = true }) {
                Text("Sort: ${sortOrder.label()}")
            }
            DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                DevicesSortOrder.entries.forEach { entry ->
                    DropdownMenuItem(
                        text = { Text(entry.label()) },
                        onClick = {
                            onSortOrderChange(entry)
                            sortMenuExpanded = false
                        },
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HostConfidence.entries.forEach { confidence ->
                FilterChip(
                    selected = confidence in confidenceFilter,
                    onClick = { onToggleConfidence(confidence) },
                    label = { Text(confidence.label()) },
                )
            }
        }
    }
}

@Composable
internal fun HostCard(
    host: Host,
    onClick: () -> Unit,
) {
    val isStale = host.confidence == HostConfidence.STALE
    val contentAlpha = if (isStale) 0.5f else 1f

    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.padding(16.dp).alpha(contentAlpha).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ConfidenceDot(host.confidence)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = host.displayName(),
                    style = MaterialTheme.typography.titleMedium,
                    fontStyle = if (host.hasInferredDisplayName) FontStyle.Italic else FontStyle.Normal,
                )
                Text(
                    text = "${host.address.addressString} · ${host.confidence.label()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Skip this line when the title already shows the hint (design §11.3 - an
                // inferred label stands in for the name, but is never shown twice).
                if (!host.hasInferredDisplayName) {
                    host.deviceHint?.let { hint ->
                        Text(
                            text = hint.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfidenceDot(confidence: HostConfidence) {
    val color =
        when (confidence) {
            HostConfidence.CONFIRMED -> MaterialTheme.colorScheme.primary
            HostConfidence.ANNOUNCED -> MaterialTheme.colorScheme.tertiary
            HostConfidence.STALE -> MaterialTheme.colorScheme.outline
        }
    Canvas(modifier = Modifier.size(12.dp)) {
        drawCircle(color = color)
    }
}

@Composable
internal fun FirstRunAcknowledgementDialog(
    onAcknowledge: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Before scanning this network") },
        text = {
            Text(
                "Active host discovery and port scanning against a network you do not own or " +
                    "administer is, depending on jurisdiction, somewhere between impolite and " +
                    "unlawful. Only run this against your own network.",
            )
        },
        confirmButton = { Button(onClick = onAcknowledge) { Text("I understand - scan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}

@Composable
internal fun ShortPrefixConfirmationDialog(
    hostCount: Long,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Large network detected") },
        text = {
            Text(
                "This network's prefix covers $hostCount possible addresses. Scanning all of " +
                    "them will take a while and generate a lot of probe traffic. Continue anyway?",
            )
        },
        confirmButton = { Button(onClick = onConfirm) { Text("Scan anyway") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
