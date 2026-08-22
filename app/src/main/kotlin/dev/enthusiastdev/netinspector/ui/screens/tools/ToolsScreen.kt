package dev.enthusiastdev.netinspector.ui.screens.tools

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.enthusiastdev.netinspector.R

@Composable
fun ToolsScreen(
    onNavigate: (Tool) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        modifier = modifier.fillMaxSize().padding(16.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                stringResource(R.string.destination_tools),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
        }
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
