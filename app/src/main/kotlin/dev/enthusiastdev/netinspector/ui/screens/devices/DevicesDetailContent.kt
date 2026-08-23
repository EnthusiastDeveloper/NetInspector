package dev.enthusiastdev.netinspector.ui.screens.devices

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import dev.enthusiastdev.netinspector.core.model.lan.Host
import dev.enthusiastdev.netinspector.core.model.lan.HostConfidence
import dev.enthusiastdev.netinspector.core.model.lan.nicknameKey

/** design §3 Phase 6 - the Devices detail pane counterpart to [DevicesListPane]'s [HostCard]
 * rows. Looks up the host by address string each recomposition rather than capturing a [Host]
 * snapshot, so a host that goes `STALE` (or drops out of the list entirely - design §8.3's
 * "kept visible for one sweep, greyed, then dropped") updates or clears here too. */
@Composable
internal fun DevicesDetailPane(
    address: String,
    hosts: List<Host>,
    onPingHost: (String) -> Unit,
    onTracerouteHost: (String) -> Unit,
    onPortScanHost: (String) -> Unit,
    onSetNickname: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val host = hosts.firstOrNull { it.address.addressString == address }
    if (host == null) {
        Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("This device is no longer visible", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }
    DevicesDetailContent(host, onPingHost, onTracerouteHost, onPortScanHost, onSetNickname, modifier)
}

@Composable
internal fun DevicesDetailContent(
    host: Host,
    onPingHost: (String) -> Unit,
    onTracerouteHost: (String) -> Unit,
    onPortScanHost: (String) -> Unit,
    onSetNickname: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { DevicesDetailHeader(host, onPingHost, onTracerouteHost, onPortScanHost, onSetNickname) }
        host.deviceHint?.let { hint -> item { DevicesDetailHintCard(hint) } }
        item { DevicesDetailIdentificationCard(host) }
        if (host.hostnames.isNotEmpty()) item { DevicesDetailHostnamesCard(host) }
        if (host.openPorts.isNotEmpty()) item { DevicesDetailHygieneScoreCard(host) }
        if (host.openPorts.isNotEmpty()) item { DevicesDetailOpenPortsCard(host) }
        if (host.services.isNotEmpty()) item { DevicesDetailServicesCard(host) }
        item { DevicesDetailEvidenceCard(host) }
    }
}

@Composable
private fun DevicesDetailHeader(
    host: Host,
    onPingHost: (String) -> Unit,
    onTracerouteHost: (String) -> Unit,
    onPortScanHost: (String) -> Unit,
    onSetNickname: (String, String) -> Unit,
) {
    val context = LocalContext.current
    var showNicknameEditor by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = host.displayName(),
                style = MaterialTheme.typography.titleLarge,
                fontStyle = if (host.hasInferredDisplayName) FontStyle.Italic else FontStyle.Normal,
            )
            IconButton(onClick = { showNicknameEditor = true }) {
                Icon(Icons.Filled.Edit, contentDescription = "Set a nickname for this device")
            }
        }
        // Android 13+ (this app's minSdk) shows its own "Copied" confirmation UI on every
        // clipboard write, so no in-app snackbar/toast is needed here.
        Text(
            text = host.address.addressString,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            modifier =
                Modifier.clickable {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(ClipData.newPlainText("IP address", host.address.addressString))
                },
        )
        ConfidenceLabel(host.confidence)
        // design §3 Phase 6 - deep links into the diagnostic tools, pre-filled. Every action
        // here is a no-op the tool would just resolve straight back to itself for isSelf, so
        // the whole row is skipped rather than offered as dead-feeling actions.
        if (!host.isSelf) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Button(onClick = { onPingHost(host.address.addressString) }) {
                    Text("Ping")
                }
                OutlinedButton(onClick = { onTracerouteHost(host.address.addressString) }) {
                    Text("Traceroute")
                }
                OutlinedButton(onClick = { onPortScanHost(host.address.addressString) }) {
                    Text("Scan ports")
                }
            }
        }
    }
    if (showNicknameEditor) {
        NicknameEditorDialog(
            currentNickname = host.nickname.orEmpty(),
            onSave = { nickname ->
                onSetNickname(host.nicknameKey(), nickname)
                showNicknameEditor = false
            },
            onDismiss = { showNicknameEditor = false },
        )
    }
}

/** docs/device-identification-ideas.md D - overrides every automated naming signal with a
 * plain user-entered label; saving a blank value clears it (`SavedHostRepository.setNickname`),
 * so there's no separate "remove" action to offer here. */
@Composable
private fun NicknameEditorDialog(
    currentNickname: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(currentNickname) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nickname") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("e.g. \"Living room printer\"") },
                singleLine = true,
            )
        },
        confirmButton = { Button(onClick = { onSave(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** design §11.3 - "no field without a basis," extended to the confidence label itself: it's
 * jargon on first read, so it's tappable rather than left to speak for itself. */
@Composable
private fun ConfidenceLabel(confidence: HostConfidence) {
    var showExplanation by remember { mutableStateOf(false) }
    Text(
        text = confidence.label(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.clickable { showExplanation = true },
    )
    if (showExplanation) {
        AlertDialog(
            onDismissRequest = { showExplanation = false },
            title = { Text("What does \"${confidence.label()}\" mean?") },
            text = { Text(confidence.explanation()) },
            confirmButton = { TextButton(onClick = { showExplanation = false }) { Text("Got it") } },
        )
    }
}
