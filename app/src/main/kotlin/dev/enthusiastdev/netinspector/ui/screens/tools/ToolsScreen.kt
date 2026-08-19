package dev.enthusiastdev.netinspector.ui.screens.tools

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

private data class ToolTile(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

// Only Ping exists so far (Phase 2). The other eight tiles (design §9) land in Phase 7,
// which also handles the port-preset review gate before the port scanner specifically.
@Composable
fun ToolsScreen(
    onNavigateToPing: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tools = listOf(ToolTile("Ping", Icons.Filled.NetworkPing, onNavigateToPing))

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 120.dp),
        modifier = modifier.fillMaxSize().padding(16.dp),
    ) {
        items(tools) { tool -> ToolTileCard(tool) }
    }
}

@Composable
private fun ToolTileCard(tool: ToolTile) {
    Card(onClick = tool.onClick, modifier = Modifier.padding(8.dp)) {
        Icon(tool.icon, contentDescription = null, modifier = Modifier.padding(16.dp))
        Text(text = tool.label, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
}
