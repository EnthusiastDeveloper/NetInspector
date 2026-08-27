package dev.enthusiastdev.netinspector.ui.screens.tools.throughput

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.enthusiastdev.netinspector.core.designsystem.chart.RollingLineChart
import dev.enthusiastdev.netinspector.core.designsystem.component.InfoCard
import dev.enthusiastdev.netinspector.core.designsystem.component.InfoRow
import dev.enthusiastdev.netinspector.core.model.diagnostics.ThroughputResult

@Composable
fun ThroughputRoute(
    modifier: Modifier = Modifier,
    viewModel: ThroughputViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    ThroughputScreen(
        uiState = uiState,
        onTargetChange = viewModel::updateTarget,
        onSelectHost = viewModel::selectHost,
        onStart = viewModel::start,
        onStop = viewModel::stop,
        modifier = modifier,
    )
}

@Composable
fun ThroughputScreen(
    uiState: ThroughputUiState,
    onTargetChange: (String) -> Unit,
    onSelectHost: (HostOption) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(modifier = Modifier.fillMaxHeight().widthIn(max = 700.dp)) {
            ThroughputForm(uiState, onTargetChange, onSelectHost, onStart, onStop)

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (uiState.mbpsSamples.size >= 2) {
                    item { ThroughputChartCard(uiState) }
                }
                uiState.result?.let { item { ThroughputResultCard(it) } }
                uiState.correlationAtStart?.let { item { WifiCorrelationCard("Wi-Fi at test start", it) } }
                uiState.correlationAtEnd?.let { item { WifiCorrelationCard("Wi-Fi at test end", it) } }
            }
        }
    }
}

@Composable
private fun ThroughputForm(
    uiState: ThroughputUiState,
    onTargetChange: (String) -> Unit,
    onSelectHost: (HostOption) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    var isDropdownExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // "measures throughput to a device on your local network, not your internet connection" -
        // required copy (docs/ideas.md #31's rescope) so this never reads as a
        // speedtest.net-style internet speed test.
        Text(
            text = "Measures throughput to a device on your local network, not your internet connection.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = uiState.target,
                    onValueChange = onTargetChange,
                    label = { Text("Host or IP on your network") },
                    singleLine = true,
                    trailingIcon = {
                        if (uiState.hostOptions.isNotEmpty()) {
                            IconButton(onClick = { isDropdownExpanded = true }) {
                                Icon(Icons.Filled.ArrowDropDown, contentDescription = "Choose a known device")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                DropdownMenu(expanded = isDropdownExpanded, onDismissRequest = { isDropdownExpanded = false }) {
                    uiState.hostOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text("${option.label} (${option.address})") },
                            onClick = {
                                onSelectHost(option)
                                isDropdownExpanded = false
                            },
                        )
                    }
                }
            }
            Button(onClick = if (uiState.isRunning) onStop else onStart) {
                Text(if (uiState.isRunning) "Stop" else "Test")
            }
        }
        if (uiState.isRunning) {
            Text(
                text = "${uiState.packetsReceived} / ${uiState.packetsSent} probes replied",
                style = MaterialTheme.typography.labelSmall,
            )
        }
        uiState.errorMessage?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ThroughputChartCard(uiState: ThroughputUiState) {
    val maxSample = uiState.mbpsSamples.max()
    InfoCard(title = "Throughput") {
        RollingLineChart(
            samples = uiState.mbpsSamples,
            minValue = 0f,
            maxValue = (maxSample * 1.2f).coerceAtLeast(1f),
            contentDescription =
                "Round-trip throughput over the run so far, latest %.1f Mbps, up to %.1f Mbps"
                    .format(uiState.mbpsSamples.last(), maxSample),
            valueLabel = { "%.0f Mbps".format(it) },
        )
    }
}

@Composable
private fun ThroughputResultCard(result: ThroughputResult) {
    InfoCard(title = "Result") {
        InfoRow("Average", "%.1f Mbps".format(result.avgMbps))
        InfoRow("Peak", "%.1f Mbps".format(result.peakMbps))
        InfoRow("Sent / received", "${result.packetsSent} / ${result.packetsReceived}")
        InfoRow("Loss", "%.0f%%".format(result.lossPercent))
    }
}

@Composable
private fun WifiCorrelationCard(
    title: String,
    correlation: WifiCorrelationSnapshot,
) {
    InfoCard(title = title) {
        if (correlation.ssid == null) {
            Text("Not connected to Wi-Fi", style = MaterialTheme.typography.bodySmall)
        } else {
            InfoRow("Network", correlation.ssid)
            correlation.rssiDbm?.let { InfoRow("RSSI", "$it dBm") }
            correlation.channel?.let { channel ->
                val width = correlation.widthMhz?.let { " ($it MHz)" }.orEmpty()
                InfoRow("Channel", "$channel$width")
            }
            correlation.overlappingApCount?.let { InfoRow("Other networks on this channel", it.toString()) }
        }
    }
}
