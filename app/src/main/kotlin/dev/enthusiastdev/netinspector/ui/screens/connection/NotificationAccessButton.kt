package dev.enthusiastdev.netinspector.ui.screens.connection

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import dev.enthusiastdev.netinspector.core.designsystem.util.findActivity

// minSdk 33 (design §0) means POST_NOTIFICATIONS is a runtime permission on every supported
// level - shared by every screen that needs to know whether the continuous-monitoring
// notification can be shown.
internal fun Context.currentNotificationAccessState(): NotificationAccessState {
    val hasPermission =
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    return if (hasPermission) NotificationAccessState.GRANTED else NotificationAccessState.PERMISSION_NEEDED
}

/** The `POST_NOTIFICATIONS` rationale/request flow (design §4.1a's pattern), factored out of
 * [MonitoringDetailsSheet] so the request lives next to the explanation of what the notification
 * is for, rather than dropping the user into a bare system prompt. */
@Composable
internal fun NotificationAccessButton(
    onGranted: () -> Unit,
    onNotificationAccessChanged: () -> Unit,
) {
    val context = LocalContext.current
    // findActivity(), not `context as Activity`: this composable renders inside a
    // ModalBottomSheet, whose LocalContext is the sheet window's ContextThemeWrapper rather
    // than the Activity, so the cast threw ClassCastException and took the app down.
    val activity = context.findActivity()
    var hasRequestedPermission by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasRequestedPermission = true
            onNotificationAccessChanged()
            if (granted) onGranted()
        }

    val permanentlyDenied =
        hasRequestedPermission &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)

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
