package dev.enthusiastdev.netinspector.ui.screens.tools.ping

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
import dev.enthusiastdev.netinspector.core.designsystem.component.InfoCard
import dev.enthusiastdev.netinspector.core.designsystem.component.InfoRow
import dev.enthusiastdev.netinspector.core.model.diagnostics.PingProbeResult

@Composable
fun PingRoute(
    modifier: Modifier = Modifier,
    viewModel: PingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    PingScreen(
        uiState = uiState,
        onTargetChange = viewModel::updateTarget,
        onStart = viewModel::start,
        onStop = viewModel::stop,
        modifier = modifier,
    )
}

@Composable
fun PingScreen(
    uiState: PingUiState,
    onTargetChange: (String) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().widthIn(max = 600.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
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
                Text(if (uiState.isRunning) "Stop" else "Ping")
            }
        }

        uiState.errorMessage?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(uiState.results) { result -> ProbeResultRow(result) }
            uiState.summary?.let { summary ->
                item {
                    InfoCard(title = "Summary (${summary.tier})") {
                        InfoRow("Sent / received", "${summary.sent} / ${summary.received}")
                        InfoRow("Loss", "%.1f%%".format(summary.lossPercent))
                        summary.minMs?.let { InfoRow("Min", "%.1f ms".format(it)) }
                        summary.avgMs?.let { InfoRow("Avg", "%.1f ms".format(it)) }
                        summary.maxMs?.let { InfoRow("Max", "%.1f ms".format(it)) }
                        summary.medianMs?.let { InfoRow("Median", "%.1f ms".format(it)) }
                        summary.stddevMs?.let { InfoRow("Std dev", "%.1f ms".format(it)) }
                        summary.jitterMs?.let { InfoRow("Jitter", "%.1f ms".format(it)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProbeResultRow(result: PingProbeResult) {
    val text =
        when (result) {
            is PingProbeResult.Reply -> "seq=${result.sequence} time=%.1fms [${result.tier}]".format(result.rttMs)
            is PingProbeResult.Timeout -> "seq=${result.sequence} timeout [${result.tier}]"
            is PingProbeResult.Error -> "seq=${result.sequence} error: ${result.message}"
        }
    Text(text = text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
}
