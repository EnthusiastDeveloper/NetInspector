package dev.enthusiastdev.netinspector.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScope
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavDestination
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.enthusiastdev.netinspector.core.designsystem.adaptive.DevicePosture
import dev.enthusiastdev.netinspector.core.designsystem.adaptive.LocalDevicePosture
import dev.enthusiastdev.netinspector.core.designsystem.adaptive.rememberDevicePosture
import dev.enthusiastdev.netinspector.core.designsystem.adaptive.translatedTo
import dev.enthusiastdev.netinspector.ui.navigation.ConnectionRoute
import dev.enthusiastdev.netinspector.ui.navigation.DashboardRoute
import dev.enthusiastdev.netinspector.ui.navigation.DevicesRoute
import dev.enthusiastdev.netinspector.ui.navigation.PingToolRoute
import dev.enthusiastdev.netinspector.ui.navigation.PortScannerToolRoute
import dev.enthusiastdev.netinspector.ui.navigation.SettingsRoute
import dev.enthusiastdev.netinspector.ui.navigation.ThroughputToolRoute
import dev.enthusiastdev.netinspector.ui.navigation.ToolsRoute
import dev.enthusiastdev.netinspector.ui.navigation.TracerouteToolRoute
import dev.enthusiastdev.netinspector.ui.navigation.WifiRoute
import dev.enthusiastdev.netinspector.ui.navigation.topLevelDestinations
import dev.enthusiastdev.netinspector.ui.screens.connection.ConnectionScreen
import dev.enthusiastdev.netinspector.ui.screens.connection.ConnectionViewModel
import dev.enthusiastdev.netinspector.ui.screens.dashboard.DashboardScreen
import dev.enthusiastdev.netinspector.ui.screens.dashboard.DashboardViewModel
import dev.enthusiastdev.netinspector.ui.screens.devices.DevicesScreen
import dev.enthusiastdev.netinspector.ui.screens.devices.DevicesViewModel
import dev.enthusiastdev.netinspector.ui.screens.settings.SettingsDestination
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

            val layoutType = navigationSuiteLayoutType()
            // Only the bottom bar splits its width six ways and stacks the label under the icon,
            // so it is the one layout where a label can outgrow its slot. The rail and drawer
            // give each label its own row and always keep the stock style. (fittedBottomNavLabelStyle
            // stays an unconditional call so it is never a composable invoked only on some
            // recompositions.)
            val fittedLabelStyle = fittedBottomNavLabelStyle()
            val labelStyle =
                if (layoutType == NavigationSuiteType.NavigationBar) {
                    fittedLabelStyle
                } else {
                    MaterialTheme.typography.labelMedium
                }

            NavigationSuiteScaffold(
                navigationSuiteItems = { navigationItems(navController, currentDestination, labelStyle) },
                layoutType = layoutType,
            ) {
                AppNavHost(navController)
            }
        }
    }
}

/** Below this window height the bottom navigation bar - a fixed ~80dp of chrome regardless of
 * orientation - eats a punishing share of the screen. */
private const val COMPACT_WINDOW_HEIGHT_DP = 480

/**
 * A phone in landscape has plenty of width and very little height, so the bottom bar costs
 * proportionally far more of the content area there than it does in portrait. Material's own
 * size-class mapping only looks at *width*, which reads a landscape phone as "medium/expanded"
 * and still hands it a bottom bar; a short window is switched to the left-hand rail instead,
 * which spends the axis the device actually has to spare. Everything else keeps the stock
 * width-driven mapping (bar on portrait phones, rail on tablets, drawer on wide windows).
 */
@Composable
private fun navigationSuiteLayoutType(): NavigationSuiteType {
    val windowHeightDp = LocalConfiguration.current.screenHeightDp
    return if (windowHeightDp < COMPACT_WINDOW_HEIGHT_DP) {
        NavigationSuiteType.NavigationRail
    } else {
        NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())
    }
}

/**
 * A bottom-nav label that does not fit its slot wraps to two lines, which looks broken and
 * steals a row of content height. The nav labels are deliberately kept short ("Link", not
 * "Connection") so at a normal width they fit and scale with the UI text setting untouched.
 * This is the safety net for the extremes (a very narrow window, or the top of the scale
 * range): it shrinks the label text just enough for the widest one to still fit on one line,
 * and only when even that shrink would push the text below [MIN_BOTTOM_NAV_LABEL_SCALE] of its
 * normal size does it give up and return `null`, meaning the bar should show icons alone.
 */
@Composable
private fun fittedBottomNavLabelStyle(): TextStyle? {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val baseStyle = MaterialTheme.typography.labelMedium
    val labels = topLevelDestinations.map { stringResource(it.labelRes) }
    return remember(configuration.screenWidthDp, density.density, density.fontScale, labels) {
        val slotWidthPx =
            with(density) { (configuration.screenWidthDp.dp / topLevelDestinations.size).toPx() }
        // NavigationBarItem keeps a little breathing room on each side of the label.
        val availablePx = slotWidthPx - with(density) { BOTTOM_NAV_LABEL_SLACK.toPx() }
        val widestPx =
            labels.maxOf { measurer.measure(it, baseStyle, softWrap = false, maxLines = 1).size.width }
        when {
            widestPx <= availablePx -> baseStyle
            availablePx / widestPx >= MIN_BOTTOM_NAV_LABEL_SCALE ->
                baseStyle.copy(fontSize = baseStyle.fontSize * (availablePx / widestPx))
            else -> null
        }
    }
}

private val BOTTOM_NAV_LABEL_SLACK = 8.dp

/** Below this fraction of the normal label size the text is too small to bother with, so the
 * bottom bar switches to icons only instead. */
private const val MIN_BOTTOM_NAV_LABEL_SCALE = 0.8f

private fun NavigationSuiteScope.navigationItems(
    navController: NavHostController,
    currentDestination: NavDestination?,
    labelStyle: TextStyle?,
) {
    topLevelDestinations.forEach { destination ->
        item(
            selected = destination.isSelected(currentDestination),
            onClick = { navController.navigateToTopLevel(destination.route) },
            icon = { Icon(destination.icon, contentDescription = null) },
            label =
                labelStyle?.let { style ->
                    { Text(stringResource(destination.labelRes), style = style, maxLines = 1, softWrap = false) }
                },
        )
    }
}

/** Shared by the bottom-nav items above and the dashboard's shortcut cards, so tapping "Devices"
 * on the dashboard behaves exactly like tapping the Devices tab - not a separate navigation path
 * that could drift out of sync with it. */
private fun NavHostController.navigateToTopLevel(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        // The Tools tab always reopens on its own grid rather than restoring whatever tool was
        // last open - restoring here would resurrect Ping/DNS/etc. from that tab's saved back
        // stack instead of landing on ToolsHomeRoute.
        restoreState = route != ToolsRoute
    }
    // Reaching the Devices tab this way should land on the scan-results list, not the host
    // detail that happened to be open when the tab was last left (restoreState brings that
    // detail back with the rest of the tab's state). The tool deep links that must return to
    // that detail go through navigateToToolDeepLink instead and never raise this flag.
    if (route == DevicesRoute) {
        currentBackStackEntry?.savedStateHandle?.set(RESET_DEVICE_SELECTION_KEY, true)
    }
}

/** SavedStateHandle key on the Devices back-stack entry - see [navigateToTopLevel]. */
private const val RESET_DEVICE_SELECTION_KEY = "devices_reset_selection"

@Composable
private fun AppNavHost(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = DashboardRoute,
        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout)),
    ) {
        composable<DashboardRoute> {
            DashboardDestination(
                onOpenWifi = { navController.navigateToTopLevel(WifiRoute) },
                onOpenDevices = { navController.navigateToTopLevel(DevicesRoute) },
                onOpenTools = { navController.navigateToTopLevel(ToolsRoute) },
            )
        }
        composable<ConnectionRoute> { ConnectionDestination() }
        composable<WifiRoute> { WifiDestination() }
        composable<DevicesRoute> { backStackEntry ->
            val resetSelectionRequested by
                backStackEntry.savedStateHandle
                    .getStateFlow(RESET_DEVICE_SELECTION_KEY, false)
                    .collectAsState()
            DevicesDestination(
                resetSelectionRequested = resetSelectionRequested,
                onSelectionReset = { backStackEntry.savedStateHandle[RESET_DEVICE_SELECTION_KEY] = false },
                onPingHost = { target -> navController.navigateToToolDeepLink(PingToolRoute(target)) },
                onTracerouteHost = { target -> navController.navigateToToolDeepLink(TracerouteToolRoute(target)) },
                onPortScanHost = { target -> navController.navigateToToolDeepLink(PortScannerToolRoute(target)) },
                onThroughputHost = { target -> navController.navigateToToolDeepLink(ThroughputToolRoute(target)) },
            )
        }
        toolsGraph(navController)
        composable<SettingsRoute> { SettingsDestination() }
    }
}

@Composable
private fun DashboardDestination(
    onOpenWifi: () -> Unit,
    onOpenDevices: () -> Unit,
    onOpenTools: () -> Unit,
) {
    val dashboardViewModel: DashboardViewModel = hiltViewModel()
    val dashboardUiState by dashboardViewModel.uiState.collectAsState()
    DashboardScreen(
        uiState = dashboardUiState,
        onOpenWifi = onOpenWifi,
        onOpenDevices = onOpenDevices,
        onOpenTools = onOpenTools,
        onExportCrashReport = dashboardViewModel::exportCrashReport,
        onDismissCrashReport = dashboardViewModel::dismissCrashReport,
    )
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
private fun DevicesDestination(
    resetSelectionRequested: Boolean,
    onSelectionReset: () -> Unit,
    onPingHost: (String) -> Unit,
    onTracerouteHost: (String) -> Unit,
    onPortScanHost: (String) -> Unit,
    onThroughputHost: (String) -> Unit,
) {
    val devicesViewModel: DevicesViewModel = hiltViewModel()
    val devicesUiState by devicesViewModel.uiState.collectAsState()
    DevicesScreen(
        uiState = devicesUiState,
        resetSelectionRequested = resetSelectionRequested,
        onSelectionReset = onSelectionReset,
        onScan = devicesViewModel::onScanRequested,
        onCancel = devicesViewModel::cancelSweep,
        onAcknowledgeAndScan = devicesViewModel::acknowledgeAndStartSweep,
        onConfirmShortPrefixScan = devicesViewModel::confirmShortPrefixSweep,
        onDismissConfirmation = devicesViewModel::dismissConfirmation,
        onPingHost = onPingHost,
        onTracerouteHost = onTracerouteHost,
        onPortScanHost = onPortScanHost,
        onThroughputHost = onThroughputHost,
        onSortOrderChange = devicesViewModel::setSortOrder,
        onToggleConfidenceFilter = devicesViewModel::toggleConfidenceFilter,
        onSetNickname = devicesViewModel::setNickname,
        onSetKnownDevice = devicesViewModel::setKnownDevice,
    )
}
