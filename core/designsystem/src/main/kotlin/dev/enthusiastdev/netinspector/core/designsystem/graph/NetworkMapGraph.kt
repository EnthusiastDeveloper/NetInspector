package dev.enthusiastdev.netinspector.core.designsystem.graph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp

private const val CANVAS_HEIGHT_DP = 320
private const val HUB_RADIUS_DP = 28f
private const val NODE_RADIUS_DP = 16f
private const val LABEL_OFFSET_DP = 4f

/**
 * A hub-and-spoke visualization of a *logical* network - who was discovered around the gateway,
 * not real switch-level wiring (unavailable without SNMP/LLDP). [hub] is drawn at the center;
 * [spokes] ring it, packed into concentric rings by [computeRadialSlots] as their count grows.
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
    val hubColor = MaterialTheme.colorScheme.primary
    val selfColor = MaterialTheme.colorScheme.tertiary
    val atRiskColor = MaterialTheme.colorScheme.error
    val normalColor = MaterialTheme.colorScheme.secondary
    val spokeLineColor = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val textMeasurer = rememberTextMeasurer()

    val hubRadiusPx = with(LocalDensity.current) { HUB_RADIUS_DP.dp.toPx() }
    val nodeRadiusPx = with(LocalDensity.current) { NODE_RADIUS_DP.dp.toPx() }

    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .height(CANVAS_HEIGHT_DP.dp)
                .pointerInput(hub, spokes) {
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
                                spokes = geometry.spokeOffsets.mapIndexed { i, offset -> spokes[i].id to offset },
                                hitRadiusPx = nodeRadiusPx,
                            )
                        hitId?.let(onNodeClick)
                    }
                }.clearAndSetSemantics { contentDescription = describeMap(hub, spokes) },
    ) {
        val geometry = NetworkMapGeometry(size.width, size.height, hubRadiusPx, nodeRadiusPx, spokes)

        geometry.spokeOffsets.forEach { offset ->
            drawLine(spokeLineColor, geometry.center, offset, strokeWidth = 1.dp.toPx())
        }
        if (hub != null) {
            drawCircle(hubColor, radius = hubRadiusPx, center = geometry.center)
            drawNodeLabel(textMeasurer, hub.label, geometry.center, hubRadiusPx, labelStyle)
        }
        spokes.forEachIndexed { index, node ->
            val offset = geometry.spokeOffsets[index]
            val color =
                if (node.isAtRisk) {
                    atRiskColor
                } else if (node.isSelf) {
                    selfColor
                } else {
                    normalColor
                }
            drawCircle(color, radius = nodeRadiusPx, center = offset)
            drawNodeLabel(textMeasurer, node.label, offset, nodeRadiusPx, labelStyle)
        }
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
    val measured = textMeasurer.measure(label, style)
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
