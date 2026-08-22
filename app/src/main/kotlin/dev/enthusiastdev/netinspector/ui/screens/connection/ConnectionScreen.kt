package dev.enthusiastdev.netinspector.ui.screens.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.enthusiastdev.netinspector.R

@Composable
fun ConnectionScreen(
    uiState: ConnectionUiState,
    monitoringState: MonitoringUiState,
    modifier: Modifier = Modifier,
    onLocationAccessChanged: () -> Unit = {},
    onStartMonitoring: () -> Unit = {},
    onStopMonitoring: () -> Unit = {},
    onNotificationAccessChanged: () -> Unit = {},
    onDismissMonitoring: () -> Unit = {},
) {
    when (uiState) {
        ConnectionUiState.Loading -> CenteredMessage("Loading…", modifier)
        ConnectionUiState.Disconnected -> CenteredMessage("Not connected to Wi-Fi", modifier)
        is ConnectionUiState.Connected ->
            ConnectedContent(
                uiState,
                monitoringState,
                modifier,
                onLocationAccessChanged,
                onStartMonitoring,
                onStopMonitoring,
                onNotificationAccessChanged,
                onDismissMonitoring,
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
private fun ConnectedContent(
    state: ConnectionUiState.Connected,
    monitoringState: MonitoringUiState,
    modifier: Modifier = Modifier,
    onLocationAccessChanged: () -> Unit = {},
    onStartMonitoring: () -> Unit = {},
    onStopMonitoring: () -> Unit = {},
    onNotificationAccessChanged: () -> Unit = {},
    onDismissMonitoring: () -> Unit = {},
) {
    val snapshot = state.snapshot

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                stringResource(R.string.destination_connection),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item { ConnectionHeader(snapshot, state.locationAccess, state.rssiDisplayUnit) }
        if (state.locationAccess != LocationAccessState.GRANTED) {
            item { LocationAccessCard(state.locationAccess, onLocationAccessChanged) }
        }
        item { StatusBadges(snapshot) }
        item { RadioSection(snapshot) }
        if (!monitoringState.isDismissed) {
            item {
                MonitoringCard(
                    monitoringState,
                    onStartMonitoring,
                    onStopMonitoring,
                    onNotificationAccessChanged,
                    onDismissMonitoring,
                )
            }
        }
        item { Ipv4Section(snapshot) }
        if (snapshot.ipv6.isNotEmpty()) item { Ipv6Section(snapshot) }
        if (snapshot.dnsServers.isNotEmpty()) item { DnsSection(snapshot) }
    }
}
