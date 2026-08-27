package dev.enthusiastdev.netinspector.ui.screens.connection

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
 * Rationale + request flow for [LocationAccessState.PERMISSION_NEEDED] /
 * [LocationAccessState.SERVICES_DISABLED] (design §4.1a). Not shown once granted.
 *
 * `hasRequestedPermission` only survives configuration changes, not process death - after a
 * real permanent denial and an app restart, this shows "Grant location access" once more
 * before correctly falling back to "Open app settings" on the next denial. Worth revisiting
 * once preferences are persisted (Phase 5/8 DataStore work), not before.
 */
@Composable
internal fun LocationAccessCard(
    locationAccess: LocationAccessState,
    onLocationAccessChanged: () -> Unit,
) {
    if (locationAccess == LocationAccessState.GRANTED) return

    val context = LocalContext.current
    // findActivity() rather than `context as Activity` so this keeps working if the card is
    // ever hosted in a dialog/sheet, where LocalContext is a ContextThemeWrapper (see
    // NotificationAccessButton, where that cast crashed).
    val activity = context.findActivity()
    var hasRequestedPermission by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            hasRequestedPermission = true
            onLocationAccessChanged()
        }

    InfoCard(title = "Network name hidden") {
        when (locationAccess) {
            LocationAccessState.SERVICES_DISABLED -> {
                Text(
                    "Location services are off. Android requires them, plus location access, to " +
                        "reveal the connected network's name and BSSID - NetInspector does not read, " +
                        "store or transmit your location.",
                )
                TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) {
                    Text("Turn on location services")
                }
            }
            LocationAccessState.PERMISSION_NEEDED -> {
                Text(
                    "Android requires location access to reveal the connected network's name and " +
                        "BSSID - an OS-level restriction on Wi-Fi info, not something NetInspector uses " +
                        "for its own purposes. Location is never read, stored or transmitted.",
                )
                val permanentlyDenied =
                    hasRequestedPermission &&
                        !ActivityCompat.shouldShowRequestPermissionRationale(
                            activity,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                        )
                TextButton(
                    onClick = {
                        requestOrOpenSettings(
                            context,
                            activity,
                            permanentlyDenied,
                            permissionLauncher::launch,
                        )
                    },
                ) {
                    Text(if (permanentlyDenied) "Open app settings" else "Grant location access")
                }
            }
            LocationAccessState.GRANTED -> Unit
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
