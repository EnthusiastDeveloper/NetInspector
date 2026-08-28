package dev.enthusiastdev.netinspector.ui.screens.devices

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import dev.enthusiastdev.netinspector.core.model.lan.Host
import dev.enthusiastdev.netinspector.core.model.lan.HostConfidence
import dev.enthusiastdev.netinspector.core.model.lan.HygieneScore
import dev.enthusiastdev.netinspector.core.model.lan.SweepProgress
import kotlinx.coroutines.delay

/**
 * The device count, the tap-through [HygieneBadge] and the scan control, all on one row.
 *
 * The hygiene read sits here rather than in its own card so the top of the screen carries one
 * status line instead of a controls column competing with a findings card. The scan action is a
 * [FilledTonalButton] with a radar glyph: still clearly the primary action, but toned down from
 * the old high-emphasis filled pill so it sits with the muted toolbar below rather than shouting
 * over it.
 *
 * For the ~1.8s after a scan finishes the badge expands to a one-line summary. On a narrow
 * window the device count animates out for that beat so the wider pill does not shove the scan
 * button off-screen; once the row has room to spare (a landscape phone, a foldable inner
 * screen, a tablet) the count just stays put and the pill grows into the slack.
 */
@Composable
internal fun DevicesSummaryRow(
    hostCount: Int,
    hygiene: HygieneScore?,
    progress: SweepProgress,
    isConnected: Boolean,
    onScan: () -> Unit,
    onCancel: () -> Unit,
    onHygieneClick: () -> Unit,
) {
    val scanning = progress.isRunning
    var justResolved by remember { mutableStateOf(false) }
    var wasScanning by remember { mutableStateOf(scanning) }
    LaunchedEffect(scanning, hygiene) {
        if (wasScanning && !scanning && hygiene != null) justResolved = true
        wasScanning = scanning
    }
    LaunchedEffect(justResolved) {
        if (justResolved) {
            delay(HYGIENE_REVEAL_MILLIS)
            justResolved = false
        }
    }
    val roomyRow = LocalConfiguration.current.screenWidthDp >= WIDE_SUMMARY_ROW_DP
    val showCount = roomyRow || !justResolved

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedVisibility(visible = showCount) {
                Text(text = "$hostCount devices", style = MaterialTheme.typography.titleMedium, maxLines = 1)
            }
            HygieneBadge(
                score = hygiene,
                scanning = scanning,
                expanded = justResolved,
                onClick = onHygieneClick,
            )
            Spacer(modifier = Modifier.weight(1f))
            if (scanning) {
                OutlinedButton(onClick = onCancel) { Text("Cancel") }
            } else {
                ScanButton(onClick = onScan, enabled = isConnected)
            }
        }
        if (!isConnected) {
            Text(
                "Connect to Wi-Fi to discover devices on the network.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (scanning) {
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

/** Duration of the post-scan badge reveal, shared with [HygieneBadge]'s expanded state. */
private const val HYGIENE_REVEAL_MILLIS = 1800L

/** At or above this window width the summary row has room for the expanded hygiene pill without
 * hiding the device count. Below it (a phone in portrait) the count animates out for the beat. */
private const val WIDE_SUMMARY_ROW_DP = 520

@Composable
private fun ScanButton(
    onClick: () -> Unit,
    enabled: Boolean,
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
    ) {
        Icon(Icons.Filled.Radar, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
        // softWrap off so a transient width squeeze can never wrap this to "S/c/a/n".
        Text("Scan", maxLines = 1, softWrap = false)
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
