package dev.enthusiastdev.netinspector.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import dev.enthusiastdev.netinspector.ui.navigation.DevicesRoute
import dev.enthusiastdev.netinspector.ui.navigation.DiagnosticHistoryToolRoute
import dev.enthusiastdev.netinspector.ui.navigation.DnsToolRoute
import dev.enthusiastdev.netinspector.ui.navigation.HttpInspectorToolRoute
import dev.enthusiastdev.netinspector.ui.navigation.PingToolRoute
import dev.enthusiastdev.netinspector.ui.navigation.PortScannerToolRoute
import dev.enthusiastdev.netinspector.ui.navigation.ScanHistoryToolRoute
import dev.enthusiastdev.netinspector.ui.navigation.SignalMeterToolRoute
import dev.enthusiastdev.netinspector.ui.navigation.SubnetCalculatorToolRoute
import dev.enthusiastdev.netinspector.ui.navigation.ThroughputToolRoute
import dev.enthusiastdev.netinspector.ui.navigation.ToolsHomeRoute
import dev.enthusiastdev.netinspector.ui.navigation.ToolsRoute
import dev.enthusiastdev.netinspector.ui.navigation.TracerouteToolRoute
import dev.enthusiastdev.netinspector.ui.navigation.WakeOnLanToolRoute
import dev.enthusiastdev.netinspector.ui.navigation.WhoisToolRoute
import dev.enthusiastdev.netinspector.ui.navigation.WifiChangesToolRoute
import dev.enthusiastdev.netinspector.ui.screens.tools.Tool
import dev.enthusiastdev.netinspector.ui.screens.tools.ToolPageScaffold
import dev.enthusiastdev.netinspector.ui.screens.tools.ToolsScreen
import dev.enthusiastdev.netinspector.ui.screens.tools.dns.DnsRoute
import dev.enthusiastdev.netinspector.ui.screens.tools.history.DiagnosticHistoryRoute
import dev.enthusiastdev.netinspector.ui.screens.tools.history.ScanHistoryRoute
import dev.enthusiastdev.netinspector.ui.screens.tools.httpinspector.HttpInspectorRoute
import dev.enthusiastdev.netinspector.ui.screens.tools.ping.PingRoute
import dev.enthusiastdev.netinspector.ui.screens.tools.portscanner.PortScannerRoute
import dev.enthusiastdev.netinspector.ui.screens.tools.signalmeter.SignalMeterRoute
import dev.enthusiastdev.netinspector.ui.screens.tools.subnetcalc.SubnetCalculatorRoute
import dev.enthusiastdev.netinspector.ui.screens.tools.throughput.ThroughputRoute
import dev.enthusiastdev.netinspector.ui.screens.tools.traceroute.TracerouteRoute
import dev.enthusiastdev.netinspector.ui.screens.tools.whois.WhoisRoute
import dev.enthusiastdev.netinspector.ui.screens.tools.wifichanges.WifiChangesRoute
import dev.enthusiastdev.netinspector.ui.screens.tools.wol.WakeOnLanRoute

/** The Tools tab's own nested graph - split out of [AppNavHost] to keep this file's tool-page
 * wiring (one route + composable per tool) from pushing [NetInspectorApp]'s file past detekt's
 * per-file function threshold. */
internal fun NavGraphBuilder.toolsGraph(navController: NavHostController) {
    navigation<ToolsRoute>(startDestination = ToolsHomeRoute) {
        composable<ToolsHomeRoute> {
            ToolsScreen(onNavigate = { tool -> navController.navigateToTool(tool) })
        }
        composable<PingToolRoute> { ToolPage(Tool.PING, navController) { PingRoute() } }
        composable<TracerouteToolRoute> { ToolPage(Tool.TRACEROUTE, navController) { TracerouteRoute() } }
        composable<DnsToolRoute> { ToolPage(Tool.DNS, navController) { DnsRoute() } }
        composable<PortScannerToolRoute> { ToolPage(Tool.PORT_SCANNER, navController) { PortScannerRoute() } }
        composable<ThroughputToolRoute> { ToolPage(Tool.LAN_THROUGHPUT, navController) { ThroughputRoute() } }
        composable<SignalMeterToolRoute> { ToolPage(Tool.SIGNAL_METER, navController) { SignalMeterRoute() } }
        composable<SubnetCalculatorToolRoute> {
            ToolPage(Tool.SUBNET_CALCULATOR, navController) { SubnetCalculatorRoute() }
        }
        composable<WhoisToolRoute> { ToolPage(Tool.WHOIS, navController) { WhoisRoute() } }
        composable<HttpInspectorToolRoute> { ToolPage(Tool.HTTP_INSPECTOR, navController) { HttpInspectorRoute() } }
        composable<WakeOnLanToolRoute> { ToolPage(Tool.WAKE_ON_LAN, navController) { WakeOnLanRoute() } }
        composable<ScanHistoryToolRoute> { ToolPage(Tool.SCAN_HISTORY, navController) { ScanHistoryRoute() } }
        composable<DiagnosticHistoryToolRoute> {
            ToolPage(Tool.DIAGNOSTIC_HISTORY, navController) { DiagnosticHistoryRoute() }
        }
        composable<WifiChangesToolRoute> { ToolPage(Tool.WIFI_CHANGES, navController) { WifiChangesRoute() } }
    }
}

@Composable
private fun ToolPage(
    tool: Tool,
    navController: NavHostController,
    content: @Composable () -> Unit,
) {
    ToolPageScaffold(title = tool.label, onBack = { navController.navigateUp() }, content = content)
}

/**
 * Every caller of this is a "run this tool on this host" action inside the Devices detail pane
 * (Ping / Traceroute / Port scanner / LAN throughput, each with the host address prefilled).
 *
 * It pops up to [DevicesRoute] rather than the graph start so that [DevicesRoute] stays on the
 * back stack: the tool's up arrow and the system back gesture then return to the host detail
 * the tool was launched from, which is what [ToolPageScaffold]'s contract promises and what a
 * user expects. Popping to the graph start instead (an earlier attempt at stopping a stale
 * Tools-graph entry from confusing the next bottom-nav tab switch) dropped [DevicesRoute] too,
 * so back landed on the dashboard. The tab-switch confusion is already handled where it belongs,
 * by `restoreState = route != ToolsRoute` in `navigateToTopLevel`.
 *
 * `saveState` on the popUpTo preserves the Devices list/detail state so the same host detail is
 * showing on return. `restoreState` is left off here so a deep link always opens on the freshly
 * targeted host, never a previously saved run of that tool.
 */
internal fun NavHostController.navigateToToolDeepLink(route: Any) {
    navigate(route) {
        popUpTo(DevicesRoute) { saveState = true }
        launchSingleTop = true
    }
}

private fun NavHostController.navigateToTool(tool: Tool) {
    when (tool) {
        Tool.PING -> navigate(PingToolRoute())
        Tool.TRACEROUTE -> navigate(TracerouteToolRoute())
        Tool.DNS -> navigate(DnsToolRoute)
        Tool.PORT_SCANNER -> navigate(PortScannerToolRoute())
        Tool.LAN_THROUGHPUT -> navigate(ThroughputToolRoute())
        Tool.WAKE_ON_LAN -> navigate(WakeOnLanToolRoute)
        Tool.WHOIS -> navigate(WhoisToolRoute)
        Tool.HTTP_INSPECTOR -> navigate(HttpInspectorToolRoute)
        Tool.SUBNET_CALCULATOR -> navigate(SubnetCalculatorToolRoute)
        Tool.SIGNAL_METER -> navigate(SignalMeterToolRoute)
        Tool.SCAN_HISTORY -> navigate(ScanHistoryToolRoute)
        Tool.DIAGNOSTIC_HISTORY -> navigate(DiagnosticHistoryToolRoute)
        Tool.WIFI_CHANGES -> navigate(WifiChangesToolRoute)
    }
}
