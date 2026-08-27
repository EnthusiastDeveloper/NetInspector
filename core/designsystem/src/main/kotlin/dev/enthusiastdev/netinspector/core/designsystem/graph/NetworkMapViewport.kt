package dev.enthusiastdev.netinspector.core.designsystem.graph

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize

/** Zooming out below 1x is what makes an oversized map usable: the default view is spaced for
 * legibility rather than for fitting, so "show me everything at once" has to be a gesture the
 * user can reach. */
internal const val MIN_SCALE = 0.3f
internal const val MAX_SCALE = 4f

/**
 * How far a pinch/pan gesture has moved the network map from its default 1x, centered view.
 *
 * Kept as a plain value type with its arithmetic in [transformedBy] so the pinch/pan maths can
 * be unit tested without a `Canvas`, a density or a gesture - the same reason
 * `AxisViewport` (the channel occupancy graph's equivalent) is a plain type rather than
 * inline state in that graph's composable.
 */
internal data class NetworkMapViewport(
    val scale: Float = 1f,
    val offset: Offset = Offset.Zero,
)

/** The viewport a pinch/drag gesture produces, given the drawing's own radius and the space it
 * has to fit into.
 *
 * Panning is bounded by how far the drawing actually extends past the viewport, so an
 * oversized map can be dragged around while one that already fits stays put - see
 * [NetworkMapGraph]'s doc comment on zooming past 1x. */
internal fun NetworkMapViewport.transformedBy(
    pan: Offset,
    zoom: Float,
    contentRadiusPx: Float,
    containerSize: IntSize,
): NetworkMapViewport {
    val newScale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
    val maxOffsetX = (contentRadiusPx * newScale - containerSize.width / 2f).coerceAtLeast(0f)
    val maxOffsetY = (contentRadiusPx * newScale - containerSize.height / 2f).coerceAtLeast(0f)
    val newOffset =
        Offset(
            x = (offset.x + pan.x).coerceIn(-maxOffsetX, maxOffsetX),
            y = (offset.y + pan.y).coerceIn(-maxOffsetY, maxOffsetY),
        )
    return NetworkMapViewport(scale = newScale, offset = newOffset)
}
