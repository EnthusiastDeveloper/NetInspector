package dev.enthusiastdev.netinspector.ui.screens.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ConnectionScreen(
    uiState: ConnectionUiState,
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        ConnectionUiState.Loading -> CenteredMessage("Loading…", modifier)
        ConnectionUiState.Disconnected -> CenteredMessage("Not connected to Wi-Fi", modifier)
        is ConnectionUiState.Connected -> ConnectedContent(uiState, modifier)
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
    modifier: Modifier = Modifier,
) {
    val snapshot = state.snapshot

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ConnectionHeader(snapshot, state.hasScanPermission) }
        item { StatusBadges(snapshot) }
        item { RadioSection(snapshot) }
        item { Ipv4Section(snapshot) }
        if (snapshot.ipv6.isNotEmpty()) item { Ipv6Section(snapshot) }
        if (snapshot.dnsServers.isNotEmpty()) item { DnsSection(snapshot) }
    }
}
