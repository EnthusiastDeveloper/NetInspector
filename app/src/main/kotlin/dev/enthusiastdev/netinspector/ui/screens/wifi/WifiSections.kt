package dev.enthusiastdev.netinspector.ui.screens.wifi

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import dev.enthusiastdev.netinspector.core.common.wifi.rssiToQualityPercent
import dev.enthusiastdev.netinspector.core.designsystem.component.InfoRow
import dev.enthusiastdev.netinspector.core.model.wifi.AccessPoint
import dev.enthusiastdev.netinspector.core.model.wifi.Band
import dev.enthusiastdev.netinspector.core.model.wifi.ScanBudget
import dev.enthusiastdev.netinspector.ui.screens.connection.label
import java.time.Duration
import java.time.Instant

@Composable
internal fun WifiHeader(
    apCount: Int,
    lastUpdated: Instant?,
    budget: ScanBudget,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val headerText =
            if (lastUpdated == null) {
                "No results yet"
            } else {
                "$apCount networks - results as of ${lastUpdated.asClockTime()}"
            }
        Text(
            text = headerText,
            style = MaterialTheme.typography.titleMedium,
        )
        val retryAt = budget.nextAvailableAt
        if (retryAt != null) {
            val remaining = Duration.between(Instant.now(), retryAt).let { if (it.isNegative) Duration.ZERO else it }
            Text(
                text = "Active scan throttled - next in ${remaining.asCountdown()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun WifiFilterSortBar(
    sortOrder: WifiSortOrder,
    onSortOrderChange: (WifiSortOrder) -> Unit,
    bandFilter: Set<Band>,
    onBandFilterChange: (Set<Band>) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(Band.GHZ_2_4, Band.GHZ_5, Band.GHZ_6).forEach { band ->
            FilterChip(
                selected = band in bandFilter,
                onClick = {
                    onBandFilterChange(if (band in bandFilter) bandFilter - band else bandFilter + band)
                },
                label = { Text(band.label()) },
            )
        }

        var sortMenuExpanded by remember { mutableStateOf(false) }
        Column {
            TextButton(onClick = { sortMenuExpanded = true }) {
                Text("Sort: ${sortOrder.label()}")
            }
            DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                WifiSortOrder.entries.forEach { order ->
                    DropdownMenuItem(
                        text = { Text(order.label()) },
                        onClick = {
                            onSortOrderChange(order)
                            sortMenuExpanded = false
                        },
                    )
                }
            }
        }
    }
}

private fun WifiSortOrder.label(): String =
    when (this) {
        WifiSortOrder.SIGNAL -> "Signal"
        WifiSortOrder.NAME -> "Name"
        WifiSortOrder.CHANNEL -> "Channel"
    }

internal fun LazyListScope.wifiGroupItems(
    groups: List<WifiGroup>,
    onApClick: (String) -> Unit,
) {
    items(groups, key = { it.ssid.ifEmpty { it.members.first().bssid } }) { group ->
        WifiGroupCard(group, onApClick)
    }
}

@Composable
private fun WifiGroupCard(
    group: WifiGroup,
    onApClick: (String) -> Unit,
) {
    var expanded by remember(group.ssid) { mutableStateOf(false) }
    val strongest = group.members.first() // pre-sorted by RSSI descending within the group
    val hasMultipleRadios = group.members.size > 1

    Card(
        onClick = { if (hasMultipleRadios) expanded = !expanded else onApClick(strongest.bssid) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = strongest.ssid.ifEmpty { "<hidden>" },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = summaryLine(strongest),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (hasMultipleRadios) {
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                    )
                }
            }
            if (expanded) {
                group.members.forEach { ap ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable { onApClick(ap.bssid) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        InfoRow(label = ap.bssid, value = summaryLine(ap))
                    }
                }
            }
        }
    }
}

private fun summaryLine(ap: AccessPoint): String {
    val flags =
        buildList {
            if (ap.isConnected) add("connected")
            if (ap.isDfsChannel) add("DFS")
            if (ap.is6GhzPsc) add("PSC")
        }
    val quality = rssiToQualityPercent(ap.rssiDbm)
    val base = "Ch ${ap.span.primaryChannel} · ${ap.rssiDbm} dBm ($quality%) · ${ap.security.label()}"
    return if (flags.isEmpty()) base else "$base · ${flags.joinToString()}"
}
