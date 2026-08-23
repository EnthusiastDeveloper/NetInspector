package dev.enthusiastdev.netinspector.ui.screens.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.enthusiastdev.netinspector.R
import dev.enthusiastdev.netinspector.core.common.wifi.rssiToQualityPercent
import dev.enthusiastdev.netinspector.core.designsystem.component.InfoCard
import dev.enthusiastdev.netinspector.core.designsystem.gauge.RssiGauge
import dev.enthusiastdev.netinspector.core.model.connection.ConnectionSnapshot
import dev.enthusiastdev.netinspector.core.model.lan.SweepProgress
import dev.enthusiastdev.netinspector.core.model.settings.RssiDisplayUnit

/** design idea #14 - "network health at a glance": Wi-Fi quality, device count and active
 * diagnostics as three tappable summary cards, each opening the tab with the full detail rather
 * than duplicating it here. */
@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    onOpenWifi: () -> Unit,
    onOpenDevices: () -> Unit,
    onOpenTools: () -> Unit,
    onExportCrashReport: () -> Unit,
    onDismissCrashReport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        DashboardUiState.Loading -> CenteredMessage("Loading…", modifier)
        is DashboardUiState.Content ->
            DashboardContent(
                uiState,
                onOpenWifi,
                onOpenDevices,
                onOpenTools,
                onExportCrashReport,
                onDismissCrashReport,
                modifier,
            )
    }
}

@Composable
private fun CenteredMessage(
    text: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun DashboardContent(
    state: DashboardUiState.Content,
    onOpenWifi: () -> Unit,
    onOpenDevices: () -> Unit,
    onOpenTools: () -> Unit,
    onExportCrashReport: () -> Unit,
    onDismissCrashReport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                stringResource(R.string.app_display_name),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (state.pendingCrashReportFilename != null) {
            item { CrashReportBanner(onExport = onExportCrashReport, onDismiss = onDismissCrashReport) }
        }
        item { ConnectionQualityCard(state.connection, state.rssiDisplayUnit, onOpenWifi) }
        item { DeviceCountCard(state.hostCount, onOpenDevices) }
        item { DiagnosticsCard(state.sweepProgress, state.isMonitoringActive, onOpenTools) }
    }
}

/** improvement-ideas.md #21 - "auto-detect the crash on the next app start and suggest to
 * report it." Follows the same dismissible-InfoCard shape as the Connection tab's
 * MonitoringCard: an X in the corner clears the prompt without exporting, an inline action
 * does the real work. Dismissing never deletes the underlying report - it's still reachable
 * later via Settings' "Export crash report." */
@Composable
private fun CrashReportBanner(
    onExport: () -> Unit,
    onDismiss: () -> Unit,
) {
    InfoCard(
        title = "Crash report available",
        trailingContent = {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Dismiss crash report prompt")
            }
        },
    ) {
        Text(
            "NetInspector crashed last time it was used. Export the report to help track down " +
                "what happened - it stays on this device unless you choose to share it.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onExport) { Text("Export crash report") }
    }
}

@Composable
private fun ConnectionQualityCard(
    connection: ConnectionSnapshot?,
    rssiDisplayUnit: RssiDisplayUnit,
    onClick: () -> Unit,
) {
    DashboardCard(onClick = onClick) {
        Text("Wi-Fi quality", style = MaterialTheme.typography.titleMedium)
        val rssi = connection?.rssiDbm
        if (connection == null || rssi == null) {
            Text(
                "Not connected to Wi-Fi",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@DashboardCard
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            RssiGauge(
                rssiDbm = rssi,
                qualityPercent = rssiToQualityPercent(rssi),
                showAsPercent = rssiDisplayUnit == RssiDisplayUnit.PERCENT,
            )
            Text(connection.ssid ?: "Wi-Fi name unavailable", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun DeviceCountCard(
    hostCount: Int,
    onClick: () -> Unit,
) {
    DashboardCard(onClick = onClick) {
        Text("Devices", style = MaterialTheme.typography.titleMedium)
        Text(
            hostCountLabel(hostCount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DiagnosticsCard(
    sweepProgress: SweepProgress,
    isMonitoringActive: Boolean,
    onClick: () -> Unit,
) {
    DashboardCard(onClick = onClick) {
        Text("Active diagnostics", style = MaterialTheme.typography.titleMedium)
        Text(
            diagnosticsStatus(sweepProgress, isMonitoringActive).label(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (sweepProgress.isRunning) {
            val fraction =
                if (sweepProgress.addressesTotal > 0) {
                    sweepProgress.addressesProbed / sweepProgress.addressesTotal.toFloat()
                } else {
                    0f
                }
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun DashboardCard(
    onClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content,
        )
    }
}
