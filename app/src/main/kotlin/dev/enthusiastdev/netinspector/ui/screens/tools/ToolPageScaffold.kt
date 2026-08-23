package dev.enthusiastdev.netinspector.ui.screens.tools

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow

/**
 * The chrome every tool page shares: its name plus an "up" affordance back to the Tools grid.
 *
 * Opening a tool used to replace the grid with a bare screen whose only way back was the system
 * back gesture - no visible hint that going back was even possible, against the up-arrow
 * convention every other Android app follows. Applied centrally in `NetInspectorApp`'s nav graph
 * rather than inside each of the eleven tool screens, so a new tool gets it by being registered
 * as a route, not by remembering to wrap its own content.
 *
 * The arrow calls `navigateUp()`, so it lands wherever the system back gesture would: the Tools
 * grid for a tool opened from there, or the Devices tab for one reached through a host detail's
 * "Ping this host" deep link. Hence the neutral "Navigate up" description rather than naming the
 * Tools grid, which isn't always where it goes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolPageScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Navigate up")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(),
        )
        Box(modifier = Modifier.weight(1f)) { content() }
    }
}
