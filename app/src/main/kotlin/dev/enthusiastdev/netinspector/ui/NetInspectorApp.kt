package dev.enthusiastdev.netinspector.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.enthusiastdev.netinspector.R
import dev.enthusiastdev.netinspector.core.designsystem.adaptive.DevicePosture
import dev.enthusiastdev.netinspector.core.designsystem.adaptive.LocalDevicePosture
import dev.enthusiastdev.netinspector.core.designsystem.adaptive.rememberDevicePosture
import dev.enthusiastdev.netinspector.core.designsystem.adaptive.translatedTo
import dev.enthusiastdev.netinspector.ui.navigation.ConnectionRoute
import dev.enthusiastdev.netinspector.ui.navigation.DevicesRoute
import dev.enthusiastdev.netinspector.ui.navigation.PingToolRoute
import dev.enthusiastdev.netinspector.ui.navigation.ToolsRoute
import dev.enthusiastdev.netinspector.ui.navigation.WifiRoute
import dev.enthusiastdev.netinspector.ui.navigation.topLevelDestinations
import dev.enthusiastdev.netinspector.ui.screens.PlaceholderScreen
import dev.enthusiastdev.netinspector.ui.screens.connection.ConnectionScreen
import dev.enthusiastdev.netinspector.ui.screens.connection.ConnectionViewModel
import dev.enthusiastdev.netinspector.ui.screens.tools.ToolsScreen
import dev.enthusiastdev.netinspector.ui.screens.tools.ping.PingRoute
import dev.enthusiastdev.netinspector.ui.screens.wifi.WifiScreen
import dev.enthusiastdev.netinspector.ui.screens.wifi.WifiViewModel

@Composable
fun NetInspectorApp() {
    val rawPosture by rememberDevicePosture()
    var rootCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val devicePosture =
        remember(rawPosture, rootCoordinates) {
            rootCoordinates?.let { rawPosture.translatedTo(it) } ?: DevicePosture.Normal
        }

    CompositionLocalProvider(LocalDevicePosture provides devicePosture) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { rootCoordinates = it },
        ) {
            val navController = rememberNavController()
            val currentDestination = navController.currentBackStackEntryAsState().value?.destination

            NavigationSuiteScaffold(
                navigationSuiteItems = {
                    topLevelDestinations.forEach { destination ->
                        item(
                            selected = destination.isSelected(currentDestination),
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(stringResource(destination.labelRes)) },
                        )
                    }
                },
            ) {
                NavHost(navController = navController, startDestination = ConnectionRoute) {
                    composable<ConnectionRoute> { ConnectionDestination() }
                    composable<WifiRoute> { WifiDestination() }
                    composable<DevicesRoute> {
                        PlaceholderScreen(stringResource(R.string.destination_devices))
                    }
                    composable<ToolsRoute> {
                        ToolsScreen(onNavigateToPing = { navController.navigate(PingToolRoute) })
                    }
                    composable<PingToolRoute> { PingRoute() }
                }
            }
        }
    }
}

@Composable
private fun ConnectionDestination() {
    val connectionViewModel: ConnectionViewModel = hiltViewModel()
    val connectionUiState by connectionViewModel.uiState.collectAsState()
    // Granting location access via system Settings, then returning here, doesn't fire any
    // callback the ViewModel observes - re-check on resume.
    LifecycleResumeEffect(Unit) {
        connectionViewModel.refreshLocationAccess()
        onPauseOrDispose {}
    }
    ConnectionScreen(
        uiState = connectionUiState,
        onLocationAccessChanged = connectionViewModel::refreshLocationAccess,
    )
}

@Composable
private fun WifiDestination() {
    val wifiViewModel: WifiViewModel = hiltViewModel()
    val wifiUiState by wifiViewModel.uiState.collectAsState()
    val isRefreshing by wifiViewModel.isRefreshing.collectAsState()
    // Covers first entry (design §6.1: "one [active scan] on screen entry") and re-arming
    // after a permission grant, which fires no callback of its own to observe.
    LifecycleResumeEffect(Unit) {
        wifiViewModel.onResumed()
        onPauseOrDispose {}
    }
    WifiScreen(
        uiState = wifiUiState,
        isRefreshing = isRefreshing,
        onRefresh = wifiViewModel::onRefresh,
        onWifiAccessChanged = wifiViewModel::onResumed,
        informationElementsFor = wifiViewModel::informationElements,
    )
}
