package dev.enthusiastdev.netinspector.ui.screens.tools.portscanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.enthusiastdev.netinspector.core.model.diagnostics.PortScanFinding
import dev.enthusiastdev.netinspector.core.model.diagnostics.PortScanPresetKind
import dev.enthusiastdev.netinspector.core.model.diagnostics.PortScanProgress
import dev.enthusiastdev.netinspector.core.model.diagnostics.PortSelection

@Composable
fun PortScannerRoute(
    modifier: Modifier = Modifier,
    viewModel: PortScannerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    PortScannerScreen(
        uiState = uiState,
        onTargetChange = viewModel::updateTarget,
        onSelectionChange = viewModel::updateSelection,
        onCustomStartChange = viewModel::updateCustomStart,
        onCustomEndChange = viewModel::updateCustomEnd,
        onStart = viewModel::start,
        onStop = viewModel::stop,
        modifier = modifier,
    )
}

@Composable
fun PortScannerScreen(
    uiState: PortScannerUiState,
    onTargetChange: (String) -> Unit,
    onSelectionChange: (PortSelection) -> Unit,
    onCustomStartChange: (String) -> Unit,
    onCustomEndChange: (String) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().widthIn(max = 600.dp)) {
        PortScannerForm(
            uiState,
            onTargetChange,
            onSelectionChange,
            onCustomStartChange,
            onCustomEndChange,
            onStart,
            onStop,
        )

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(uiState.findings.sortedBy { it.port }) { finding -> FindingRow(finding) }
        }
    }
}

@Composable
private fun PortScannerForm(
    uiState: PortScannerUiState,
    onTargetChange: (String) -> Unit,
    onSelectionChange: (PortSelection) -> Unit,
    onCustomStartChange: (String) -> Unit,
    onCustomEndChange: (String) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = uiState.target,
                onValueChange = onTargetChange,
                label = { Text("Host or IP") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = if (uiState.isRunning) onStop else onStart) {
                Text(if (uiState.isRunning) "Stop" else "Scan")
            }
        }

        PortPresetChips(uiState, onSelectionChange)

        if (uiState.selection.kind == PortScanPresetKind.CUSTOM) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = uiState.customStart,
                    onValueChange = onCustomStartChange,
                    label = { Text("Start") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = uiState.customEnd,
                    onValueChange = onCustomEndChange,
                    label = { Text("End") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Text(
            text = "TCP connect scan - not a SYN scan; results reflect what a normal connection sees.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        uiState.progress?.let { progress -> ScanProgress(progress) }
        uiState.errorMessage?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun PortPresetChips(
    uiState: PortScannerUiState,
    onSelectionChange: (PortSelection) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = uiState.selection.kind == PortScanPresetKind.COMMON,
            onClick = { onSelectionChange(PortSelection.Common) },
            label = { Text("Common") },
        )
        FilterChip(
            selected = uiState.selection.kind == PortScanPresetKind.WELL_KNOWN,
            onClick = { onSelectionChange(PortSelection.WellKnown) },
            label = { Text("1-1024") },
        )
        FilterChip(
            selected = uiState.selection.kind == PortScanPresetKind.ALL,
            onClick = { onSelectionChange(PortSelection.All) },
            label = { Text("All") },
        )
        FilterChip(
            selected = uiState.selection.kind == PortScanPresetKind.CUSTOM,
            onClick = {
                val start = uiState.customStart.toIntOrNull() ?: 1
                val end = uiState.customEnd.toIntOrNull() ?: 1024
                onSelectionChange(PortSelection.Custom(start, end))
            },
            label = { Text("Custom") },
        )
    }
}

@Composable
private fun ScanProgress(progress: PortScanProgress) {
    Column {
        LinearProgressIndicator(
            progress = { if (progress.total == 0) 0f else progress.scanned / progress.total.toFloat() },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "${progress.scanned} / ${progress.total} ports scanned",
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun FindingRow(finding: PortScanFinding) {
    val bannerLabel =
        finding.banner
            ?.takeIf(String::isNotBlank)
            ?.let { " - $it" }
            .orEmpty()
    Text(
        text = "${finding.port} open$bannerLabel",
        fontFamily = FontFamily.Monospace,
        style = MaterialTheme.typography.bodySmall,
    )
}
