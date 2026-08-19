package dev.enthusiastdev.netinspector.core.designsystem.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * design §11.2 - the shared shape of every tabletop-posture screen: continuously-updating
 * output above the hinge, controls below, nothing drawn across the crease. [hingeBounds] must
 * already be translated into this composable's own local coordinate space (see
 * [translatedTo]) - using window coordinates directly places the split visibly in the wrong
 * spot, the most common bug in posture-aware layouts.
 */
@Composable
fun TabletopSplitLayout(
    hingeBounds: Rect,
    modifier: Modifier = Modifier,
    upper: @Composable BoxScope.() -> Unit,
    lower: @Composable BoxScope.() -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val totalHeightPx = with(LocalDensity.current) { maxHeight.toPx() }
        val hingeTopPx = hingeBounds.top.coerceIn(0f, totalHeightPx)
        val hingeBottomPx = hingeBounds.bottom.coerceIn(hingeTopPx, totalHeightPx)
        val upperFraction = if (totalHeightPx > 0f) (hingeTopPx / totalHeightPx).coerceIn(0.1f, 0.9f) else 0.5f
        val hingeThickness: Dp =
            with(LocalDensity.current) { (hingeBottomPx - hingeTopPx).toDp() }.coerceAtLeast(0.dp)

        Column(Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxWidth().weight(upperFraction), content = upper)
            if (hingeThickness > 0.dp) {
                Spacer(modifier = Modifier.fillMaxWidth().height(hingeThickness))
            }
            Box(modifier = Modifier.fillMaxWidth().weight(1f - upperFraction), content = lower)
        }
    }
}
