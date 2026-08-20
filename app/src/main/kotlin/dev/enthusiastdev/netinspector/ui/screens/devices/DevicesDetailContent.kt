package dev.enthusiastdev.netinspector.ui.screens.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.enthusiastdev.netinspector.core.designsystem.component.InfoCard
import dev.enthusiastdev.netinspector.core.designsystem.component.InfoRow
import dev.enthusiastdev.netinspector.core.model.lan.DeviceHint
import dev.enthusiastdev.netinspector.core.model.lan.Host
import dev.enthusiastdev.netinspector.core.model.lan.portRiskNote

/** design §3 Phase 6 - the Devices detail pane counterpart to [DevicesListPane]'s [HostCard]
 * rows. Looks up the host by address string each recomposition rather than capturing a [Host]
 * snapshot, so a host that goes `STALE` (or drops out of the list entirely - design §8.3's
 * "kept visible for one sweep, greyed, then dropped") updates or clears here too. */
@Composable
internal fun DevicesDetailPane(
    address: String,
    hosts: List<Host>,
    onPingHost: (String) -> Unit,
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
    DevicesDetailContent(host, onPingHost, modifier)
}

@Composable
internal fun DevicesDetailContent(
    host: Host,
    onPingHost: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { DevicesDetailHeader(host, onPingHost) }
        host.deviceHint?.let { hint -> item { DevicesDetailHintCard(hint) } }
        item { DevicesDetailIdentificationCard(host) }
        if (host.hostnames.isNotEmpty()) item { DevicesDetailHostnamesCard(host) }
        if (host.openPorts.isNotEmpty()) item { DevicesDetailOpenPortsCard(host) }
        if (host.services.isNotEmpty()) item { DevicesDetailServicesCard(host) }
        item { DevicesDetailEvidenceCard(host) }
    }
}

@Composable
private fun DevicesDetailHeader(
    host: Host,
    onPingHost: (String) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(text = host.displayName(), style = MaterialTheme.typography.titleLarge)
        Text(
            text = host.address.addressString,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = host.confidence.label(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // design §3 Phase 6 - deep link into the ping tool, pre-filled. Pinging this device
        // (isSelf) is a no-op the tool would just resolve straight back to itself, so it's
        // skipped rather than offered as a dead-feeling action. The port-scan deep link the
        // plan also names has no destination yet - Phase 7 builds that tool.
        if (!host.isSelf) {
            Button(onClick = { onPingHost(host.address.addressString) }, modifier = Modifier.padding(top = 8.dp)) {
                Text("Ping this host")
            }
        }
    }
}

@Composable
private fun DevicesDetailHintCard(hint: DeviceHint) {
    InfoCard(title = "Device identification") {
        InfoRow("Guess", hint.label)
        InfoRow("Confidence", hint.certainty.label())
        InfoRow("Basis", hint.basis)
    }
}

@Composable
private fun DevicesDetailIdentificationCard(host: Host) {
    InfoCard(title = "Network") {
        val rttMedianMs = host.rttMedianMs
        if (rttMedianMs != null) InfoRow("Median RTT", "${rttMedianMs.toInt()} ms")
        host.icmpReplyTtl?.let { InfoRow("ICMP reply TTL", it.toString()) }
        MacAddressRow()
    }
}

/** design C-01/plan Phase 6 - "no empty row, and a short explanation available on tap rather
 * than a mysterious absence." [Host.macAddress] is always `null` on an unrooted device (the
 * ARP table is unreadable from Android 10 on), so this renders as a fixed, tappable row rather
 * than reading `host.macAddress` at all. */
@Composable
private fun MacAddressRow() {
    var showExplanation by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { showExplanation = true },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "MAC address",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = "Not available (tap for why)", style = MaterialTheme.typography.bodyMedium)
    }
    if (showExplanation) {
        AlertDialog(
            onDismissRequest = { showExplanation = false },
            title = { Text("Why no MAC address?") },
            text = {
                Text(
                    "Android 10 and later block apps from reading the device's ARP table, so " +
                        "there is no way to learn another host's MAC address without root. This " +
                        "device is identified instead by what it announces (mDNS, SSDP, NetBIOS), " +
                        "its open ports, and its reverse-DNS name.",
                )
            },
            confirmButton = { TextButton(onClick = { showExplanation = false }) { Text("Got it") } },
        )
    }
}

@Composable
private fun DevicesDetailHostnamesCard(host: Host) {
    InfoCard(title = "Hostnames") {
        host.hostnames.forEach { (source, name) -> InfoRow(source.label(), name) }
    }
}

@Composable
private fun DevicesDetailOpenPortsCard(host: Host) {
    InfoCard(title = "Open ports") {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            host.openPorts.forEach { port ->
                Text(port.label(), style = MaterialTheme.typography.bodyMedium)
                portRiskNote(port.port)?.let { note ->
                    Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun DevicesDetailServicesCard(host: Host) {
    InfoCard(title = "Discovered services") {
        host.services.forEach { service ->
            InfoRow(service.serviceType ?: "Service", service.name ?: service.detail ?: "-")
            service.manufacturer?.let { InfoRow("Manufacturer", it) }
            service.modelName?.let { InfoRow("Model", it) }
            service.txtRecords.forEach { (key, value) -> InfoRow(key, value.ifBlank { "(present)" }) }
        }
    }
}

@Composable
private fun DevicesDetailEvidenceCard(host: Host) {
    InfoCard(title = "Evidence") {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            host.evidence.sortedByDescending { it.observedAt }.forEach { evidence ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        evidence.source.label(),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier =
                            Modifier
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                    Text(evidence.observedAt.asClockTime(), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
