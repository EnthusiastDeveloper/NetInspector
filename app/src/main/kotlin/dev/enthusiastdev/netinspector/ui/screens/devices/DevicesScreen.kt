package dev.enthusiastdev.netinspector.ui.screens.devices

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun DevicesScreen(
    uiState: DevicesUiState,
    onScan: () -> Unit,
    onCancel: () -> Unit,
    onAcknowledgeAndScan: () -> Unit,
    onConfirmShortPrefixScan: () -> Unit,
    onDismissConfirmation: () -> Unit,
    onPingHost: (String) -> Unit,
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
                onPingHost,
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

/** design §3 Phase 6 - "Host list and detail as a single `ListDetailPaneScaffold` destination,"
 * mirroring the Wi-Fi AP list/detail pane (design §11.1). Keyed by the host's dotted-quad
 * address string rather than the [java.net.Inet4Address] itself, matching the Wi-Fi pane's
 * BSSID-string key - a plain data key the navigator can carry across process death. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun DevicesContent(
    state: DevicesUiState.Content,
    onScan: () -> Unit,
    onCancel: () -> Unit,
    onAcknowledgeAndScan: () -> Unit,
    onConfirmShortPrefixScan: () -> Unit,
    onDismissConfirmation: () -> Unit,
    onPingHost: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // design §11.4 - the ack dialog gates the act of starting a sweep, not opening the screen,
    // so it only appears once the user actually taps Scan while unacknowledged.
    var showAcknowledgement by remember { mutableStateOf(false) }

    val navigator = rememberListDetailPaneScaffoldNavigator<String>()
    val coroutineScope = rememberCoroutineScope()
    BackHandler(enabled = navigator.canNavigateBack()) {
        coroutineScope.launch { navigator.navigateBack() }
    }

    NavigableListDetailPaneScaffold(
        navigator = navigator,
        modifier = modifier,
        listPane = {
            AnimatedPane {
                DevicesListPane(
                    state = state,
                    onScan = { if (state.needsAcknowledgement) showAcknowledgement = true else onScan() },
                    onCancel = onCancel,
                    onHostClick = { address ->
                        coroutineScope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, address) }
                    },
                )
            }
        },
        detailPane = {
            AnimatedPane {
                navigator.currentDestination?.contentKey?.let { address ->
                    DevicesDetailPane(address, state.hosts, onPingHost)
                }
            }
        },
    )

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

@Composable
private fun DevicesListPane(
    state: DevicesUiState.Content,
    onScan: () -> Unit,
    onCancel: () -> Unit,
    onHostClick: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            DevicesHeader(
                hostCount = state.hosts.size,
                progress = state.progress,
                isConnected = state.isConnected,
                onScan = onScan,
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
            items(state.hosts, key = { it.address.addressString }) { host ->
                HostCard(host, onClick = { onHostClick(host.address.addressString) })
            }
        }
    }
}
