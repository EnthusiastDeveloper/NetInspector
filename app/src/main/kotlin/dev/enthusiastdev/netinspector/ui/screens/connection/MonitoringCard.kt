package dev.enthusiastdev.netinspector.ui.screens.connection

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.enthusiastdev.netinspector.core.designsystem.component.InfoCard

/** design §8 - "explicit user start/stop" for the continuous-monitoring foreground service,
 * plus its `POST_NOTIFICATIONS` rationale/request flow (design §4.1a's pattern, applied here
 * since without the notification the OS silently suppresses the foreground service's own
 * notice that it's running - C-09). */
@Composable
internal fun MonitoringCard(
    state: MonitoringUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onNotificationAccessChanged: () -> Unit,
    onDismiss: () -> Unit,
) {
    InfoCard(
        title = "Continuous monitoring",
        trailingContent = {
            // Dismissing doesn't stop an already-running service - its own notification keeps a
            // Stop action, so control isn't lost, only this card's presence on the dashboard.
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Dismiss continuous monitoring card")
            }
        },
    ) {
        when {
            state.isRunning -> {
                Text("Running - a persistent notification shows the live RSSI. Stops only when you stop it.")
                OutlinedButton(onClick = onStop) { Text("Stop monitoring") }
            }
            state.notificationAccess == NotificationAccessState.GRANTED -> {
                Text(
                    "Keeps a persistent notification with the live RSSI while running, independent " +
                        "of whether the app is in the foreground.",
                )
                Button(onClick = onStart) { Text("Start monitoring") }
            }
            else -> {
                Text(
                    "Android requires notification access for the persistent monitoring notice - " +
                        "without it the foreground service would be running invisibly.",
                )
                NotificationAccessButton(onGranted = onStart, onNotificationAccessChanged = onNotificationAccessChanged)
            }
        }
    }
}
