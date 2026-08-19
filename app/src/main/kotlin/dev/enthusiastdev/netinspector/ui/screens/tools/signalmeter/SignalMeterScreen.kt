package dev.enthusiastdev.netinspector.ui.screens.tools.signalmeter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.enthusiastdev.netinspector.core.common.wifi.rssiToQualityPercent
import dev.enthusiastdev.netinspector.core.designsystem.adaptive.DevicePosture
import dev.enthusiastdev.netinspector.core.designsystem.adaptive.TabletopSplitLayout
import dev.enthusiastdev.netinspector.core.designsystem.adaptive.rememberDevicePosture
import dev.enthusiastdev.netinspector.core.designsystem.adaptive.translatedTo
import dev.enthusiastdev.netinspector.core.designsystem.chart.RollingLineChart
import dev.enthusiastdev.netinspector.core.designsystem.component.InfoCard
import dev.enthusiastdev.netinspector.core.designsystem.component.InfoRow

@Composable
fun SignalMeterRoute(
    modifier: Modifier = Modifier,
    viewModel: SignalMeterViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    SignalMeterScreen(uiState = uiState, modifier = modifier)
}

@Composable
fun SignalMeterScreen(
    uiState: SignalMeterUiState,
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
                upper = { Chart(uiState, modifier = Modifier.fillMaxSize().padding(16.dp)) },
                lower = { Readouts(uiState, modifier = Modifier.fillMaxSize()) },
            )
        } else {
            Column(modifier = Modifier.fillMaxSize().widthIn(max = 600.dp)) {
                Chart(uiState, modifier = Modifier.padding(16.dp))
                Readouts(uiState)
            }
        }
    }
}

@Composable
private fun Chart(
    uiState: SignalMeterUiState,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(text = "RSSI, last 60 s", style = MaterialTheme.typography.titleMedium)
        if (uiState.history.size < 2) {
            Text(text = "Collecting samples…", style = MaterialTheme.typography.bodySmall)
        } else {
            val samples = uiState.history.map { it.rssiDbm.toFloat() }
            RollingLineChart(
                samples = samples,
                minValue = -100f,
                maxValue = -30f,
                contentDescription =
                    "RSSI trend over the last 60 seconds, ${samples.size} samples, " +
                        "latest ${samples.last().toInt()} dBm, ranging from ${samples.min().toInt()} " +
                        "to ${samples.max().toInt()} dBm",
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Readouts(
    uiState: SignalMeterUiState,
    modifier: Modifier = Modifier,
) {
    val snapshot = uiState.snapshot
    val rssiDbm = snapshot?.rssiDbm
    Column(modifier = modifier.padding(16.dp)) {
        if (snapshot == null || rssiDbm == null) {
            Text(text = "Not connected", style = MaterialTheme.typography.bodyMedium)
            return@Column
        }
        InfoCard(title = "Signal") {
            InfoRow("RSSI", "$rssiDbm dBm")
            InfoRow("Quality", "${rssiToQualityPercent(rssiDbm)}%")
            snapshot.txLinkSpeedMbps?.let { InfoRow("Tx link speed", "$it Mbps") }
            snapshot.rxLinkSpeedMbps?.let { InfoRow("Rx link speed", "$it Mbps") }
        }
    }
}
