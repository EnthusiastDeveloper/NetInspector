package dev.enthusiastdev.netinspector.ui.screens.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.enthusiastdev.netinspector.core.common.wifi.rssiToQualityPercent
import dev.enthusiastdev.netinspector.core.designsystem.component.InfoCard
import dev.enthusiastdev.netinspector.core.designsystem.component.InfoRow
import dev.enthusiastdev.netinspector.core.designsystem.gauge.RssiGauge
import dev.enthusiastdev.netinspector.core.model.connection.ConnectionSnapshot

@Composable
internal fun ConnectionHeader(
    snapshot: ConnectionSnapshot,
    locationAccess: LocationAccessState,
    modifier: Modifier = Modifier,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier.fillMaxWidth()) {
        snapshot.rssiDbm?.let { rssi ->
            RssiGauge(rssiDbm = rssi, qualityPercent = rssiToQualityPercent(rssi))
        }
        Text(text = snapshot.ssidLabel(locationAccess), style = MaterialTheme.typography.titleLarge)
        Text(
            text = snapshot.bssidLabel(locationAccess),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
internal fun StatusBadges(snapshot: ConnectionSnapshot) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (snapshot.hasInternet) StatusChip("Internet", Icons.Filled.CheckCircle)
        if (snapshot.isCaptivePortal) StatusChip("Captive portal", Icons.Filled.Warning)
        if (snapshot.isMetered) StatusChip("Metered", Icons.Filled.DataUsage)
    }
}

@Composable
private fun StatusChip(
    label: String,
    icon: ImageVector,
) {
    SuggestionChip(
        onClick = {},
        label = { Text(label) },
        icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(SuggestionChipDefaults.IconSize)) },
    )
}

@Composable
internal fun RadioSection(snapshot: ConnectionSnapshot) {
    InfoCard(title = "Radio") {
        InfoRow("Band", snapshot.span?.band?.label() ?: "Unknown")
        InfoRow("Channel", snapshot.span?.primaryChannel?.toString() ?: "Unknown")
        InfoRow("Standard", snapshot.standard.label())
        InfoRow("Tx link speed", snapshot.txLinkSpeedMbps?.let { "$it Mbps" } ?: "Unknown")
        InfoRow("Rx link speed", snapshot.rxLinkSpeedMbps?.let { "$it Mbps" } ?: "Unknown")
    }
}

@Composable
internal fun Ipv4Section(snapshot: ConnectionSnapshot) {
    InfoCard(title = "IPv4") {
        val address = snapshot.ipv4?.let { "${it.address.hostAddress}/${it.prefixLength}" } ?: "Unknown"
        InfoRow("Address", address)
        InfoRow("Gateway", snapshot.gateway?.hostAddress ?: "Unknown")
        InfoRow("Domains", snapshot.domains ?: "None")
    }
}

@Composable
internal fun Ipv6Section(snapshot: ConnectionSnapshot) {
    InfoCard(title = "IPv6 (display only)") {
        snapshot.ipv6.forEach { info ->
            InfoRow(info.address.hostAddress ?: "?", "/${info.prefixLength}")
        }
    }
}

@Composable
internal fun DnsSection(snapshot: ConnectionSnapshot) {
    InfoCard(title = "DNS servers") {
        snapshot.dnsServers.forEach { dns ->
            Text(text = dns.hostAddress ?: "?", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
