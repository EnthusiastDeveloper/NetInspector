package dev.enthusiastdev.netinspector.ui.screens.connection

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
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
) {
    val context = LocalContext.current
    val activity = context as Activity
    var hasRequestedPermission by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasRequestedPermission = true
            onNotificationAccessChanged()
            if (granted) onStart()
        }

    InfoCard(title = "Continuous monitoring") {
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
                val permanentlyDenied =
                    hasRequestedPermission &&
                        !ActivityCompat.shouldShowRequestPermissionRationale(
                            activity,
                            Manifest.permission.POST_NOTIFICATIONS,
                        )
                Button(
                    onClick = {
                        if (permanentlyDenied) {
                            val uri = Uri.fromParts("package", context.packageName, null)
                            activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri))
                        } else {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                ) {
                    Text(if (permanentlyDenied) "Open app settings" else "Grant notification access")
                }
            }
        }
    }
}
