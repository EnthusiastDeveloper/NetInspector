package dev.enthusiastdev.netinspector.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScope
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
import androidx.navigation.NavDestination
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import dev.enthusiastdev.netinspector.core.designsystem.adaptive.DevicePosture
import dev.enthusiastdev.netinspector.core.designsystem.adaptive.LocalDevicePosture
import dev.enthusiastdev.netinspector.core.designsystem.adaptive.rememberDevicePosture
import dev.enthusiastdev.netinspector.core.designsystem.adaptive.translatedTo
import dev.enthusiastdev.netinspector.ui.navigation.ConnectionRoute
import dev.enthusiastdev.netinspector.ui.navigation.DevicesRoute
import dev.enthusiastdev.netinspector.ui.navigation.DiagnosticHistoryToolRoute
import dev.enthusiastdev.netinspector.ui.navigation.DnsToolRoute
import dev.enthusiastdev.netinspector.ui.navigation.HttpInspectorToolRoute
import dev.enthusiastdev.netinspector.ui.navigation.PingToolRoute
import dev.enthusiastdev.netinspector.ui.navigation.PortScannerToolRoute
import dev.enthusiastdev.netinspector.ui.navigation.ScanHistoryToolRoute
import dev.enthusiastdev.netinspector.ui.navigation.SettingsToolRoute
import dev.enthusiastdev.netinspector.ui.navigation.SignalMeterToolRoute
import dev.enthusiastdev.netinspector.ui.navigation.SubnetCalculatorToolRoute
import dev.enthusiastdev.netinspector.ui.navigation.ToolsHomeRoute
import dev.enthusiastdev.netinspector.ui.navigation.ToolsRoute
import dev.enthusiastdev.netinspector.ui.navigation.TracerouteToolRoute
import dev.enthusiastdev.netinspector.ui.navigation.WakeOnLanToolRoute
import dev.enthusiastdev.netinspector.ui.navigation.WhoisToolRoute
import dev.enthusiastdev.netinspector.ui.navigation.WifiRoute
import dev.enthusiastdev.netinspector.ui.navigation.topLevelDestinations
import dev.enthusiastdev.netinspector.ui.screens.connection.ConnectionScreen
import dev.enthusiastdev.netinspector.ui.screens.connection.ConnectionViewModel
import dev.enthusiastdev.netinspector.ui.screens.devices.DevicesScreen
import dev.enthusiastdev.netinspector.ui.screens.devices.DevicesViewModel
import dev.enthusiastdev.netinspector.ui.screens.settings.SettingsRoute
import dev.enthusiastdev.netinspector.ui.screens.tools.Tool
import dev.enthusiastdev.netinspector.ui.screens.tools.ToolsScreen
import dev.enthusiastdev.netinspector.ui.screens.tools.dns.DnsRoute
import dev.enthusiastdev.netinspector.ui.screens.tools.history.DiagnosticHistoryRoute
import dev.enthusiastdev.netinspector.ui.screens.tools.history.ScanHistoryRoute
import dev.enthusiastdev.netinspector.ui.screens.tools.httpinspector.HttpInspectorRoute
import dev.enthusiastdev.netinspector.ui.screens.tools.ping.PingRoute
import dev.enthusiastdev.netinspector.ui.screens.tools.portscanner.PortScannerRoute
import dev.enthusiastdev.netinspector.ui.screens.tools.signalmeter.SignalMeterRoute
import dev.enthusiastdev.netinspector.ui.screens.tools.subnetcalc.SubnetCalculatorRoute
import dev.enthusiastdev.netinspector.ui.screens.tools.traceroute.TracerouteRoute
import dev.enthusiastdev.netinspector.ui.screens.tools.whois.WhoisRoute
import dev.enthusiastdev.netinspector.ui.screens.tools.wol.WakeOnLanRoute
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
                navigationSuiteItems = { navigationItems(navController, currentDestination) },
            ) {
                AppNavHost(navController)
            }
        }
    }
}

private fun NavigationSuiteScope.navigationItems(
    navController: NavHostController,
    currentDestination: NavDestination?,
) {
    topLevelDestinations.forEach { destination ->
        item(
            selected = destination.isSelected(currentDestination),
            onClick = {
                navController.navigate(destination.route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    // The Tools tab always reopens on its own grid rather than restoring
                    // whatever tool was last open - restoring here would resurrect Ping/DNS/etc.
                    // from that tab's saved back stack instead of landing on ToolsHomeRoute.
                    restoreState = destination.route != ToolsRoute
                }
            },
            icon = { Icon(destination.icon, contentDescription = null) },
            label = { Text(stringResource(destination.labelRes)) },
        )
    }
}

@Composable
private fun AppNavHost(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = ConnectionRoute,
        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout)),
    ) {
        composable<ConnectionRoute> { ConnectionDestination() }
        composable<WifiRoute> { WifiDestination() }
        composable<DevicesRoute> {
            DevicesDestination(onPingHost = { target -> navController.navigateToPingDeepLink(target) })
        }
        navigation<ToolsRoute>(startDestination = ToolsHomeRoute) {
            composable<ToolsHomeRoute> {
                ToolsScreen(onNavigate = { tool -> navController.navigateToTool(tool) })
            }
            composable<PingToolRoute> { PingRoute() }
            composable<TracerouteToolRoute> { TracerouteRoute() }
            composable<DnsToolRoute> { DnsRoute() }
            composable<PortScannerToolRoute> { PortScannerRoute() }
            composable<SignalMeterToolRoute> { SignalMeterRoute() }
            composable<SubnetCalculatorToolRoute> { SubnetCalculatorRoute() }
            composable<WhoisToolRoute> { WhoisRoute() }
            composable<HttpInspectorToolRoute> { HttpInspectorRoute() }
            composable<WakeOnLanToolRoute> { WakeOnLanRoute() }
            composable<ScanHistoryToolRoute> { ScanHistoryRoute() }
            composable<DiagnosticHistoryToolRoute> { DiagnosticHistoryRoute() }
            composable<SettingsToolRoute> { SettingsRoute() }
        }
    }
}

@Composable
private fun ConnectionDestination() {
    val connectionViewModel: ConnectionViewModel = hiltViewModel()
    val connectionUiState by connectionViewModel.uiState.collectAsState()
    val monitoringState by connectionViewModel.monitoringState.collectAsState()
    // Granting location/notification access via system Settings, then returning here, doesn't
    // fire any callback the ViewModel observes - re-check both on resume.
    LifecycleResumeEffect(Unit) {
        connectionViewModel.refreshLocationAccess()
        connectionViewModel.refreshNotificationAccess()
        onPauseOrDispose {}
    }
    ConnectionScreen(
        uiState = connectionUiState,
        monitoringState = monitoringState,
        onLocationAccessChanged = connectionViewModel::refreshLocationAccess,
        onStartMonitoring = connectionViewModel::startMonitoring,
        onStopMonitoring = connectionViewModel::stopMonitoring,
        onNotificationAccessChanged = connectionViewModel::refreshNotificationAccess,
        onDismissMonitoring = connectionViewModel::dismissMonitoringCard,
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

@Composable
private fun DevicesDestination(onPingHost: (String) -> Unit) {
    val devicesViewModel: DevicesViewModel = hiltViewModel()
    val devicesUiState by devicesViewModel.uiState.collectAsState()
    DevicesScreen(
        uiState = devicesUiState,
        onScan = devicesViewModel::onScanRequested,
        onCancel = devicesViewModel::cancelSweep,
        onAcknowledgeAndScan = devicesViewModel::acknowledgeAndStartSweep,
        onConfirmShortPrefixScan = devicesViewModel::confirmShortPrefixSweep,
        onDismissConfirmation = devicesViewModel::dismissConfirmation,
        onPingHost = onPingHost,
        onSortOrderChange = devicesViewModel::setSortOrder,
        onToggleConfidenceFilter = devicesViewModel::toggleConfidenceFilter,
    )
}

/**
 * Deep-linking into another tab's nested graph needs the same popUpTo(start,
 * saveState)/launchSingleTop back-stack handling the bottom-nav tab switch above uses - a plain
 * `navigate()` here leaves [DevicesRoute] *and* the Tools graph both live on the back stack
 * simultaneously, which then confuses the next bottom-nav tab switch's own popUpTo/restoreState
 * into landing back on Ping instead of the newly-tapped tab (reproduced on-device during Phase 6,
 * the first deep link into a nested tab graph). `restoreState` is left off deliberately: this
 * must always land on the freshly-targeted host, never a previously saved Ping run.
 */
private fun NavHostController.navigateToPingDeepLink(target: String) {
    navigate(PingToolRoute(target)) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
    }
}

private fun NavHostController.navigateToTool(tool: Tool) {
    when (tool) {
        Tool.PING -> navigate(PingToolRoute())
        Tool.TRACEROUTE -> navigate(TracerouteToolRoute())
        Tool.DNS -> navigate(DnsToolRoute)
        Tool.PORT_SCANNER -> navigate(PortScannerToolRoute())
        Tool.WAKE_ON_LAN -> navigate(WakeOnLanToolRoute)
        Tool.WHOIS -> navigate(WhoisToolRoute)
        Tool.HTTP_INSPECTOR -> navigate(HttpInspectorToolRoute)
        Tool.SUBNET_CALCULATOR -> navigate(SubnetCalculatorToolRoute)
        Tool.SIGNAL_METER -> navigate(SignalMeterToolRoute)
        Tool.SCAN_HISTORY -> navigate(ScanHistoryToolRoute)
        Tool.DIAGNOSTIC_HISTORY -> navigate(DiagnosticHistoryToolRoute)
        Tool.SETTINGS -> navigate(SettingsToolRoute)
    }
}
