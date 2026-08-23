package dev.enthusiastdev.netinspector.ui.screens.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.enthusiastdev.netinspector.R

/**
 * The connection's read-only detail sections, under a fixed header that carries the screen title
 * and the continuous-monitoring toggle.
 *
 * The header sits outside the scrolling list deliberately: monitoring is an action the user takes,
 * not a fact about the network, and it stays reachable (and its state visible) no matter how far
 * down the IPv6/DNS sections the list is scrolled - and whether or not this device is connected
 * to anything at all.
 */
@Composable
fun ConnectionScreen(
    uiState: ConnectionUiState,
    monitoringState: MonitoringUiState,
    modifier: Modifier = Modifier,
    onLocationAccessChanged: () -> Unit = {},
    onStartMonitoring: () -> Unit = {},
    onStopMonitoring: () -> Unit = {},
    onNotificationAccessChanged: () -> Unit = {},
) {
    var showMonitoringDetails by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        ConnectionTopBar(
            monitoringState = monitoringState,
            // Turning the switch on without notification access would start a service the OS then
            // hides - the sheet is opened instead, where the permission can actually be granted.
            onToggleMonitoring = { enabled ->
                when {
                    !enabled -> onStopMonitoring()
                    monitoringState.notificationAccess == NotificationAccessState.GRANTED -> onStartMonitoring()
                    else -> showMonitoringDetails = true
                }
            },
            onOpenMonitoringDetails = { showMonitoringDetails = true },
        )
        when (uiState) {
            ConnectionUiState.Loading -> CenteredMessage("Loading…")
            ConnectionUiState.Disconnected -> CenteredMessage("Not connected to Wi-Fi")
            is ConnectionUiState.Connected -> ConnectedContent(uiState, onLocationAccessChanged)
        }
    }

    if (showMonitoringDetails) {
        MonitoringDetailsSheet(
            state = monitoringState,
            onStart = onStartMonitoring,
            onStop = onStopMonitoring,
            onNotificationAccessChanged = onNotificationAccessChanged,
            onDismiss = { showMonitoringDetails = false },
        )
    }
}

@Composable
private fun ConnectionTopBar(
    monitoringState: MonitoringUiState,
    onToggleMonitoring: (Boolean) -> Unit,
    onOpenMonitoringDetails: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.destination_connection),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        MonitoringToggleBar(
            state = monitoringState,
            onToggle = onToggleMonitoring,
            onOpenDetails = onOpenMonitoringDetails,
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
    onLocationAccessChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snapshot = state.snapshot

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ConnectionHeader(snapshot, state.locationAccess, state.rssiDisplayUnit) }
        if (state.locationAccess != LocationAccessState.GRANTED) {
            item { LocationAccessCard(state.locationAccess, onLocationAccessChanged) }
        }
        item { ConnectivitySection(snapshot) }
        item { RadioSection(snapshot, state.locationAccess) }
        item { Ipv4Section(snapshot) }
        if (snapshot.ipv6.isNotEmpty()) item { Ipv6Section(snapshot) }
        if (snapshot.dnsServers.isNotEmpty()) item { DnsSection(snapshot) }
    }
}
