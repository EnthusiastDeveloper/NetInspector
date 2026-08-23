package dev.enthusiastdev.netinspector.ui.screens.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.enthusiastdev.netinspector.core.designsystem.component.InfoCard
import dev.enthusiastdev.netinspector.core.model.connection.ConnectionSnapshot

/**
 * Connectivity facts, drawn as flat non-interactive badges.
 *
 * These were `SuggestionChip`s with an empty `onClick`: they took presses, showed a ripple and a
 * pressed state, and then did nothing - the classic "looks like a button, isn't one" trap. They're
 * plain `Surface`s now, with no click handling to promise anything.
 *
 * The set is also no longer silence-on-absence. "Internet" only ever appeared when the connection
 * *was* validated, so its absence - the case actually worth flagging - looked identical to a
 * screen that simply hadn't rendered it yet. Both outcomes are stated explicitly now.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ConnectivitySection(snapshot: ConnectionSnapshot) {
    InfoCard(title = "Connectivity") {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (snapshot.hasInternet) {
                StatusBadge("Internet access", Icons.Filled.CheckCircle, StatusTone.POSITIVE)
            } else {
                StatusBadge("No internet access", Icons.Filled.CloudOff, StatusTone.NEGATIVE)
            }
            if (snapshot.isCaptivePortal) StatusBadge("Sign-in required", Icons.Filled.Warning, StatusTone.WARNING)
            if (snapshot.isMetered) StatusBadge("Metered", Icons.Filled.DataUsage, StatusTone.WARNING)
        }
        Text(
            text = snapshot.connectivityExplanation(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Android's own words for these states, translated: "validated" means the OS reached a known
 * endpoint through this network, which is a stronger claim than "the Wi-Fi icon is solid" and is
 * worth spelling out - it's why the badge can honestly say there's no internet on a network the
 * device is fully associated with. */
private fun ConnectionSnapshot.connectivityExplanation(): String =
    when {
        isCaptivePortal ->
            "Android reached a sign-in page instead of the internet. Open the network's login " +
                "page to finish connecting."
        !hasInternet ->
            "Android's connectivity check couldn't reach the internet through this network. Local " +
                "devices may still be reachable."
        isMetered ->
            "Android reached the internet through this network. It's marked metered, so apps will " +
                "hold back large background transfers."
        else -> "Android reached the internet through this network."
    }

private enum class StatusTone { POSITIVE, NEGATIVE, WARNING }

@Composable
private fun StatusBadge(
    label: String,
    icon: ImageVector,
    tone: StatusTone,
) {
    val container = tone.containerColor()
    Surface(shape = MaterialTheme.shapes.small, color = container) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun StatusTone.containerColor(): Color =
    when (this) {
        StatusTone.POSITIVE -> MaterialTheme.colorScheme.primaryContainer
        StatusTone.NEGATIVE -> MaterialTheme.colorScheme.errorContainer
        StatusTone.WARNING -> MaterialTheme.colorScheme.tertiaryContainer
    }
