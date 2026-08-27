package dev.enthusiastdev.netinspector.ui.screens.devices

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.enthusiastdev.netinspector.core.model.lan.HostConfidence

/**
 * The single compact toolbar above the device list: a List/Map view toggle, a sort menu and a
 * confidence-filter menu, all on one row and always in this form.
 *
 * It used to expand into a two-column block (these controls beside a full hygiene card) that
 * folded to an icon row once the list scrolled. The hygiene read now rides in the summary row
 * above ([DevicesSummaryRow]), so the toolbar has exactly one shape and there is no
 * scroll-driven collapse animation left to reason about.
 *
 * The row scrolls horizontally if a long sort label ("Device type") plus the two menus overflow
 * a narrow window, rather than wrapping onto a second line.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DevicesToolbar(
    viewMode: DevicesViewMode,
    sortOrder: DevicesSortOrder,
    confidenceFilter: Set<HostConfidence>,
    onViewModeChange: (DevicesViewMode) -> Unit,
    onSortOrderChange: (DevicesSortOrder) -> Unit,
    onToggleConfidence: (HostConfidence) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ViewModeToggle(viewMode, onViewModeChange)
        SortMenuButton(sortOrder, onSortOrderChange)
        FilterMenuButton(confidenceFilter, onToggleConfidence)
    }
}

/** Icon-only so two segments plus the two menus stay on one line on a narrow window - the two
 * shapes are recognisable enough (a list, a node tree) not to need labels at this size. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewModeToggle(
    viewMode: DevicesViewMode,
    onViewModeChange: (DevicesViewMode) -> Unit,
) {
    SingleChoiceSegmentedButtonRow {
        DevicesViewMode.entries.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = mode == viewMode,
                onClick = { onViewModeChange(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = DevicesViewMode.entries.size),
                icon = {},
                label = {
                    Icon(
                        imageVector =
                            if (mode == DevicesViewMode.LIST) {
                                Icons.AutoMirrored.Filled.List
                            } else {
                                Icons.Filled.AccountTree
                            },
                        contentDescription = if (mode == DevicesViewMode.LIST) "List view" else "Map view",
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
        }
    }
}

@Composable
private fun SortMenuButton(
    sortOrder: DevicesSortOrder,
    onSortOrderChange: (DevicesSortOrder) -> Unit,
) {
    ToolbarMenuButton(
        icon = Icons.AutoMirrored.Filled.Sort,
        label = sortOrder.label(),
        contentDescription = "Sort by ${sortOrder.label()}",
    ) { dismiss ->
        DevicesSortOrder.entries.forEach { entry ->
            DropdownMenuItem(
                text = { Text(entry.label()) },
                trailingIcon = { if (entry == sortOrder) Icon(Icons.Filled.Check, contentDescription = null) },
                onClick = {
                    onSortOrderChange(entry)
                    dismiss()
                },
            )
        }
    }
}

/** The menu stays open across taps (no `dismiss()` in the item click) - the three confidence
 * tiers are independent toggles, and closing after each one would make turning two of them off
 * a four-tap operation. */
@Composable
private fun FilterMenuButton(
    confidenceFilter: Set<HostConfidence>,
    onToggleConfidence: (HostConfidence) -> Unit,
) {
    val total = HostConfidence.entries.size
    ToolbarMenuButton(
        icon = Icons.Filled.FilterList,
        label = "Filter ${confidenceFilter.size}/$total",
        contentDescription = "Filter by confidence, ${confidenceFilter.size} of $total tiers shown",
    ) { _ ->
        HostConfidence.entries.forEach { confidence ->
            DropdownMenuItem(
                text = { Text(confidence.label()) },
                trailingIcon = {
                    if (confidence in confidenceFilter) Icon(Icons.Filled.Check, contentDescription = null)
                },
                onClick = { onToggleConfidence(confidence) },
            )
        }
    }
}

/** A text button carrying an icon, the current value and a dropdown caret, plus the menu it
 * opens. Sort and filter share it so they read as the same kind of control. */
@Composable
private fun ToolbarMenuButton(
    icon: ImageVector,
    label: String,
    contentDescription: String,
    menuContent: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            menuContent { expanded = false }
        }
    }
}
