package dev.enthusiastdev.netinspector.ui.screens.devices

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.enthusiastdev.netinspector.R
import dev.enthusiastdev.netinspector.core.designsystem.graph.NetworkMapGraph
import dev.enthusiastdev.netinspector.core.model.lan.Host
import dev.enthusiastdev.netinspector.core.model.lan.HostConfidence
import dev.enthusiastdev.netinspector.core.model.lan.networkHygieneScore
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
    onThroughputHost: (String) -> Unit,
    onSortOrderChange: (DevicesSortOrder) -> Unit,
    onToggleConfidenceFilter: (HostConfidence) -> Unit,
    onSetNickname: (String, String) -> Unit,
    onSetKnownDevice: (String, Boolean) -> Unit,
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
                onThroughputHost,
                onSortOrderChange,
                onToggleConfidenceFilter,
                onSetNickname,
                onSetKnownDevice,
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

/** List vs [NetworkMapGraph] - the map has no independent nav destination of its own (design
 * idea #10 wants it to "tap-through to the existing device detail screen," not a new one), so it
 * lives as an alternate body for the very same list pane below rather than the Wi-Fi graph's
 * approach of breaking out of the pane scaffold entirely (design §11.2) - that would have lost
 * the detail pane a map tap needs to open into. */
internal enum class DevicesViewMode { LIST, MAP }

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
    onThroughputHost: (String) -> Unit,
    onSortOrderChange: (DevicesSortOrder) -> Unit,
    onToggleConfidenceFilter: (HostConfidence) -> Unit,
    onSetNickname: (String, String) -> Unit,
    onSetKnownDevice: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // design §11.4 - the ack dialog gates the act of starting a sweep, not opening the screen,
    // so it only appears once the user actually taps Scan while unacknowledged.
    var showAcknowledgement by remember { mutableStateOf(false) }
    // rememberSaveable - a plain `remember` here loses the user's chosen view on rotation
    // (Activity recreation discards non-saveable Compose state, unlike ViewModel state).
    var viewMode by rememberSaveable { mutableStateOf(DevicesViewMode.LIST) }

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
                    viewMode = viewMode,
                    onViewModeChange = { viewMode = it },
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
                    DevicesDetailPane(
                        address,
                        state.hosts,
                        onPingHost,
                        onTracerouteHost,
                        onPortScanHost,
                        onThroughputHost,
                        onSetNickname,
                        onSetKnownDevice,
                    )
                }
            }
        },
    )

    DevicesContentDialogs(
        showAcknowledgement = showAcknowledgement,
        onAcknowledge = {
            showAcknowledgement = false
            onAcknowledgeAndScan()
        },
        onDismissAcknowledgement = { showAcknowledgement = false },
        pendingConfirmationHostCount = state.pendingConfirmationHostCount,
        onConfirmShortPrefixScan = onConfirmShortPrefixScan,
        onDismissConfirmation = onDismissConfirmation,
    )
}

/** Split out of [DevicesContent] purely to keep that function's length in check - both dialogs
 * are independent, gated by their own state, and have no relationship to each other. */
@Composable
private fun DevicesContentDialogs(
    showAcknowledgement: Boolean,
    onAcknowledge: () -> Unit,
    onDismissAcknowledgement: () -> Unit,
    pendingConfirmationHostCount: Long?,
    onConfirmShortPrefixScan: () -> Unit,
    onDismissConfirmation: () -> Unit,
) {
    if (showAcknowledgement) {
        FirstRunAcknowledgementDialog(
            onAcknowledge = onAcknowledge,
            onDismiss = onDismissAcknowledgement,
        )
    }
    pendingConfirmationHostCount?.let { hostCount ->
        ShortPrefixConfirmationDialog(
            hostCount = hostCount,
            onConfirm = onConfirmShortPrefixScan,
            onDismiss = onDismissConfirmation,
        )
    }
}

/** Past this much scroll the controls block folds into its compact row. Small enough that the
 * fold happens as soon as the user is clearly reading the list, big enough that a stray pixel of
 * overscroll doesn't set it off. */
private val COLLAPSE_THRESHOLD = 16.dp

/** The header/controls block is fixed-height at the top; the body below it fills whatever
 * space remains. Map mode needs that - a `LazyColumn` sized to its content (the original shape
 * of this pane) left the map stuck at whatever fixed height it asked for, wasting the rest of
 * the pane instead of giving a dense host set the room [NetworkMapGraph] can actually use. */
@Composable
private fun DevicesListPane(
    state: DevicesUiState.Content,
    viewMode: DevicesViewMode,
    onViewModeChange: (DevicesViewMode) -> Unit,
    onScan: () -> Unit,
    onCancel: () -> Unit,
    onHostClick: (String) -> Unit,
    onSortOrderChange: (DevicesSortOrder) -> Unit,
    onToggleConfidenceFilter: (HostConfidence) -> Unit,
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var showHygieneDetails by remember { mutableStateOf(false) }
    val thresholdPx = with(LocalDensity.current) { COLLAPSE_THRESHOLD.roundToPx() }
    // Map mode has no list to scroll, so its controls never collapse - there is no gesture that
    // would bring them back.
    val isCollapsed by
        remember(viewMode, thresholdPx) {
            derivedStateOf {
                viewMode == DevicesViewMode.LIST &&
                    (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > thresholdPx)
            }
        }
    // docs/ideas.md #1 - meaningless (always "100, Excellent") until at least one
    // host has been through the extended port probe, same gate DevicesDetailCards uses per-host,
    // so this doesn't misrepresent a network nobody has scanned ports on yet. Computed over the
    // unfiltered host list - see DevicesNetworkHygieneCard.
    val hygiene =
        remember(state.allHosts) {
            state.allHosts.takeIf { hosts -> hosts.any { it.openPorts.isNotEmpty() } }?.let(::networkHygieneScore)
        }

    Column(modifier = Modifier.fillMaxSize()) {
        DevicesPaneHeader(
            state = state,
            isCollapsed = isCollapsed,
            controlsState =
                DevicesControlsState(
                    viewMode = viewMode,
                    sortOrder = state.sortOrder,
                    confidenceFilter = state.confidenceFilter,
                    hygiene = hygiene,
                ),
            controlsActions =
                DevicesControlsActions(
                    onViewModeChange = onViewModeChange,
                    onSortOrderChange = onSortOrderChange,
                    onToggleConfidence = onToggleConfidenceFilter,
                    onShowHygieneDetails = { showHygieneDetails = true },
                    onExpandRequested = { coroutineScope.launch { listState.animateScrollToItem(0) } },
                ),
            onScan = onScan,
            onCancel = onCancel,
        )
        DevicesBody(state, viewMode, listState, onHostClick)
    }

    hygiene?.takeIf { showHygieneDetails }?.let { score ->
        NetworkHygieneDetailsDialog(
            score = score,
            onHostClick = { address ->
                showHygieneDetails = false
                onHostClick(address)
            },
            onDismiss = { showHygieneDetails = false },
        )
    }
}

/** Title, scan controls and the collapsible controls block - the fixed part of the pane, above
 * whichever body [DevicesBody] renders. */
@Composable
private fun DevicesPaneHeader(
    state: DevicesUiState.Content,
    isCollapsed: Boolean,
    controlsState: DevicesControlsState,
    controlsActions: DevicesControlsActions,
    onScan: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.destination_devices),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        DevicesHeader(
            hostCount = state.hosts.size,
            progress = state.progress,
            isConnected = state.isConnected,
            onScan = onScan,
            onCancel = onCancel,
        )
        DevicesControls(isCollapsed = isCollapsed, state = controlsState, actions = controlsActions)
    }
}

@Composable
private fun ColumnScope.DevicesBody(
    state: DevicesUiState.Content,
    viewMode: DevicesViewMode,
    listState: LazyListState,
    onHostClick: (String) -> Unit,
) {
    when {
        state.hosts.isEmpty() && !state.progress.isRunning ->
            Text(
                emptyListMessage(state),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        viewMode == DevicesViewMode.MAP ->
            DevicesNetworkMap(
                hosts = state.hosts,
                onHostClick = onHostClick,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        else ->
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.hosts, key = { it.address.addressString }) { host ->
                    HostCard(host, onClick = { onHostClick(host.address.addressString) })
                }
            }
    }
}

/** An empty list after a sweep that *did* find hosts means the confidence filter hid them all -
 * saying "no devices found" there would be plainly wrong, and leaves the user with no hint that
 * the filter chips are the way out. */
private fun emptyListMessage(state: DevicesUiState.Content): String =
    if (state.allHosts.isEmpty()) {
        "No devices found yet. Tap Scan to discover hosts on this network."
    } else {
        "${state.allHosts.size} devices found, but none match the current filters."
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DevicesViewModeToggle(
    viewMode: DevicesViewMode,
    onViewModeChange: (DevicesViewMode) -> Unit,
) {
    SingleChoiceSegmentedButtonRow {
        DevicesViewMode.entries.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = mode == viewMode,
                onClick = { onViewModeChange(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = DevicesViewMode.entries.size),
            ) {
                Text(if (mode == DevicesViewMode.LIST) "List" else "Map")
            }
        }
    }
}

/** design idea #10 - "framed honestly as a logical map, not physical topology": the caption
 * under the graph says so explicitly rather than leaving a hub-and-spoke drawing to imply real
 * switch wiring it has no way to know. */
@Composable
private fun DevicesNetworkMap(
    hosts: List<Host>,
    onHostClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mapData = remember(hosts) { hosts.toNetworkMapData() }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        NetworkMapGraph(
            hub = mapData.hub,
            spokes = mapData.spokes,
            onNodeClick = onHostClick,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        Text(
            "Logical view based on discovered hosts - not physical wiring topology. Devices are " +
                "spaced to stay readable, so a large network runs past the edges: pinch to zoom out " +
                "and fit it, drag to pan.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
}
