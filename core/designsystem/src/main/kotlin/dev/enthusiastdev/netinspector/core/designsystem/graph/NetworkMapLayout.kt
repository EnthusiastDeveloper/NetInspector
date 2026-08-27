package dev.enthusiastdev.netinspector.core.designsystem.graph

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal const val DEFAULT_RING_CAPACITY = 6

/** [FULL_SIZE_SPOKE_CAPACITY] is how many spokes fit in the first two rings at
 * [DEFAULT_RING_CAPACITY] (6 + 12) - the density this map's fixed node/label sizes were
 * evidently tuned for. `availableRadiusPx` is a fixed budget shared across however many rings
 * a sweep's host count needs (see [networkMapOffsets]'s `ringSpacingPx`), so a busier network
 * needing a third or fourth ring compresses that budget without this - a real sweep of 30+
 * hosts (an ordinary home network's device count today) packed enough nodes at fixed size to
 * make labels fully illegible and unreadable at the map's default zoom, not just "a bit
 * tight" (see docs/ideas.md's driving bug report). Shrinking
 * proportionally to `sqrt(capacity / count)` rather than linearly keeps a moderately busy
 * network (say 25 hosts) close to full size while still meaningfully de-crowding a much
 * larger one, and [MIN_NODE_SCALE] keeps even a very large sweep's nodes tap-able rather than
 * shrinking to illegible dots - pinch-to-zoom (already supported) is still the way to inspect
 * a crowded area up close, this only fixes the unusable *default* view. */
internal const val FULL_SIZE_SPOKE_CAPACITY = DEFAULT_RING_CAPACITY * 3 // 18
internal const val MIN_NODE_SCALE = 0.5f

internal fun nodeScaleFor(spokeCount: Int): Float {
    if (spokeCount <= FULL_SIZE_SPOKE_CAPACITY) return 1f
    return sqrt(FULL_SIZE_SPOKE_CAPACITY.toFloat() / spokeCount).coerceIn(MIN_NODE_SCALE, 1f)
}

/** One spoke's position expressed as (ring, angle) rather than pixels, so it can be unit tested
 * without a `Canvas`/density and reused by both the draw pass and the tap-hit-test pass. */
internal data class RadialSlot(
    val ring: Int,
    val angleRadians: Float,
)

/** Assigns every spoke to a ring around the hub, packing more nodes into each successive ring
 * (`ringCapacity * (ring + 1)`) since a ring's circumference grows with its radius - a flat
 * per-ring capacity would leave outer rings sparse and inner rings crowded for the same spacing.
 * Within a ring, nodes are spread at even angular intervals starting from angle 0. */
internal fun computeRadialSlots(
    count: Int,
    ringCapacity: Int = DEFAULT_RING_CAPACITY,
): List<RadialSlot> {
    require(ringCapacity > 0) { "ringCapacity must be positive, was $ringCapacity" }
    if (count <= 0) return emptyList()

    val slots = ArrayList<RadialSlot>(count)
    var ring = 0
    var remaining = count
    while (remaining > 0) {
        val capacityThisRing = ringCapacity * (ring + 1)
        val countThisRing = minOf(remaining, capacityThisRing)
        repeat(countThisRing) { index ->
            slots += RadialSlot(ring, angleRadians = (2 * PI * index / countThisRing).toFloat())
        }
        remaining -= countThisRing
        ring++
    }
    return slots
}

/**
 * How far apart consecutive rings sit: evenly across [availableRadiusPx] (the room left for
 * spokes once [hubRadiusPx] is reserved at the center), but never tighter than
 * [minRingSpacingPx].
 *
 * That floor is what stops a busy network from collapsing into an unreadable knot. Fitting every
 * ring inside the viewport works up to a couple of dozen hosts and then stops: at 30+ the rings
 * are packed closer than a node is wide, nodes touch, and the labels underneath them overlap into
 * mush. Past that point the honest answer is that the map is bigger than the screen - so it is
 * drawn at a legible size and the viewport shows part of it, leaving the caller's pinch-to-zoom
 * to pull back to a full (if small) overview on demand.
 */
internal fun networkMapRingSpacingPx(
    hubRadiusPx: Float,
    availableRadiusPx: Float,
    ringCount: Int,
    minRingSpacingPx: Float = 0f,
): Float {
    if (ringCount <= 0) return 0f
    val fitted = (availableRadiusPx - hubRadiusPx).coerceAtLeast(0f) / ringCount
    return maxOf(fitted, minRingSpacingPx)
}

/** The radius the whole drawing occupies, so a caller can tell whether it currently overflows
 * its viewport and how far it may be panned. */
internal fun networkMapContentRadiusPx(
    hubRadiusPx: Float,
    nodeRadiusPx: Float,
    ringSpacingPx: Float,
    ringCount: Int,
): Float = hubRadiusPx + ringSpacingPx * ringCount + nodeRadiusPx

/** Converts [slots] to pixel offsets around [center], using [networkMapRingSpacingPx]. */
internal fun networkMapOffsets(
    center: Offset,
    hubRadiusPx: Float,
    availableRadiusPx: Float,
    slots: List<RadialSlot>,
    minRingSpacingPx: Float = 0f,
): List<Offset> {
    if (slots.isEmpty()) return emptyList()
    val ringCount = slots.maxOf { it.ring } + 1
    val ringSpacingPx = networkMapRingSpacingPx(hubRadiusPx, availableRadiusPx, ringCount, minRingSpacingPx)
    return slots.map { slot ->
        val radiusPx = hubRadiusPx + ringSpacingPx * (slot.ring + 1)
        Offset(
            x = center.x + radiusPx * cos(slot.angleRadians),
            y = center.y + radiusPx * sin(slot.angleRadians),
        )
    }
}

/** The number of rings [count] spokes will occupy - the caller needs this to size the drawing
 * before it has laid anything out. */
internal fun networkMapRingCount(
    count: Int,
    ringCapacity: Int = DEFAULT_RING_CAPACITY,
): Int = computeRadialSlots(count, ringCapacity).maxOfOrNull { it.ring }?.plus(1) ?: 0
