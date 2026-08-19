package dev.enthusiastdev.netinspector.ui.screens.tools.history

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.enthusiastdev.netinspector.data.persistence.diagnostics.DiagnosticRunEntity
import kotlinx.coroutines.launch

@Composable
fun DiagnosticHistoryRoute(
    modifier: Modifier = Modifier,
    viewModel: DiagnosticHistoryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    DiagnosticHistoryScreen(uiState = uiState, modifier = modifier)
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun DiagnosticHistoryScreen(
    uiState: DiagnosticHistoryUiState,
    modifier: Modifier = Modifier,
) {
    val navigator = rememberListDetailPaneScaffoldNavigator<Long>()
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
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Text(text = "Diagnostic history", style = MaterialTheme.typography.titleLarge) }
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
