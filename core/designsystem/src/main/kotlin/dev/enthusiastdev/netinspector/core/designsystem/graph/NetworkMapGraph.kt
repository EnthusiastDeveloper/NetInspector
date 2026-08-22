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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private const val HUB_RADIUS_DP = 28f
private const val NODE_RADIUS_DP = 16f
private const val LABEL_OFFSET_DP = 4f
private const val MIN_SCALE = 1f
private const val MAX_SCALE = 4f

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
 * Pinch-to-zoom/pan (via `detectTransformGestures`) is the main way to pull crowded labels apart
 * once a real sweep's host count packs several rings tightly - a fixed extra layout pass to avoid
 * every possible collision would fight the radial layout's whole point (position mirrors ring/
 * angle, not label width), so letting the user zoom into a crowded area is the more honest fix.
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
    var scale by remember { mutableStateOf(MIN_SCALE) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier =
            modifier
                .clipToBounds()
                .onSizeChanged { containerSize = it }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                        val maxOffsetX = (containerSize.width * (newScale - 1) / 2f).coerceAtLeast(0f)
                        val maxOffsetY = (containerSize.height * (newScale - 1) / 2f).coerceAtLeast(0f)
                        offset =
                            Offset(
                                x = (offset.x + pan.x).coerceIn(-maxOffsetX, maxOffsetX),
                                y = (offset.y + pan.y).coerceIn(-maxOffsetY, maxOffsetY),
                            )
                        scale = newScale
                    }
                },
    ) {
        NetworkMapCanvas(hub, spokes, onNodeClick, scale, offset)
    }
}

@Composable
private fun NetworkMapCanvas(
    hub: NetworkMapNode?,
    spokes: List<NetworkMapNode>,
    onNodeClick: (String) -> Unit,
    scale: Float,
    offset: Offset,
) {
    val paint =
        NetworkMapPaint(
            textMeasurer = rememberTextMeasurer(),
            labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
            colors = networkMapColors(),
            hubRadiusPx = with(LocalDensity.current) { HUB_RADIUS_DP.dp.toPx() },
            nodeRadiusPx = with(LocalDensity.current) { NODE_RADIUS_DP.dp.toPx() },
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
        drawCircle(paint.colors.hub, radius = paint.hubRadiusPx, center = geometry.center)
        drawNodeLabel(paint.textMeasurer, hub.label, geometry.center, paint.hubRadiusPx, paint.labelStyle)
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
        drawCircle(color, radius = paint.nodeRadiusPx, center = spokeOffset)
        drawNodeLabel(paint.textMeasurer, node.label, spokeOffset, paint.nodeRadiusPx, paint.labelStyle)
    }
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
        spokeOffsets = networkMapOffsets(center, hubRadiusPx, availableRadiusPx, slots)
    }
}

private fun DrawScope.drawNodeLabel(
    textMeasurer: TextMeasurer,
    label: String,
    nodeCenter: Offset,
    nodeRadiusPx: Float,
    style: TextStyle,
) {
    val measured =
        textMeasurer.measure(
            text = label,
            style = style,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
            maxLines = 1,
            constraints = Constraints(maxWidth = LABEL_MAX_WIDTH_DP.dp.toPx().roundToInt()),
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
