package dev.enthusiastdev.netinspector.ui.screens.tools

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ToolsScreen(
    onNavigate: (Tool) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 120.dp),
        modifier = modifier.fillMaxSize().padding(16.dp),
    ) {
        items(Tool.entries) { tool -> ToolTileCard(tool, onClick = { onNavigate(tool) }) }
    }
}

@Composable
private fun ToolTileCard(
    tool: Tool,
    onClick: () -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.padding(8.dp)) {
        Icon(tool.icon, contentDescription = null, modifier = Modifier.padding(16.dp))
        Text(text = tool.label, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
}
