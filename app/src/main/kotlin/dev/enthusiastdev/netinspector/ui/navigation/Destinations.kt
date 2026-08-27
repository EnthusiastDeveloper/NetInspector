package dev.enthusiastdev.netinspector.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NetworkWifi
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import dev.enthusiastdev.netinspector.R
import kotlinx.serialization.Serializable

// Type-safe nav routes - kept as bare markers (no UI metadata) so kotlinx.serialization never
// has to serialize an ImageVector.
@Serializable data object DashboardRoute

@Serializable data object ConnectionRoute

@Serializable data object WifiRoute

@Serializable data object DevicesRoute

// `ToolsRoute` is the *graph* route for the Tools tab, not a screen - nesting the tools grid
// and every tool destination (`PingToolRoute`, and Phase 7's rest) inside it keeps their
// back-stack state scoped to this tab. Without that nesting, the bottom nav's
// popUpTo(start){saveState=true}/restoreState pattern (needed so switching tabs doesn't lose
// each tab's state) has nothing to key a save on *per tab*, and restoring, say, the Devices tab
// after visiting Ping from a deep link can resurrect Ping instead - reproduced on-device during
// Phase 6 once host detail added its "Ping this host" deep link, the first place anything
// navigated to a Tools-tab screen from *outside* the Tools tab.
@Serializable data object ToolsRoute

@Serializable data object ToolsHomeRoute

// Settings is a top-level destination rather than a tile in the Tools grid: it configures the
// app, it isn't a diagnostic the user *runs*, and burying it among the tools made it the one
// entry there that never produced a result.
@Serializable data object SettingsRoute

data class TopLevelDestination(
    val route: Any,
    val labelRes: Int,
    val icon: ImageVector,
    val isSelected: (NavDestination?) -> Boolean,
)

// `hasRoute<T>()` is reified, so it can only be called where T is statically known - hence
// one lambda per destination here rather than a generic KClass comparison at the call site.
val topLevelDestinations: List<TopLevelDestination> =
    listOf(
        TopLevelDestination(DashboardRoute, R.string.destination_dashboard, Icons.Filled.Home) {
            it.isInHierarchy<DashboardRoute>()
        },
        // nav_connection ("Link"), not destination_connection ("Connection"): the shorter label
        // keeps all six nav items on one line and scaling with the UI text setting on a narrow
        // window. The screen itself still titles the section "Connection".
        TopLevelDestination(ConnectionRoute, R.string.nav_connection, Icons.Filled.NetworkWifi) {
            it.isInHierarchy<ConnectionRoute>()
        },
        TopLevelDestination(WifiRoute, R.string.destination_wifi, Icons.Filled.Wifi) {
            it.isInHierarchy<WifiRoute>()
        },
        TopLevelDestination(DevicesRoute, R.string.destination_devices, Icons.Filled.Devices) {
            it.isInHierarchy<DevicesRoute>()
        },
        TopLevelDestination(ToolsRoute, R.string.destination_tools, Icons.Filled.Build) {
            it.isInHierarchy<ToolsRoute>()
        },
        TopLevelDestination(SettingsRoute, R.string.destination_settings, Icons.Filled.Settings) {
            it.isInHierarchy<SettingsRoute>()
        },
    )

private inline fun <reified T : Any> NavDestination?.isInHierarchy(): Boolean =
    this?.hierarchy?.any { it.hasRoute<T>() } == true
