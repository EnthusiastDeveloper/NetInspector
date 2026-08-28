package dev.enthusiastdev.netinspector.ui.screens.tools.history

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.enthusiastdev.netinspector.core.designsystem.chart.RollingLineChart
import dev.enthusiastdev.netinspector.core.designsystem.component.InfoCard
import dev.enthusiastdev.netinspector.core.designsystem.component.InfoRow
import dev.enthusiastdev.netinspector.data.persistence.scan.KnownApEntity
import dev.enthusiastdev.netinspector.data.persistence.scan.RssiHistoryPoint
import dev.enthusiastdev.netinspector.ui.adaptive.rememberListDetailNavigator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@Composable
fun ScanHistoryRoute(
    modifier: Modifier = Modifier,
    viewModel: ScanHistoryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val csvLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            uri?.let { writeExport(context, coroutineScope, it, ScanHistoryExporter.toCsv(uiState.knownAps)) }
        }
    val jsonLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            uri?.let { writeExport(context, coroutineScope, it, ScanHistoryExporter.toJson(uiState.knownAps)) }
        }
    ScanHistoryScreen(
        uiState = uiState,
        rssiHistoryFor = viewModel::rssiHistory,
        onExportCsv = { csvLauncher.launch("netinspector-wifi-history.csv") },
        onExportJson = { jsonLauncher.launch("netinspector-wifi-history.json") },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun ScanHistoryScreen(
    uiState: ScanHistoryUiState,
    rssiHistoryFor: (String) -> Flow<List<RssiHistoryPoint>>,
    modifier: Modifier = Modifier,
    onExportCsv: () -> Unit = {},
    onExportJson: () -> Unit = {},
) {
    val navigator = rememberListDetailNavigator<String>()
    val coroutineScope = rememberCoroutineScope()
    BackHandler(enabled = navigator.canNavigateBack()) {
        coroutineScope.launch { navigator.navigateBack() }
    }

    NavigableListDetailPaneScaffold(
        navigator = navigator,
        modifier = modifier,
        listPane = {
            AnimatedPane {
                ScanHistoryListPane(
                    knownAps = uiState.knownAps,
                    onApClick = { bssid ->
                        coroutineScope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, bssid) }
                    },
                    onExportCsv = onExportCsv,
                    onExportJson = onExportJson,
                )
            }
        },
        detailPane = {
            AnimatedPane {
                navigator.currentDestination?.contentKey?.let { bssid ->
                    val knownAp = uiState.knownAps.firstOrNull { it.bssid == bssid }
                    if (knownAp != null) {
                        val history by rssiHistoryFor(bssid).collectAsState(initial = emptyList())
                        ScanHistoryDetailPane(knownAp, history.map { it.rssiDbm.toFloat() })
                    }
                }
            }
        },
    )
}

@Composable
private fun ScanHistoryListPane(
    knownAps: List<KnownApEntity>,
    onApClick: (String) -> Unit,
    onExportCsv: () -> Unit,
    onExportJson: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (knownAps.isNotEmpty()) {
            item { HistoryExportRow(onExportCsv, onExportJson) }
        }
        if (knownAps.isEmpty()) {
            item { Text("No networks seen yet - visit the Wi-Fi tab to start collecting history.") }
        } else {
            items(knownAps, key = { it.bssid }) { knownAp ->
                KnownApRow(knownAp, onClick = { onApClick(knownAp.bssid) })
            }
        }
    }
}

@Composable
private fun KnownApRow(
    knownAp: KnownApEntity,
    onClick: () -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = knownAp.ssid.ifEmpty { "<hidden>" }, style = MaterialTheme.typography.bodyLarge)
            Text(text = knownAp.bssid, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            Text(
                text = "Best ${knownAp.bestRssiDbm} dBm · last seen ${knownAp.lastSeenMillis.asRelativeTime()}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ScanHistoryDetailPane(
    knownAp: KnownApEntity,
    rssiSamples: List<Float>,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(text = knownAp.ssid.ifEmpty { "<hidden>" }, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = knownAp.bssid,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        item {
            InfoCard(title = "RSSI history") {
                if (rssiSamples.size < 2) {
                    Text("Not enough history yet - this AP needs to appear in more scans.")
                } else {
                    RollingLineChart(
                        samples = rssiSamples,
                        minValue = -100f,
                        maxValue = -30f,
                        contentDescription =
                            "RSSI history for ${knownAp.ssid.ifEmpty { "this hidden network" }}, " +
                                "${rssiSamples.size} samples, latest ${rssiSamples.last().toInt()} dBm, " +
                                "ranging from ${rssiSamples.min().toInt()} to ${rssiSamples.max().toInt()} dBm",
                    )
                }
            }
        }
        item {
            InfoCard(title = "Details") {
                InfoRow("Vendor", knownAp.vendor ?: "Unknown")
                InfoRow("Best signal", "${knownAp.bestRssiDbm} dBm")
                InfoRow("First seen", knownAp.firstSeenMillis.asRelativeTime())
                InfoRow("Last seen", knownAp.lastSeenMillis.asRelativeTime())
            }
        }
    }
}
