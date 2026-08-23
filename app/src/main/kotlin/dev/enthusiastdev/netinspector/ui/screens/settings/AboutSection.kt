package dev.enthusiastdev.netinspector.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.enthusiastdev.netinspector.BuildConfig
import dev.enthusiastdev.netinspector.R
import dev.enthusiastdev.netinspector.core.designsystem.component.InfoCard
import dev.enthusiastdev.netinspector.core.designsystem.component.InfoRow

private const val GITHUB_HANDLE = "EnthusiastDeveloper"
private const val PROFILE_URL = "https://github.com/EnthusiastDeveloper"
private const val REPOSITORY_URL = "https://github.com/EnthusiastDeveloper/NetInspector"
private const val NEW_ISSUE_URL = "https://github.com/EnthusiastDeveloper/NetInspector/issues/new/choose"
private const val DISCUSSIONS_URL = "https://github.com/EnthusiastDeveloper/NetInspector/discussions"
private const val RELEASES_URL = "https://github.com/EnthusiastDeveloper/NetInspector/releases"
private const val LICENSE_URL = "https://github.com/EnthusiastDeveloper/NetInspector/blob/main/LICENSE"

/**
 * Where the app says what it is and who made it, and where a user with a bug or a question is
 * handed somewhere to put it.
 *
 * The build version matters here specifically because the app ships as a sideloaded APK rather
 * than through a store listing: there is no other place - no Play "app info" page - where a user
 * filing an issue can find the version they are actually running.
 *
 * Every row opens a URL in the browser through [LocalUriHandler]; nothing is fetched in-process,
 * so the "no accounts, no telemetry" promise below stays true of this screen too.
 */
@Composable
internal fun AboutSection(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    InfoCard(title = "About", modifier = modifier) {
        Text(stringResource(R.string.app_display_name), style = MaterialTheme.typography.titleMedium)
        Text(
            "Android network analyzer. Runs entirely on-device: no root, no accounts, no " +
                "telemetry, no analytics.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        InfoRow("Version", "${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})")
        InfoRow("Build type", BuildConfig.BUILD_TYPE)
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            LinkRow("Developer", "@$GITHUB_HANDLE on GitHub") { uriHandler.openUri(PROFILE_URL) }
            LinkRow("Source code", "$GITHUB_HANDLE/NetInspector") { uriHandler.openUri(REPOSITORY_URL) }
            LinkRow("Report a bug", "Open a new issue") { uriHandler.openUri(NEW_ISSUE_URL) }
            LinkRow("Ask or suggest", "GitHub discussions") { uriHandler.openUri(DISCUSSIONS_URL) }
            LinkRow("Releases", "Download the latest APK") { uriHandler.openUri(RELEASES_URL) }
            LinkRow("License", "Apache License 2.0") { uriHandler.openUri(LICENSE_URL) }
        }
    }
}

/** A tappable [InfoRow] with a trailing "opens externally" affordance - the icon is what
 * distinguishes these from the read-only rows above them, since both are otherwise a plain
 * label/value pair. `heightIn(48.dp)` keeps every one of them a full touch target. */
@Composable
private fun LinkRow(
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f, fill = false),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f, fill = false).padding(start = 8.dp),
        ) {
            Text(text = value, style = MaterialTheme.typography.bodyMedium)
            Icon(
                Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = "Opens in your browser",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
