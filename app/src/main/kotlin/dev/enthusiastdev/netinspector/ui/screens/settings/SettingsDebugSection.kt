package dev.enthusiastdev.netinspector.ui.screens.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
        Text(
            "Crash reports stay on this device until you choose to share them - nothing is " +
                "sent automatically.",
            style = MaterialTheme.typography.bodySmall,
        )
        AlertToggleRow(
            label = "Local crash reporting",
            checked = crashReportingEnabled,
            onCheckedChange = onCrashReportingToggle,
        )
        TextButton(onClick = onExportCrashReport, enabled = hasCrashReports) {
            Text("Export crash report")
        }
        TextButton(onClick = onExportDebugBundle) { Text("Export debug bundle") }
    }
}
