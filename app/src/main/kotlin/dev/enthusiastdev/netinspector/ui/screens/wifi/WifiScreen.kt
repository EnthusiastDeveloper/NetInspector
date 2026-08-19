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
                    onApClick = { bssid ->
                        coroutineScope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, bssid) }
                    },
                )
            }
        },
        detailPane = {
            AnimatedPane {
                navigator.currentDestination?.contentKey?.let { bssid ->
                    WifiDetailPane(bssid, state.accessPoints, informationElementsFor)
                }
            }
        },
    )
}

private enum class WifiViewMode { LIST, GRAPH }

@Composable
private fun WifiListPane(
    state: WifiUiState.Content,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onWifiAccessChanged: () -> Unit,
    onApClick: (String) -> Unit,
) {
    var viewMode by remember { mutableStateOf(WifiViewMode.LIST) }

    // design §11.2 - re-derived locally (rather than read from the app-root
    // `LocalDevicePosture`) because the hinge bounds it carries are relative to whichever
    // composable last translated them; this pane sits well below the root, behind nav-suite
    // chrome and content insets, so it re-translates from its own position instead.
    val rawPosture by rememberDevicePosture()
    var containerCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val posture =
        remember(rawPosture, containerCoordinates) {
            containerCoordinates?.let { rawPosture.translatedTo(it) } ?: DevicePosture.Normal
        }

    Column(
        modifier = Modifier.fillMaxSize().onGloballyPositioned { containerCoordinates = it },
    ) {
        val tabletopPosture = posture as? DevicePosture.Tabletop
        if (viewMode == WifiViewMode.GRAPH && tabletopPosture != null) {
            WifiGraphTabletopContent(
                state = state,
                hingeBounds = tabletopPosture.hingeBounds,
                viewMode = viewMode,
                onViewModeChange = { viewMode = it },
                onWifiAccessChanged = onWifiAccessChanged,
            )
        } else {
            WifiListOrGraphScrollContent(
                state = state,
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                onWifiAccessChanged = onWifiAccessChanged,
                onApClick = onApClick,
                viewMode = viewMode,
                onViewModeChange = { viewMode = it },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WifiListOrGraphScrollContent(
    state: WifiUiState.Content,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onWifiAccessChanged: () -> Unit,
    onApClick: (String) -> Unit,
    viewMode: WifiViewMode,
    onViewModeChange: (WifiViewMode) -> Unit,
) {
    var sortOrder by remember { mutableStateOf(WifiSortOrder.SIGNAL) }
    var bandFilter by remember { mutableStateOf(emptySet<Band>()) }
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
            item {
                WifiHeader(apCount = state.accessPoints.size, lastUpdated = state.lastUpdated, budget = state.budget)
            }
            if (state.wifiAccess != WifiAccessState.GRANTED) {
                item { WifiLocationAccessCard(state.wifiAccess, onWifiAccessChanged) }
            }
            item { WifiViewModeToggle(viewMode, onViewModeChange) }
            when (viewMode) {
                WifiViewMode.LIST -> {
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
                WifiViewMode.GRAPH -> {
                    item { WifiGraphView(state.accessPoints, state.sampleCount) }
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
    var selectedBand by remember { mutableStateOf(Band.GHZ_5) }
    var highlightedBssid by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WifiHeader(apCount = state.accessPoints.size, lastUpdated = state.lastUpdated, budget = state.budget)
            if (state.wifiAccess != WifiAccessState.GRANTED) {
                WifiLocationAccessCard(state.wifiAccess, onWifiAccessChanged)
            }
            WifiViewModeToggle(viewMode, onViewModeChange)
        }

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
