package dev.enthusiastdev.netinspector.ui.adaptive

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldNavigator
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * A single-pane list-detail navigator for the app's list-detail screens (Wi-Fi, Devices, and
 * the two history screens): a full-width list, and push navigation into a full-width detail.
 *
 * The stock directive splits into two panes on any "expanded" width - a landscape phone, a
 * foldable inner screen, a tablet - which has two problems here. With nothing selected the list
 * is pinned to its ~360dp preferred width with a large empty pane beside it (on a big tablet
 * the list is about a third of the window). And switching the directive to two panes only once
 * a row is opened does not make the scaffold re-expand the list: its animated state is seeded
 * once and does not re-seek when the directive changes under it, so the list stays hidden until
 * the screen is re-entered.
 *
 * Rather than ship a half-working split, every device gets the same predictable full-width
 * layout. A real two-pane list/detail for tablets and foldables is a separate, focused change.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun <T> rememberListDetailNavigator(): ThreePaneScaffoldNavigator<T> {
    val stockDirective = calculatePaneScaffoldDirective(currentWindowAdaptiveInfo())
    val singlePaneDirective = remember(stockDirective) { stockDirective.copy(maxHorizontalPartitions = 1) }
    return rememberListDetailPaneScaffoldNavigator<T>(scaffoldDirective = singlePaneDirective)
}
