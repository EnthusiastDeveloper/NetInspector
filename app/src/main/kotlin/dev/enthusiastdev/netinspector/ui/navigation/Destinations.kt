package dev.enthusiastdev.netinspector.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.NetworkWifi
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import dev.enthusiastdev.netinspector.R
import kotlinx.serialization.Serializable

// Type-safe nav routes - kept as bare markers (no UI metadata) so kotlinx.serialization never
// has to serialize an ImageVector.
@Serializable data object ConnectionRoute

@Serializable data object WifiRoute

@Serializable data object DevicesRoute

@Serializable data object ToolsRoute

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
        TopLevelDestination(ConnectionRoute, R.string.destination_connection, Icons.Filled.NetworkWifi) {
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
    )

private inline fun <reified T : Any> NavDestination?.isInHierarchy(): Boolean =
    this?.hierarchy?.any { it.hasRoute<T>() } == true
