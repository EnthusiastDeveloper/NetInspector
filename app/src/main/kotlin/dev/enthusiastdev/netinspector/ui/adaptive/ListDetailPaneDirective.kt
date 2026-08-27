package dev.enthusiastdev.netinspector.ui.adaptive

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/**
 * The pane directive for the app's list-detail screens (Wi-Fi, Devices, and the two history
 * screens), forced to a single pane on a phone.
 *
 * The stock [calculatePaneScaffoldDirective] splits into two panes on any "expanded" width,
 * and a phone held in landscape counts as expanded - so the list ends up occupying half the
 * window with an empty detail pane beside it. A genuine two-pane list/detail wants a tablet or
 * an unfolded foldable, so gate on the shortest-width qualifier (`sw600dp`, the standard
 * phone/tablet line) rather than the current width. On a phone this collapses back to the
 * ordinary full-width list with push navigation into a full-width detail.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun listDetailPaneDirective(): PaneScaffoldDirective {
    val directive = calculatePaneScaffoldDirective(currentWindowAdaptiveInfo())
    val isPhone = LocalConfiguration.current.smallestScreenWidthDp < TABLET_MIN_SMALLEST_WIDTH_DP
    return if (isPhone) directive.copy(maxHorizontalPartitions = 1) else directive
}

private const val TABLET_MIN_SMALLEST_WIDTH_DP = 600
