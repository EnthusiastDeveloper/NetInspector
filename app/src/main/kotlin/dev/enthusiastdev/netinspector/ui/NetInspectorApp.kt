package dev.enthusiastdev.netinspector.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
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
import dev.enthusiastdev.netinspector.ui.navigation.ToolsRoute
import dev.enthusiastdev.netinspector.ui.navigation.WifiRoute
import dev.enthusiastdev.netinspector.ui.navigation.topLevelDestinations
import dev.enthusiastdev.netinspector.ui.screens.PlaceholderScreen

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
                    composable<ConnectionRoute> {
                        PlaceholderScreen(stringResource(R.string.destination_connection))
                    }
                    composable<WifiRoute> {
                        PlaceholderScreen(stringResource(R.string.destination_wifi))
                    }
                    composable<DevicesRoute> {
                        PlaceholderScreen(stringResource(R.string.destination_devices))
                    }
                    composable<ToolsRoute> {
                        PlaceholderScreen(stringResource(R.string.destination_tools))
                    }
                }
            }
        }
    }
}
