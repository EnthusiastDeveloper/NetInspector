package dev.enthusiastdev.netinspector.core.designsystem.graph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private const val HUB_RADIUS_DP = 22f
private const val NODE_RADIUS_DP = 11f
private const val LABEL_OFFSET_DP = 5f

/** Rings never sit closer together than this multiple of a node's radius, so a node and the
 * label under it always have room even when the host count would otherwise pack them. See
 * [networkMapRingSpacingPx]. */
private const val MIN_RING_SPACING_FACTOR = 5f

/** Zooming out below 1x is what makes an oversized map usable: the default view is spaced for
 * legibility rather than for fitting, so "show me everything at once" has to be a gesture the
 * user can reach. */
private const val MIN_SCALE = 0.3f
private const val MAX_SCALE = 4f

// Filled discs at full opacity turn a dense map into a wall of solid color where overlapping
// nodes are indistinguishable. A translucent fill with a firmer outline keeps each node's own
// edge visible where two of them touch, and lets the spoke lines read through.
private const val NODE_FILL_ALPHA = 0.45f
private const val NODE_STROKE_ALPHA = 0.9f
private const val NODE_STROKE_WIDTH_DP = 1.5f

// A resolved hostname/device-hint label can run far longer than the node spacing at this map's
// scale allows (e.g. a device-hint fallback like "Linux/Android/iOS/macOS family") - clipped
// with an ellipsis rather than left to overlap neighboring nodes, matching the collision-avoidance
// the channel occupancy graph already applies to its own labels.
private const val LABEL_MAX_WIDTH_DP = 64f

/**
 * A hub-and-spoke visualization of a *logical* network - who was discovered around the gateway,
 * not real switch-level wiring (unavailable without SNMP/LLDP). [hub] is drawn at the center;
 * [spokes] ring it, packed into concentric rings by [computeRadialSlots] as their count grows.
 * Sizing is entirely up to [modifier] - a dense map benefits from as much room as the caller can
 * give it, so this never imposes its own fixed height.
 *
 * The layout is spaced for readability first and fitting second (see [networkMapRingSpacingPx]):
 * beyond roughly two dozen hosts the drawing is deliberately larger than the viewport, and
 * pinch-to-zoom - now including zooming *out* past the default - plus panning are how the user
 * chooses between reading labels and seeing everything at once. Squeezing 40 hosts into one
 * screenful produces a picture in which nothing can be read, which is worse than one that needs a
 * gesture. [nodeScaleFor] separately keeps the *default*, unzoomed view usable as host count
 * grows, by shrinking node/label size to match how compressed the ring spacing gets.
 *
 * Tap hit-testing recomputes the same [networkMapOffsets] geometry the draw pass used (the
 * `ChannelOccupancyGraph` pattern this module already follows) rather than attaching one
 * `Modifier.clickable` per node, since node count and layout are both dynamic. That means, like
 * that graph, there is no per-node TalkBack target - [contentDescription] instead names every
 * node in one aggregate summary, and callers are expected to offer a fully accessible list view
 * alongside this one rather than relying on the map as the only way to reach a host.
 */
@Composable
fun NetworkMapGraph(
    hub: NetworkMapNode?,
    spokes: List<NetworkMapNode>,
    onNodeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val contentRadiusPx =
        remember(spokes.size, containerSize, density) {
            with(density) { contentRadiusPx(spokes.size, containerSize) }
        }

    Box(
        modifier =
            modifier
                .clipToBounds()
                .onSizeChanged { containerSize = it }
                .pointerInput(contentRadiusPx) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                        // Panning is bounded by how far the drawing actually extends past the
                        // viewport, so an oversized map can be dragged around while one that
                        // already fits stays put.
                        val maxOffsetX =
                            (contentRadiusPx * newScale - containerSize.width / 2f).coerceAtLeast(0f)
                        val maxOffsetY =
                            (contentRadiusPx * newScale - containerSize.height / 2f).coerceAtLeast(0f)
                        offset =
                            Offset(
                                x = (offset.x + pan.x).coerceIn(-maxOffsetX, maxOffsetX),
                                y = (offset.y + pan.y).coerceIn(-maxOffsetY, maxOffsetY),
                            )
                        scale = newScale
                    }
                },
    ) {
        NetworkMapCanvas(hub = hub, spokes = spokes, onNodeClick = onNodeClick, scale = scale, offset = offset)
    }
}

/** The drawing's own radius in pixels, independent of the viewport it has to fit into. */
private fun Density.contentRadiusPx(
    spokeCount: Int,
    containerSize: IntSize,
): Float {
    val hubRadiusPx = HUB_RADIUS_DP.dp.toPx()
    val nodeRadiusPx = NODE_RADIUS_DP.dp.toPx()
    val ringCount = networkMapRingCount(spokeCount)
    val availableRadiusPx = minOf(containerSize.width, containerSize.height) / 2f - nodeRadiusPx
    val ringSpacingPx =
        networkMapRingSpacingPx(
            hubRadiusPx = hubRadiusPx,
            availableRadiusPx = availableRadiusPx,
            ringCount = ringCount,
            minRingSpacingPx = nodeRadiusPx * MIN_RING_SPACING_FACTOR,
        )
    return networkMapContentRadiusPx(hubRadiusPx, nodeRadiusPx, ringSpacingPx, ringCount)
}

@Composable
private fun NetworkMapCanvas(
    hub: NetworkMapNode?,
    spokes: List<NetworkMapNode>,
    onNodeClick: (String) -> Unit,
    scale: Float,
    offset: Offset,
) {
    // docs/adr - a fixed node/label size overlaps once enough hosts need a third-plus ring;
    // this shrinks both to match how compressed the ring spacing actually is, see nodeScaleFor.
    val nodeScale = nodeScaleFor(spokes.size)
    val paint =
        NetworkMapPaint(
            textMeasurer = rememberTextMeasurer(),
            labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
            colors = networkMapColors(),
            hubRadiusPx = with(LocalDensity.current) { (HUB_RADIUS_DP * nodeScale).dp.toPx() },
            nodeRadiusPx = with(LocalDensity.current) { (NODE_RADIUS_DP * nodeScale).dp.toPx() },
            labelMaxWidthPx = with(LocalDensity.current) { (LABEL_MAX_WIDTH_DP * nodeScale).dp.toPx() },
        )
    val hubRadiusPx = paint.hubRadiusPx
    val nodeRadiusPx = paint.nodeRadiusPx

    Canvas(
        modifier =
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }.pointerInput(hub, spokes) {
                    // No onDoubleTap here either: it would delay every node tap by the
                    // double-tap timeout, and pinching out already returns to a full overview.
                    detectTapGestures { tapOffset ->
                        val geometry =
                            NetworkMapGeometry(
                                size.width.toFloat(),
                                size.height.toFloat(),
                                hubRadiusPx,
                                nodeRadiusPx,
                                spokes,
                            )
                        val hitId =
                            hitTestNode(
                                tap = tapOffset,
                                hub = hub?.let { it.id to geometry.center },
                                spokes = geometry.spokeOffsets.mapIndexed { i, o -> spokes[i].id to o },
                                hitRadiusPx = nodeRadiusPx,
                            )
                        hitId?.let(onNodeClick)
                    }
                }.clearAndSetSemantics { contentDescription = describeMap(hub, spokes) },
    ) {
        val geometry = NetworkMapGeometry(size.width, size.height, hubRadiusPx, nodeRadiusPx, spokes)
        drawNetworkMap(geometry, hub, spokes, paint)
    }
}

private data class NetworkMapColors(
    val hub: Color,
    val self: Color,
    val atRisk: Color,
    val normal: Color,
    val spokeLine: Color,
)

@Composable
private fun networkMapColors() =
    NetworkMapColors(
        hub = MaterialTheme.colorScheme.primary,
        self = MaterialTheme.colorScheme.tertiary,
        atRisk = MaterialTheme.colorScheme.error,
        normal = MaterialTheme.colorScheme.secondary,
        spokeLine = MaterialTheme.colorScheme.outlineVariant,
    )

/** Everything the draw pass needs besides geometry, bundled so [drawNetworkMap] stays under a
 * plain parameter-count lint threshold without losing any of it. */
private class NetworkMapPaint(
    val textMeasurer: TextMeasurer,
    val labelStyle: TextStyle,
    val colors: NetworkMapColors,
    val hubRadiusPx: Float,
    val nodeRadiusPx: Float,
    val labelMaxWidthPx: Float,
)

private fun DrawScope.drawNetworkMap(
    geometry: NetworkMapGeometry,
    hub: NetworkMapNode?,
    spokes: List<NetworkMapNode>,
    paint: NetworkMapPaint,
) {
    geometry.spokeOffsets.forEach { spokeOffset ->
        drawLine(paint.colors.spokeLine, geometry.center, spokeOffset, strokeWidth = 1.dp.toPx())
    }
    if (hub != null) {
        drawNode(paint.colors.hub, paint.hubRadiusPx, geometry.center)
        drawNodeLabel(paint, hub.label, geometry.center, paint.hubRadiusPx)
    }
    spokes.forEachIndexed { index, node ->
        val spokeOffset = geometry.spokeOffsets[index]
        val color =
            if (node.isAtRisk) {
                paint.colors.atRisk
            } else if (node.isSelf) {
                paint.colors.self
            } else {
                paint.colors.normal
            }
        drawNode(color, paint.nodeRadiusPx, spokeOffset)
        drawNodeLabel(paint, node.label, spokeOffset, paint.nodeRadiusPx)
    }
}

/** A translucent disc with a firmer ring, so two overlapping nodes still read as two. */
private fun DrawScope.drawNode(
    color: Color,
    radiusPx: Float,
    center: Offset,
) {
    drawCircle(color.copy(alpha = NODE_FILL_ALPHA), radius = radiusPx, center = center)
    drawCircle(
        color.copy(alpha = NODE_STROKE_ALPHA),
        radius = radiusPx,
        center = center,
        style = Stroke(width = NODE_STROKE_WIDTH_DP.dp.toPx()),
    )
}

/** Shared node-placement geometry for one draw/tap pass - computed once so both agree on exactly
 * where every node sits, the same role `AxisMapper` plays for the channel occupancy graph. */
private class NetworkMapGeometry(
    widthPx: Float,
    heightPx: Float,
    hubRadiusPx: Float,
    nodeRadiusPx: Float,
    spokes: List<NetworkMapNode>,
) {
    val center = Offset(widthPx / 2f, heightPx / 2f)
    val spokeOffsets: List<Offset>

    init {
        val availableRadiusPx = minOf(widthPx, heightPx) / 2f - nodeRadiusPx
        val slots = computeRadialSlots(spokes.size)
        spokeOffsets =
            networkMapOffsets(
                center = center,
                hubRadiusPx = hubRadiusPx,
                availableRadiusPx = availableRadiusPx,
                slots = slots,
                minRingSpacingPx = nodeRadiusPx * MIN_RING_SPACING_FACTOR,
            )
    }
}

private fun DrawScope.drawNodeLabel(
    paint: NetworkMapPaint,
    label: String,
    nodeCenter: Offset,
    nodeRadiusPx: Float,
) {
    val measured =
        paint.textMeasurer.measure(
            text = label,
            style = paint.labelStyle,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
            maxLines = 1,
            constraints = Constraints(maxWidth = paint.labelMaxWidthPx.roundToInt()),
        )
    drawText(
        textLayoutResult = measured,
        topLeft =
            Offset(
                x = nodeCenter.x - measured.size.width / 2f,
                y = nodeCenter.y + nodeRadiusPx + LABEL_OFFSET_DP.dp.toPx(),
            ),
    )
}

private fun describeMap(
    hub: NetworkMapNode?,
    spokes: List<NetworkMapNode>,
): String {
    if (hub == null && spokes.isEmpty()) return "Network map, no devices discovered yet"
    val hubDescription = hub?.let { "gateway ${it.label} at the center" } ?: "no gateway identified"
    val spokeDescription =
        if (spokes.isEmpty()) {
            "no other devices"
        } else {
            "${spokes.size} devices around it: ${spokes.joinToString { it.label }}"
        }
    return "Network map, $hubDescription, $spokeDescription"
}
