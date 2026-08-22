package dev.enthusiastdev.netinspector.ui.screens.devices

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.enthusiastdev.netinspector.R
import dev.enthusiastdev.netinspector.core.model.lan.HostConfidence
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
    onTracerouteHost: (String) -> Unit,
    onPortScanHost: (String) -> Unit,
    onSortOrderChange: (DevicesSortOrder) -> Unit,
    onToggleConfidenceFilter: (HostConfidence) -> Unit,
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
                onTracerouteHost,
                onPortScanHost,
                onSortOrderChange,
                onToggleConfidenceFilter,
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
    onTracerouteHost: (String) -> Unit,
    onPortScanHost: (String) -> Unit,
    onSortOrderChange: (DevicesSortOrder) -> Unit,
    onToggleConfidenceFilter: (HostConfidence) -> Unit,
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
                    onSortOrderChange = onSortOrderChange,
                    onToggleConfidenceFilter = onToggleConfidenceFilter,
                )
            }
        },
        detailPane = {
            AnimatedPane {
                navigator.currentDestination?.contentKey?.let { address ->
                    DevicesDetailPane(address, state.hosts, onPingHost, onTracerouteHost, onPortScanHost)
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
    onSortOrderChange: (DevicesSortOrder) -> Unit,
    onToggleConfidenceFilter: (HostConfidence) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                stringResource(R.string.destination_devices),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            DevicesHeader(
                hostCount = state.hosts.size,
                progress = state.progress,
                isConnected = state.isConnected,
                onScan = onScan,
                onCancel = onCancel,
            )
        }
        // docs/improvement-ideas.md #1 - meaningless (always "100, Excellent") until at least
        // one host has been through the extended port probe, same gate DevicesDetailCards
        // uses per-host, so this doesn't misrepresent a network nobody has scanned ports on yet.
        if (state.hosts.any { it.openPorts.isNotEmpty() }) {
            item { DevicesNetworkHygieneCard(state.hosts, onHostClick = onHostClick) }
        }
        item {
            DevicesSortFilterBar(
                sortOrder = state.sortOrder,
                confidenceFilter = state.confidenceFilter,
                onSortOrderChange = onSortOrderChange,
                onToggleConfidence = onToggleConfidenceFilter,
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
