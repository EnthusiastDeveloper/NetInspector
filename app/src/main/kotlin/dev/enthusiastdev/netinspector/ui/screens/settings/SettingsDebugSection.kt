package dev.enthusiastdev.netinspector.ui.screens.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.enthusiastdev.netinspector.core.designsystem.component.InfoCard

/** improvement-ideas.md #21/#22 - opt-in local crash reporting plus an on-demand debug-bundle
 * export, so a bug report no longer requires a live ADB session. Kept in its own file (rather
 * than alongside [SettingsScreen]'s other sections) to stay under detekt's per-file function
 * count threshold. */
@Composable
internal fun DebugSection(
    crashReportingEnabled: Boolean,
    hasCrashReports: Boolean,
    onCrashReportingToggle: (Boolean) -> Unit,
    onExportCrashReport: () -> Unit,
    onExportDebugBundle: () -> Unit,
) {
    InfoCard(title = "Debug & diagnostics") {
        // What this actually collects: CrashHandler.buildReportText - a stack trace, app/device
        // info, and the last 50 lines this session sent through Timber (whatever the app itself
        // logged, e.g. "sweep started"), stripped of local IPs/SSIDs before it's ever written.
        // It is not a record of taps, screens visited, or anything typed in.
        Text(
            "When enabled, a crash saves a local report on this device - the crash's technical " +
                "details (stack trace, app/device info) plus a short snippet of recent app log " +
                "output. It does not record your taps or which screens you visited. Nothing is " +
                "shared until you export it yourself, below.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(modifier = Modifier.height(4.dp))
        AlertToggleRow(
            label = "Local crash reporting",
            checked = crashReportingEnabled,
            onCheckedChange = onCrashReportingToggle,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Export crash report - the most recent crash's technical report. Use this after " +
                "NetInspector has actually crashed.",
            style = MaterialTheme.typography.bodySmall,
        )
        TextButton(onClick = onExportCrashReport, enabled = hasCrashReports) {
            Text("Export crash report")
        }
        Text(
            "Export debug bundle - a snapshot of your current scan results and recent app logs. " +
                "Use this to report a problem that isn't a crash, e.g. wrong or missing devices, " +
                "a stuck scan.",
            style = MaterialTheme.typography.bodySmall,
        )
        TextButton(onClick = onExportDebugBundle) { Text("Export debug bundle") }
    }
}
