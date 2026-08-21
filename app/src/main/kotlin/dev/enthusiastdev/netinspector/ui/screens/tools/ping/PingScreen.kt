package dev.enthusiastdev.netinspector.ui.screens.tools.ping

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.enthusiastdev.netinspector.core.designsystem.adaptive.DevicePosture
import dev.enthusiastdev.netinspector.core.designsystem.adaptive.TabletopSplitLayout
import dev.enthusiastdev.netinspector.core.designsystem.adaptive.rememberDevicePosture
import dev.enthusiastdev.netinspector.core.designsystem.adaptive.translatedTo
import dev.enthusiastdev.netinspector.core.designsystem.chart.RollingLineChart
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
        onLoopModeChange = viewModel::setLoopMode,
        onStart = viewModel::start,
        onStop = viewModel::stop,
        modifier = modifier,
    )
}

/** design §11.2 - re-derives posture locally, same reasoning as `WifiScreen`'s graph view and
 * `TracerouteScreen`: this composable sits below nav-suite chrome the app-root translation
 * doesn't account for. */
@Composable
fun PingScreen(
    uiState: PingUiState,
    onTargetChange: (String) -> Unit,
    onLoopModeChange: (Boolean) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rawPosture by rememberDevicePosture()
    var containerCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val posture =
        remember(rawPosture, containerCoordinates) {
            containerCoordinates?.let { rawPosture.translatedTo(it) } ?: DevicePosture.Normal
        }

    Box(modifier = modifier.fillMaxSize().onGloballyPositioned { containerCoordinates = it }) {
        val tabletopPosture = posture as? DevicePosture.Tabletop
        if (tabletopPosture != null) {
            TabletopSplitLayout(
                hingeBounds = tabletopPosture.hingeBounds,
                upper = { ResultLog(uiState, modifier = Modifier.fillMaxSize()) },
                lower = {
                    PingControls(uiState, onTargetChange, onLoopModeChange, onStart, onStop, Modifier.fillMaxSize())
                },
            )
        } else {
            // Capped wider than the shared 600.dp reading-width convention (WifiScreen,
            // TracerouteScreen): a live chart benefits from horizontal resolution the way a
            // block of text doesn't, and the previous 600.dp cap left a lot of unused width on
            // landscape/tablet screens specifically for this screen's graph.
            Column(modifier = Modifier.align(Alignment.TopCenter).fillMaxHeight().widthIn(max = 900.dp)) {
                PingControls(uiState, onTargetChange, onLoopModeChange, onStart, onStop)
                ResultLog(uiState, modifier = Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}

@Composable
private fun PingControls(
    uiState: PingUiState,
    onTargetChange: (String) -> Unit,
    onLoopModeChange: (Boolean) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        // One row rather than a separate mode-toggle row below: the loop-mode control used to
        // be its own FilterChip row, which was extra height a short landscape screen doesn't
        // have to spare (reported on-device: the chart didn't fit under it). A Switch is a
        // fraction of a FilterChip's touch-target height and reads fine inline.
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
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (uiState.isLoopMode) "Continuous" else "Fixed count",
                    style = MaterialTheme.typography.labelSmall,
                )
                Switch(
                    checked = uiState.isLoopMode,
                    onCheckedChange = onLoopModeChange,
                    enabled = !uiState.isRunning,
                )
            }
            Button(onClick = if (uiState.isRunning) onStop else onStart) {
                Text(if (uiState.isRunning) "Stop" else "Ping")
            }
        }
        uiState.errorMessage?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}

@Composable
private fun ResultLog(
    uiState: PingUiState,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Continuous mode only: a fixed run's handful of points isn't really a "trend," and
        // gating it here (rather than on sample count alone) keeps a stale chart from a
        // previous fixed run showing once the mode switch flips without a new run started yet.
        if (uiState.isLoopMode && uiState.rttSamples.size >= 2) {
            item {
                val maxSample = uiState.rttSamples.max()
                InfoCard(title = "RTT trend") {
                    RollingLineChart(
                        samples = uiState.rttSamples,
                        minValue = 0f,
                        maxValue = (maxSample * 1.2f).coerceAtLeast(20f),
                        contentDescription =
                            "Round-trip time trend over the last ${uiState.rttSamples.size} probes, " +
                                "latest %.1f ms, up to %.1f ms".format(uiState.rttSamples.last(), maxSample),
                        valueLabel = { "${it.toInt()} ms" },
                    )
                }
            }
        }
        // Ahead of the probe log rather than after: in continuous mode that log can grow to 60
        // rows, which buried the summary card below a long scroll (reported on-device).
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
        items(uiState.results) { result -> ProbeResultRow(result) }
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
