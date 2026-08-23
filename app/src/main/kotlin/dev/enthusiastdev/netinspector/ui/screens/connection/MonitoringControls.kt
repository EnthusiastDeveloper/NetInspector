package dev.enthusiastdev.netinspector.ui.screens.connection

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * design §8 - "explicit user start/stop" for the continuous-monitoring foreground service.
 *
 * Monitoring used to live in a dismissible card wedged between the connection's read-only detail
 * sections, which read as one more fact about the network rather than the opt-in background
 * service it is - and, being dismissible, could vanish from the only screen that offered it. It's
 * now a persistent bar pinned above the details: the switch starts and stops the service directly,
 * and the row itself opens [MonitoringDetailsSheet] for the explanation and the
 * `POST_NOTIFICATIONS` flow that the switch alone has no room for.
 */
@Composable
internal fun MonitoringToggleBar(
    state: MonitoringUiState,
    onToggle: (Boolean) -> Unit,
    onOpenDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor =
        if (state.isRunning) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    Surface(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = containerColor) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable(onClick = onOpenDetails)
                    .padding(start = 16.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Filled.Notifications, contentDescription = null)
            Column(modifier = Modifier.weight(1f)) {
                Text("Continuous monitoring", style = MaterialTheme.typography.titleSmall)
                Text(
                    state.statusLine(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = state.isRunning, onCheckedChange = onToggle)
        }
    }
}

private fun MonitoringUiState.statusLine(): String =
    when {
        isRunning -> "On - live signal in a notification"
        notificationAccess == NotificationAccessState.GRANTED -> "Off - tap for details"
        else -> "Off - needs notification access"
    }

/**
 * The explanation, and the `POST_NOTIFICATIONS` rationale/request flow (design §4.1a's pattern,
 * applied here since without the notification the OS silently suppresses the foreground service's
 * own notice that it's running - C-09). A sheet rather than an always-visible card: this is a
 * paragraph of "what will this do to my battery and my notification shade", which is worth
 * reading once and then never again.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MonitoringDetailsSheet(
    state: MonitoringUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onNotificationAccessChanged: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Continuous monitoring", style = MaterialTheme.typography.titleLarge)
            MonitoringDetailsBody(state, onStart, onStop, onNotificationAccessChanged, onDismiss)
        }
    }
}

@Composable
private fun MonitoringDetailsBody(
    state: MonitoringUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onNotificationAccessChanged: () -> Unit,
    onDismiss: () -> Unit,
) {
    when {
        state.isRunning -> {
            Text(
                "Running. A persistent notification shows the live signal strength, and connection " +
                    "alerts fire from it. It keeps running when the app is in the background, and " +
                    "stops only when you stop it - here or from the notification's own Stop action.",
            )
            OutlinedButton(onClick = {
                onStop()
                onDismiss()
            }) { Text("Stop monitoring") }
        }
        state.notificationAccess == NotificationAccessState.GRANTED -> {
            Text(
                "Keeps a persistent notification with the live signal strength while running, " +
                    "independent of whether the app is in the foreground. Nothing leaves the device; " +
                    "the cost is a notification in your shade and some battery for the radio callbacks.",
            )
            Button(onClick = {
                onStart()
                onDismiss()
            }) { Text("Start monitoring") }
        }
        else -> {
            Text(
                "Android requires notification access for the persistent monitoring notice - " +
                    "without it the foreground service would be running invisibly.",
            )
            NotificationAccessButton(
                onGranted = {
                    onStart()
                    onDismiss()
                },
                onNotificationAccessChanged = onNotificationAccessChanged,
            )
        }
    }
}
