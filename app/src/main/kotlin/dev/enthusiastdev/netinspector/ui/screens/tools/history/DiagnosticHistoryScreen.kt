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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.enthusiastdev.netinspector.data.persistence.diagnostics.DiagnosticRunEntity
import dev.enthusiastdev.netinspector.ui.adaptive.rememberListDetailNavigator
import kotlinx.coroutines.launch

@Composable
fun DiagnosticHistoryRoute(
    modifier: Modifier = Modifier,
    viewModel: DiagnosticHistoryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val csvLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            uri?.let { writeExport(context, coroutineScope, it, DiagnosticHistoryExporter.toCsv(uiState.runs)) }
        }
    val jsonLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            uri?.let { writeExport(context, coroutineScope, it, DiagnosticHistoryExporter.toJson(uiState.runs)) }
        }
    DiagnosticHistoryScreen(
        uiState = uiState,
        onExportCsv = { csvLauncher.launch("netinspector-diagnostic-history.csv") },
        onExportJson = { jsonLauncher.launch("netinspector-diagnostic-history.json") },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun DiagnosticHistoryScreen(
    uiState: DiagnosticHistoryUiState,
    modifier: Modifier = Modifier,
    onExportCsv: () -> Unit = {},
    onExportJson: () -> Unit = {},
) {
    val navigator = rememberListDetailNavigator<Long>()
    val coroutineScope = rememberCoroutineScope()
    BackHandler(enabled = navigator.canNavigateBack()) {
        coroutineScope.launch { navigator.navigateBack() }
    }

    NavigableListDetailPaneScaffold(
        navigator = navigator,
        modifier = modifier,
        listPane = {
            AnimatedPane {
                DiagnosticHistoryListPane(
                    runs = uiState.runs,
                    onRunClick = { id ->
                        coroutineScope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, id) }
                    },
                    onExportCsv = onExportCsv,
                    onExportJson = onExportJson,
                )
            }
        },
        detailPane = {
            AnimatedPane {
                navigator.currentDestination?.contentKey?.let { id ->
                    uiState.runs.firstOrNull { it.id == id }?.let { run -> DiagnosticRunDetailPane(run) }
                }
            }
        },
    )
}

@Composable
private fun DiagnosticHistoryListPane(
    runs: List<DiagnosticRunEntity>,
    onRunClick: (Long) -> Unit,
    onExportCsv: () -> Unit,
    onExportJson: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (runs.isNotEmpty()) {
            item { HistoryExportRow(onExportCsv, onExportJson) }
        }
        if (runs.isEmpty()) {
            item { Text("No diagnostic runs yet - results from Ping, Traceroute and the other tools appear here.") }
        } else {
            items(runs, key = { it.id }) { run -> DiagnosticRunRow(run, onClick = { onRunClick(run.id) }) }
        }
    }
}

@Composable
private fun DiagnosticRunRow(
    run: DiagnosticRunEntity,
    onClick: () -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = run.target, style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace)
            Text(text = run.summary, style = MaterialTheme.typography.bodySmall)
            Text(
                text = "${run.toolType.lowercase().replace('_', ' ')} · ${run.timestampMillis.asRelativeTime()}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
