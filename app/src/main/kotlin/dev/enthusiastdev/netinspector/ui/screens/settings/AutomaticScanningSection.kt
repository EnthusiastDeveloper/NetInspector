package dev.enthusiastdev.netinspector.ui.screens.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.enthusiastdev.netinspector.core.designsystem.component.InfoCard
import dev.enthusiastdev.netinspector.data.persistence.preferences.AutoScanSettingsRepository
import dev.enthusiastdev.netinspector.ui.screens.connection.NotificationAccessButton
import dev.enthusiastdev.netinspector.ui.screens.connection.NotificationAccessState
import dev.enthusiastdev.netinspector.ui.screens.connection.currentNotificationAccessState

/** improvement-ideas.md #23/#24 - background Wi-Fi scan history (#23) plus, nested under it,
 * new/vanished/reappeared LAN device alerts (#24) - the nesting encodes #24's "depends on
 * #23's scheduling work" in the UI directly, since the LAN sweep only ever runs as part of
 * this same periodic job. Kept in its own file, not [SettingsScreen.kt], for the same reason
 * [SettingsDebugSection] is: past that file's per-file function threshold. */
@Composable
internal fun AutomaticScanningSection(
    uiState: SettingsUiState,
    onAutoScanEnabledChange: (Boolean) -> Unit,
    onIntervalChange: (Int) -> Unit,
    onAlertOnLanHostChangesChange: (Boolean) -> Unit,
) {
    InfoCard(title = "Automatic scanning") {
        Text(
            "Scan Wi-Fi, and optionally LAN devices, in the background so history builds up " +
                "without opening the app. Android's background limits (Doze) may delay or skip " +
                "a run when the screen has been off a while - gaps are expected, not a bug.",
            style = MaterialTheme.typography.bodySmall,
        )
        AlertToggleRow(
            label = "Scan automatically in background",
            checked = uiState.autoScanEnabled,
            onCheckedChange = onAutoScanEnabledChange,
        )
        if (uiState.autoScanEnabled) {
            Text("Interval", style = MaterialTheme.typography.labelLarge)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                AutoScanSettingsRepository.ALLOWED_INTERVAL_MINUTES.forEach { minutes ->
                    FilterChip(
                        selected = uiState.autoScanIntervalMinutes == minutes,
                        onClick = { onIntervalChange(minutes) },
                        label = { Text(intervalLabel(minutes)) },
                    )
                }
            }
            AlertToggleRow(
                label = "Alert on new or vanished devices",
                checked = uiState.alertOnLanHostChanges,
                onCheckedChange = onAlertOnLanHostChangesChange,
            )
            if (uiState.alertOnLanHostChanges) {
                LanAlertNotificationNudge()
            }
        }
    }
}

private fun intervalLabel(minutes: Int): String =
    when {
        minutes < 60 -> "${minutes}m"
        minutes % 1440 == 0 -> "${minutes / 1440}d"
        else -> "${minutes / 60}h"
    }

/** Reuses the `POST_NOTIFICATIONS` rationale/request flow already built for
 * `MonitoringService`'s alerts ([NotificationAccessButton]) rather than a second permission
 * flow - this screen's alert is the same permission, just a different notification channel. */
@Composable
private fun LanAlertNotificationNudge() {
    val context = LocalContext.current
    var permissionCheckToken by remember { mutableIntStateOf(0) }
    val notificationAccess = remember(permissionCheckToken) { context.currentNotificationAccessState() }
    if (notificationAccess == NotificationAccessState.PERMISSION_NEEDED) {
        Text(
            "Notifications are off for NetInspector, so device alerts won't show.",
            style = MaterialTheme.typography.bodySmall,
        )
        NotificationAccessButton(
            onGranted = {},
            onNotificationAccessChanged = { permissionCheckToken++ },
        )
    }
}
