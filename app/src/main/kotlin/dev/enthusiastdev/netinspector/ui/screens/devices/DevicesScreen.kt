package dev.enthusiastdev.netinspector.ui.screens.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DevicesScreen(
    uiState: DevicesUiState,
    onScan: () -> Unit,
    onCancel: () -> Unit,
    onAcknowledgeAndScan: () -> Unit,
    onConfirmShortPrefixScan: () -> Unit,
    onDismissConfirmation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        DevicesUiState.Loading -> CenteredMessage("Loading…", modifier)
        is DevicesUiState.Content ->
            DevicesContent(
                uiState,
                onScan,
                onCancel,
                onAcknowledgeAndScan,
                onConfirmShortPrefixScan,
                onDismissConfirmation,
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
private fun DevicesContent(
    state: DevicesUiState.Content,
    onScan: () -> Unit,
    onCancel: () -> Unit,
    onAcknowledgeAndScan: () -> Unit,
    onConfirmShortPrefixScan: () -> Unit,
    onDismissConfirmation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // design §11.4 - the ack dialog gates the act of starting a sweep, not opening the screen,
    // so it only appears once the user actually taps Scan while unacknowledged.
    var showAcknowledgement by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            DevicesHeader(
                hostCount = state.hosts.size,
                progress = state.progress,
                isConnected = state.isConnected,
                onScan = { if (state.needsAcknowledgement) showAcknowledgement = true else onScan() },
                onCancel = onCancel,
            )
        }
        if (state.hosts.isEmpty() && !state.progress.isRunning) {
            item {
                Text(
                    "No devices found yet. Tap Scan to discover hosts on this network.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            items(state.hosts, key = { it.address.hostAddress }) { host -> HostCard(host) }
        }
    }

    if (showAcknowledgement) {
        FirstRunAcknowledgementDialog(
            onAcknowledge = {
                showAcknowledgement = false
                onAcknowledgeAndScan()
            },
            onDismiss = { showAcknowledgement = false },
        )
    }

    state.pendingConfirmationHostCount?.let { hostCount ->
        ShortPrefixConfirmationDialog(
            hostCount = hostCount,
            onConfirm = onConfirmShortPrefixScan,
            onDismiss = onDismissConfirmation,
        )
    }
}
