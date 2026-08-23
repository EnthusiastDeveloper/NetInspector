package dev.enthusiastdev.netinspector.ui.screens.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.enthusiastdev.netinspector.core.common.wifi.rssiToQualityPercent
import dev.enthusiastdev.netinspector.core.designsystem.component.InfoCard
import dev.enthusiastdev.netinspector.core.designsystem.component.InfoRow
import dev.enthusiastdev.netinspector.core.designsystem.gauge.RssiGauge
import dev.enthusiastdev.netinspector.core.model.connection.ConnectionSnapshot
import dev.enthusiastdev.netinspector.core.model.settings.RssiDisplayUnit

private val HEADER_GAUGE_DIAMETER = 132.dp

/**
 * The two things worth knowing at a glance - which network this is, and how well it's heard -
 * side by side, each under its own label.
 *
 * They used to be stacked, with the BSSID directly under the SSID in monospace: a 17-character
 * hex string given the second-most prominent position on the screen, above the addressing and
 * radio details anyone actually reads. The BSSID is now an [InfoRow] in [RadioSection], where it
 * belongs among the other identifiers.
 */
@Composable
internal fun ConnectionHeader(
    snapshot: ConnectionSnapshot,
    locationAccess: LocationAccessState,
    rssiDisplayUnit: RssiDisplayUnit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NetworkNameColumn(snapshot, locationAccess, modifier = Modifier.weight(1f))
        SignalColumn(snapshot, rssiDisplayUnit)
    }
}

@Composable
private fun NetworkNameColumn(
    snapshot: ConnectionSnapshot,
    locationAccess: LocationAccessState,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SectionLabel("Network")
        Text(
            text = snapshot.ssidLabel(locationAccess),
            style = MaterialTheme.typography.titleLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        snapshot.span?.let { span ->
            Text(
                text = "${span.band.label()} · channel ${span.primaryChannel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SignalColumn(
    snapshot: ConnectionSnapshot,
    rssiDisplayUnit: RssiDisplayUnit,
) {
    val rssi = snapshot.rssiDbm ?: return
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SectionLabel("Signal")
        RssiGauge(
            rssiDbm = rssi,
            qualityPercent = rssiToQualityPercent(rssi),
            showAsPercent = rssiDisplayUnit == RssiDisplayUnit.PERCENT,
            diameter = HEADER_GAUGE_DIAMETER,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun RadioSection(
    snapshot: ConnectionSnapshot,
    locationAccess: LocationAccessState,
) {
    InfoCard(title = "Radio") {
        InfoRow("Band", snapshot.span?.band?.label() ?: "Unknown")
        InfoRow("Channel", snapshot.span?.primaryChannel?.toString() ?: "Unknown")
        InfoRow("Channel width", snapshot.span?.let { "${it.widthMhz} MHz" } ?: "Unknown")
        InfoRow("Standard", snapshot.standard.label())
        InfoRow("Tx link speed", snapshot.txLinkSpeedMbps?.let { "$it Mbps" } ?: "Unknown")
        InfoRow("Rx link speed", snapshot.rxLinkSpeedMbps?.let { "$it Mbps" } ?: "Unknown")
        // The access point's MAC. Kept last in this card rather than under the network name: it
        // identifies the radio, and only matters when you already care which of several APs on
        // one SSID you landed on.
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "Access point MAC",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = snapshot.bssidLabel(locationAccess),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
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
