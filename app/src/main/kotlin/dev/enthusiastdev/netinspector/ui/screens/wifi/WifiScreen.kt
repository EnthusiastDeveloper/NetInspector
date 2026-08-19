package dev.enthusiastdev.netinspector.ui.screens.wifi

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import dev.enthusiastdev.netinspector.core.designsystem.adaptive.DevicePosture
import dev.enthusiastdev.netinspector.core.designsystem.adaptive.TabletopSplitLayout
import dev.enthusiastdev.netinspector.core.designsystem.adaptive.rememberDevicePosture
import dev.enthusiastdev.netinspector.core.designsystem.adaptive.translatedTo
import dev.enthusiastdev.netinspector.core.model.wifi.Band
import dev.enthusiastdev.netinspector.core.model.wifi.InformationElementSummary
import kotlinx.coroutines.launch

@Composable
fun WifiScreen(
    uiState: WifiUiState,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onWifiAccessChanged: () -> Unit,
    informationElementsFor: (String) -> InformationElementSummary,
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        WifiUiState.Loading -> CenteredMessage("Loading…", modifier)
        is WifiUiState.Content ->
            WifiContent(uiState, isRefreshing, onRefresh, onWifiAccessChanged, informationElementsFor, modifier)
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

internal enum class WifiViewMode { LIST, GRAPH }

/**
 * design §11.1/§11.2 - List mode is the genuine list-detail pair (AP list ↔ AP detail) and
 * goes through [NavigableListDetailPaneScaffold] so both panes share the window per the size
 * class. Graph mode has no detail counterpart, so it is *not* nested inside that scaffold's list
 * pane: doing so confined it to the pane's fraction of the width, which read as the graph being
 * "pinned" to a partial-width column once a rotation or window resize brought the two-pane
 * layout into play. It renders as its own full-width branch instead - exactly the width design
 * §11.2 wants the graph to benefit from in landscape.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun WifiContent(
    state: WifiUiState.Content,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onWifiAccessChanged: () -> Unit,
    informationElementsFor: (String) -> InformationElementSummary,
    modifier: Modifier = Modifier,
) {
    // rememberSaveable - a plain `remember` here loses the user's chosen view on rotation
    // (Activity recreation discards non-saveable Compose state, unlike ViewModel state).
    var viewMode by rememberSaveable { mutableStateOf(WifiViewMode.LIST) }

    if (viewMode == WifiViewMode.GRAPH) {
        WifiGraphScreen(
            state = state,
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            onWifiAccessChanged = onWifiAccessChanged,
            viewMode = viewMode,
            onViewModeChange = { viewMode = it },
            modifier = modifier,
        )
        return
    }

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
                WifiListPane(
                    state = state,
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    onWifiAccessChanged = onWifiAccessChanged,
                    viewMode = viewMode,
                    onViewModeChange = { viewMode = it },
                    onApClick = { bssid ->
                        coroutineScope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, bssid) }
                    },
                )
            }
        },
        detailPane = {
            AnimatedPane {
                navigator.currentDestination?.contentKey?.let { bssid ->
                    WifiDetailPane(bssid, state.accessPoints, informationElementsFor, state.rssiDisplayUnit)
                }
            }
        },
    )
}

/** The header block shared by every Wi-Fi layout: AP count / freshness, the location-access
 * card when needed, and the List/Graph toggle. */
@Composable
private fun WifiHeaderBlock(
    state: WifiUiState.Content,
    viewMode: WifiViewMode,
    onViewModeChange: (WifiViewMode) -> Unit,
    onWifiAccessChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        WifiHeader(apCount = state.accessPoints.size, lastUpdated = state.lastUpdated, budget = state.budget)
        if (state.wifiAccess != WifiAccessState.GRANTED) {
            WifiLocationAccessCard(state.wifiAccess, onWifiAccessChanged)
        }
        WifiViewModeToggle(viewMode, onViewModeChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WifiListPane(
    state: WifiUiState.Content,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onWifiAccessChanged: () -> Unit,
    viewMode: WifiViewMode,
    onViewModeChange: (WifiViewMode) -> Unit,
    onApClick: (String) -> Unit,
) {
    var sortOrder by rememberSaveable { mutableStateOf(WifiSortOrder.SIGNAL) }
    var bandFilter by rememberSaveable { mutableStateOf(emptySet<Band>()) }
    val groups =
        remember(state.accessPoints, sortOrder, bandFilter) {
            state.accessPoints.toGroups(sortOrder, bandFilter)
        }

    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { WifiHeaderBlock(state, viewMode, onViewModeChange, onWifiAccessChanged) }
            item {
                WifiFilterSortBar(
                    sortOrder = sortOrder,
                    onSortOrderChange = { sortOrder = it },
                    bandFilter = bandFilter,
                    onBandFilterChange = { bandFilter = it },
                )
            }
            if (groups.isEmpty()) {
                item { Text("No networks found yet", style = MaterialTheme.typography.bodyMedium) }
            } else {
                wifiGroupItems(groups, onApClick)
            }
        }
    }
}

/**
 * design §11.2 - the Graph view mode's own top-level layout, full window width. Re-derives fold
 * posture locally (rather than reading the app-root `LocalDevicePosture`) because this
 * composable sits below nav-suite chrome and content insets that the root's translation doesn't
 * account for; re-translating from this composable's own position keeps the hinge bounds
 * accurate regardless of what sits above it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WifiGraphScreen(
    state: WifiUiState.Content,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onWifiAccessChanged: () -> Unit,
    viewMode: WifiViewMode,
    onViewModeChange: (WifiViewMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rawPosture by rememberDevicePosture()
    var containerCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val posture =
        remember(rawPosture, containerCoordinates) {
            containerCoordinates?.let { rawPosture.translatedTo(it) } ?: DevicePosture.Normal
        }

    Column(modifier = modifier.fillMaxSize().onGloballyPositioned { containerCoordinates = it }) {
        val tabletopPosture = posture as? DevicePosture.Tabletop
        if (tabletopPosture != null) {
            WifiGraphTabletopContent(
                state = state,
                hingeBounds = tabletopPosture.hingeBounds,
                viewMode = viewMode,
                onViewModeChange = onViewModeChange,
                onWifiAccessChanged = onWifiAccessChanged,
            )
        } else {
            PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    WifiHeaderBlock(
                        state,
                        viewMode,
                        onViewModeChange,
                        onWifiAccessChanged,
                        modifier = Modifier.padding(16.dp),
                    )
                    WifiGraphView(state.accessPoints, state.sampleCount)
                }
            }
        }
    }
}

/** design §11.2 - the channel graph's flagship tabletop layout: the graph fills the upper
 * display, band tabs / legend / recommendation card fill the lower one, nothing is drawn
 * across the hinge. Header and the List/Graph toggle stay fixed above both, since they aren't
 * part of the continuously-updating output this pattern is meant to separate from controls. */
@Composable
private fun WifiGraphTabletopContent(
    state: WifiUiState.Content,
    hingeBounds: Rect,
    viewMode: WifiViewMode,
    onViewModeChange: (WifiViewMode) -> Unit,
    onWifiAccessChanged: () -> Unit,
) {
    var selectedBand by rememberSaveable { mutableStateOf(Band.GHZ_5) }
    var highlightedBssid by rememberSaveable { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        WifiHeaderBlock(state, viewMode, onViewModeChange, onWifiAccessChanged, modifier = Modifier.padding(16.dp))

        TabletopSplitLayout(
            hingeBounds = hingeBounds,
            modifier = Modifier.weight(1f),
            upper = {
                WifiGraphCanvas(
                    accessPoints = state.accessPoints,
                    band = selectedBand,
                    highlightedBssid = highlightedBssid,
                    onCurveTap = { bssid -> highlightedBssid = if (highlightedBssid == bssid) null else bssid },
                    modifier = Modifier.fillMaxSize(),
                )
            },
            lower = {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    WifiBandTabs(selectedBand) { band ->
                        selectedBand = band
                        highlightedBssid = null
                    }
                    WifiGraphLegend()
                    WifiChannelRecommendationCard(
                        band = selectedBand,
                        accessPoints = state.accessPoints,
                        sampleCount = state.sampleCount,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WifiViewModeToggle(
    viewMode: WifiViewMode,
    onViewModeChange: (WifiViewMode) -> Unit,
) {
    SingleChoiceSegmentedButtonRow {
        WifiViewMode.entries.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = mode == viewMode,
                onClick = { onViewModeChange(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = WifiViewMode.entries.size),
            ) {
                Text(if (mode == WifiViewMode.LIST) "List" else "Graph")
            }
        }
    }
}
