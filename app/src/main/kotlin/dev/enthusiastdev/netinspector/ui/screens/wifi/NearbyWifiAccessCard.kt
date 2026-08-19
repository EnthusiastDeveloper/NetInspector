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

/**
 * Rationale + request flow for [WifiAccessState.PERMISSION_NEEDED] (design §6.1, C-03). A
 * single gate, unlike the dashboard's location flow - no "services disabled" state to handle.
 */
@Composable
internal fun NearbyWifiAccessCard(
    wifiAccess: WifiAccessState,
    onWifiAccessChanged: () -> Unit,
) {
    if (wifiAccess == WifiAccessState.GRANTED) return

    val context = LocalContext.current
    val activity = context as Activity
    var hasRequestedPermission by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            hasRequestedPermission = true
            onWifiAccessChanged()
        }

    val permanentlyDenied =
        hasRequestedPermission &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.NEARBY_WIFI_DEVICES)

    InfoCard(title = "Nearby networks hidden") {
        Text(
            "Android requires nearby-devices access to list Wi-Fi networks - NetInspector " +
                "does not derive or use your location from this permission.",
        )
        TextButton(
            onClick = {
                if (permanentlyDenied) {
                    val uri = Uri.fromParts("package", context.packageName, null)
                    activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri))
                } else {
                    permissionLauncher.launch(Manifest.permission.NEARBY_WIFI_DEVICES)
                }
            },
        ) {
            Text(if (permanentlyDenied) "Open app settings" else "Grant nearby-devices access")
        }
    }
}
