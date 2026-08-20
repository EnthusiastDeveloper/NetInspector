package dev.enthusiastdev.netinspector.ui.screens.connection

import android.Manifest
import android.app.Activity
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

// minSdk 33 (design §0) means POST_NOTIFICATIONS is a runtime permission on every supported
// level - shared by every screen that needs to know whether the continuous-monitoring
// notification can be shown.
internal fun Context.currentNotificationAccessState(): NotificationAccessState {
    val hasPermission =
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    return if (hasPermission) NotificationAccessState.GRANTED else NotificationAccessState.PERMISSION_NEEDED
}

/** The `POST_NOTIFICATIONS` rationale/request flow (design §4.1a's pattern), factored out so
 * both [MonitoringCard] and the Settings screen's re-enable action can trigger the same
 * permission request rather than only ever routing the user back to the dashboard card. */
@Composable
internal fun NotificationAccessButton(
    onGranted: () -> Unit,
    onNotificationAccessChanged: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as Activity
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
