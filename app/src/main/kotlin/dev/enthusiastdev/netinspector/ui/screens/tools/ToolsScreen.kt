package dev.enthusiastdev.netinspector.ui.screens.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.enthusiastdev.netinspector.R

/** Narrow enough that a portrait phone still fits two columns, wide enough that "Diagnostic
 * history" fits on one line beside its icon at typical font scales. */
private val TILE_MIN_WIDTH = 160.dp
private val TILE_ICON_SIZE = 22.dp

/**
 * A grid of icon-plus-label tiles, grouped by [ToolCategory].
 *
 * Each tile is a single horizontal row rather than the stacked icon-over-label block it used to
 * be: the stacked form spent two lines and a large gap per tile, so a portrait phone showed only
 * a handful of tools at once and the eye had to travel down-and-across to read each one. A
 * leading icon with the label beside it puts every tool's name on one predictable left-aligned
 * line, which is what makes a list of a dozen scannable.
 */
@Composable
fun ToolsScreen(
    onNavigate: (Tool) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = TILE_MIN_WIDTH),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                stringResource(R.string.destination_tools),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        ToolCategory.entries.forEach { category ->
            item(span = { GridItemSpan(maxLineSpan) }) { ToolCategoryHeader(category) }
            items(Tool.entries.filter { it.category == category }) { tool ->
                ToolTileCard(tool, onClick = { onNavigate(tool) })
            }
        }
    }
}

@Composable
private fun ToolCategoryHeader(category: ToolCategory) {
    Text(
        text = category.label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
}

@Composable
private fun ToolTileCard(
    tool: Tool,
    onClick: () -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                tool.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(TILE_ICON_SIZE),
            )
            Text(
                text = tool.label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
