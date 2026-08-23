package dev.enthusiastdev.netinspector.ui.screens.devices

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.enthusiastdev.netinspector.core.designsystem.component.ScoreChip
import dev.enthusiastdev.netinspector.core.model.lan.HostConfidence
import dev.enthusiastdev.netinspector.core.model.lan.HygieneScore

/** Everything the controls block needs, bundled so both of its two layouts can take one
 * parameter instead of nine each. */
internal data class DevicesControlsState(
    val viewMode: DevicesViewMode,
    val sortOrder: DevicesSortOrder,
    val confidenceFilter: Set<HostConfidence>,
    /** Null until at least one host has been through the extended port probe - before that the
     * score would read a meaningless "100, Excellent" for every network. */
    val hygiene: HygieneScore?,
)

internal data class DevicesControlsActions(
    val onViewModeChange: (DevicesViewMode) -> Unit,
    val onSortOrderChange: (DevicesSortOrder) -> Unit,
    val onToggleConfidence: (HostConfidence) -> Unit,
    val onShowHygieneDetails: () -> Unit,
    /** Tapping the collapsed score chip returns the user to the top of the list, where the full
     * card is - so the compact form is a way back to the expanded one, not a dead end. */
    val onExpandRequested: () -> Unit,
)

/**
 * The view toggle, sort/filter controls and hygiene score, in one of two layouts.
 *
 * Expanded (list at the top), they sit as two columns - controls on the left, the hygiene card
 * beside them on the right - rather than the hygiene card claiming a full-width block of its own
 * above everything else. Once the list is scrolled they collapse into a single row of icons,
 * which hands the device list back the ~200dp of vertical space this block otherwise holds while
 * the user is reading it, and expand again on the way back to the top.
 *
 * The transition is a spring rather than a linear tween on purpose: the block is changing shape,
 * not sliding, and a slight overshoot makes it read as one thing folding up instead of two
 * different toolbars swapping places.
 */
@Composable
internal fun DevicesControls(
    isCollapsed: Boolean,
    state: DevicesControlsState,
    actions: DevicesControlsActions,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = isCollapsed,
        modifier = modifier.fillMaxWidth(),
        transitionSpec = {
            fadeIn(animationSpec = tween(durationMillis = 180, delayMillis = 60))
                .togetherWith(fadeOut(animationSpec = tween(durationMillis = 120)))
                .using(
                    SizeTransform(clip = false) { _, _ ->
                        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                    },
                )
        },
        label = "devices-controls",
    ) { collapsed ->
        if (collapsed) {
            CollapsedDevicesControls(state, actions)
        } else {
            ExpandedDevicesControls(state, actions)
        }
    }
}

@Composable
private fun ExpandedDevicesControls(
    state: DevicesControlsState,
    actions: DevicesControlsActions,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DevicesViewModeToggle(state.viewMode, actions.onViewModeChange)
            DevicesSortFilterBar(
                sortOrder = state.sortOrder,
                confidenceFilter = state.confidenceFilter,
                onSortOrderChange = actions.onSortOrderChange,
                onToggleConfidence = actions.onToggleConfidence,
            )
        }
        state.hygiene?.let { score ->
            DevicesNetworkHygieneCard(
                score = score,
                onShowDetails = actions.onShowHygieneDetails,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CollapsedDevicesControls(
    state: DevicesControlsState,
    actions: DevicesControlsActions,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ViewModeIconButton(DevicesViewMode.LIST, Icons.AutoMirrored.Filled.List, "List view", state, actions)
        ViewModeIconButton(DevicesViewMode.MAP, Icons.Filled.AccountTree, "Map view", state, actions)
        SortIconButton(state.sortOrder, actions.onSortOrderChange)
        FilterIconButton(state.confidenceFilter, actions.onToggleConfidence)
        Box(modifier = Modifier.weight(1f))
        state.hygiene?.let { score ->
            IconButton(onClick = actions.onExpandRequested) {
                ScoreChip(score = score.value, contentDescription = "Network hygiene ${score.value}")
            }
        }
    }
}

@Composable
private fun ViewModeIconButton(
    mode: DevicesViewMode,
    icon: ImageVector,
    description: String,
    state: DevicesControlsState,
    actions: DevicesControlsActions,
) {
    val selected = state.viewMode == mode
    IconButton(
        onClick = { actions.onViewModeChange(mode) },
        colors =
            if (selected) {
                IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            } else {
                IconButtonDefaults.iconButtonColors()
            },
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SortIconButton(
    sortOrder: DevicesSortOrder,
    onSortOrderChange: (DevicesSortOrder) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                Icons.AutoMirrored.Filled.Sort,
                contentDescription = "Sort by ${sortOrder.label()}",
                modifier = Modifier.size(20.dp),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DevicesSortOrder.entries.forEach { entry ->
                DropdownMenuItem(
                    text = { Text(entry.label()) },
                    trailingIcon = { if (entry == sortOrder) Icon(Icons.Filled.Check, contentDescription = null) },
                    onClick = {
                        onSortOrderChange(entry)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** The filter chips don't survive the collapse - three of them can't fit a compact row - so
 * they become a menu, with the same toggle semantics and a tick showing what's on. */
@Composable
private fun FilterIconButton(
    confidenceFilter: Set<HostConfidence>,
    onToggleConfidence: (HostConfidence) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                Icons.Filled.FilterList,
                contentDescription =
                    "Filter by confidence, ${confidenceFilter.size} of " +
                        "${HostConfidence.entries.size} shown",
                modifier = Modifier.size(20.dp),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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
}
