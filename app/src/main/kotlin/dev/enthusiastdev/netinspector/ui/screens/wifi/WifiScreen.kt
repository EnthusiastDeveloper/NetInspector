package dev.enthusiastdev.netinspector.ui.screens.wifi

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WifiListPane(
    state: WifiUiState.Content,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onWifiAccessChanged: () -> Unit,
    onApClick: (String) -> Unit,
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
                item { NearbyWifiAccessCard(state.wifiAccess, onWifiAccessChanged) }
            }
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
