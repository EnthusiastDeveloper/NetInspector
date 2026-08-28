package dev.enthusiastdev.netinspector.ui.screens.wifi

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import dev.enthusiastdev.netinspector.core.designsystem.component.InfoCard
import dev.enthusiastdev.netinspector.core.designsystem.util.findActivity

/**
 * Rationale + request flow for [WifiAccessState.PERMISSION_NEEDED] /
 * [WifiAccessState.SERVICES_DISABLED] (design §4.1, C-03). Same shape as the dashboard's
 * `LocationAccessCard` - same permission, same two independent failure states - kept as a
 * separate composable/state enum since this screen runs its own permission check.
 */
@Composable
internal fun WifiLocationAccessCard(
    wifiAccess: WifiAccessState,
    onWifiAccessChanged: () -> Unit,
) {
    if (wifiAccess == WifiAccessState.GRANTED) return

    val context = LocalContext.current
    // findActivity() rather than `context as Activity` so this keeps working if the card is
    // ever hosted in a dialog/sheet, where LocalContext is a ContextThemeWrapper (see
    // NotificationAccessButton, where that cast crashed).
    val activity = context.findActivity()
    var hasRequestedPermission by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            hasRequestedPermission = true
            onWifiAccessChanged()
        }

    InfoCard(title = "Networks hidden") {
        when (wifiAccess) {
            WifiAccessState.SERVICES_DISABLED -> {
                Text(
                    "Location services are off. Android requires them, plus location access, to " +
                        "list nearby Wi-Fi networks - NetInspector does not read, store or transmit " +
                        "your location.",
                )
                TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) {
                    Text("Turn on location services")
                }
            }
            WifiAccessState.PERMISSION_NEEDED -> {
                Text(
                    "Android requires location access to list nearby Wi-Fi networks - an OS-level " +
                        "restriction on scan results, not something NetInspector uses for its own " +
                        "purposes. Location is never read, stored or transmitted.",
                )
                val permanentlyDenied =
                    hasRequestedPermission &&
                        !ActivityCompat.shouldShowRequestPermissionRationale(
                            activity,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                        )
                TextButton(
                    onClick = {
                        requestOrOpenSettings(context, activity, permanentlyDenied, permissionLauncher::launch)
                    },
                ) {
                    Text(if (permanentlyDenied) "Open app settings" else "Grant location access")
                }
            }
            WifiAccessState.GRANTED -> Unit
        }
    }
}

private fun requestOrOpenSettings(
    context: android.content.Context,
    activity: Activity,
    permanentlyDenied: Boolean,
    launchPermissionRequest: (String) -> Unit,
) {
    if (permanentlyDenied) {
        val uri = Uri.fromParts("package", context.packageName, null)
        activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri))
    } else {
        launchPermissionRequest(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}
