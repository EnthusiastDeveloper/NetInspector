package dev.enthusiastdev.netinspector.ui.screens.tools.traceroute

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import dev.enthusiastdev.netinspector.core.common.icmp.summarizeHop
import dev.enthusiastdev.netinspector.core.designsystem.adaptive.DevicePosture
import dev.enthusiastdev.netinspector.core.designsystem.adaptive.TabletopSplitLayout
import dev.enthusiastdev.netinspector.core.designsystem.adaptive.rememberDevicePosture
import dev.enthusiastdev.netinspector.core.designsystem.adaptive.translatedTo
import dev.enthusiastdev.netinspector.core.model.diagnostics.TracerouteHop
import dev.enthusiastdev.netinspector.core.model.diagnostics.TracerouteTier

@Composable
fun TracerouteRoute(
    modifier: Modifier = Modifier,
    viewModel: TracerouteViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    TracerouteScreen(
        uiState = uiState,
        onTargetChange = viewModel::updateTarget,
        onStart = viewModel::start,
        onStop = viewModel::stop,
        modifier = modifier,
    )
}

/** design §11.2 - re-derives posture locally, same reasoning as `WifiScreen`'s graph view: this
 * composable sits below nav-suite chrome that the app-root translation doesn't account for. */
@Composable
fun TracerouteScreen(
    uiState: TracerouteUiState,
    onTargetChange: (String) -> Unit,
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
                upper = { HopLog(uiState, modifier = Modifier.fillMaxSize()) },
                lower = { TracerouteControls(uiState, onTargetChange, onStart, onStop, Modifier.fillMaxSize()) },
            )
        } else {
            Column(modifier = Modifier.fillMaxSize().widthIn(max = 600.dp)) {
                TracerouteControls(uiState, onTargetChange, onStart, onStop)
                HopLog(uiState, modifier = Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}

@Composable
private fun TracerouteControls(
    uiState: TracerouteUiState,
    onTargetChange: (String) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
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
                Text(if (uiState.isRunning) "Stop" else "Trace")
            }
        }
        if (uiState.tier == TracerouteTier.PING_BINARY) {
            Text(
                text = "Using the ping-binary fallback - hop timing is approximate",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        uiState.errorMessage?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}

@Composable
private fun HopLog(
    uiState: TracerouteUiState,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(uiState.hops) { hop -> HopRow(hop) }
    }
}

@Composable
private fun HopRow(hop: TracerouteHop) {
    val stats = summarizeHop(hop.probes)
    val addressLabel = hop.hostname?.let { "${hop.respondingAddress} ($it)" } ?: hop.respondingAddress ?: "*"
    val timingLabel =
        if (stats.avgMs != null) {
            "min %.1f / avg %.1f / max %.1f ms".format(stats.minMs, stats.avgMs, stats.maxMs)
        } else {
            "* * *"
        }
    Text(
        text = "%2d  %-30s  %s".format(hop.ttl, addressLabel, timingLabel),
        fontFamily = FontFamily.Monospace,
        style = MaterialTheme.typography.bodySmall,
    )
}
