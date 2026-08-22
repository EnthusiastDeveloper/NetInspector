package dev.enthusiastdev.netinspector.core.designsystem.graph

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal const val DEFAULT_RING_CAPACITY = 6

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

/** Converts [slots] to pixel offsets around [center], spacing rings evenly across
 * [availableRadiusPx] (the room left for spokes once [hubRadiusPx] is reserved at the center). */
internal fun networkMapOffsets(
    center: Offset,
    hubRadiusPx: Float,
    availableRadiusPx: Float,
    slots: List<RadialSlot>,
): List<Offset> {
    if (slots.isEmpty()) return emptyList()
    val ringCount = slots.maxOf { it.ring } + 1
    val ringSpacingPx = (availableRadiusPx - hubRadiusPx).coerceAtLeast(0f) / ringCount
    return slots.map { slot ->
        val radiusPx = hubRadiusPx + ringSpacingPx * (slot.ring + 1)
        Offset(
            x = center.x + radiusPx * cos(slot.angleRadians),
            y = center.y + radiusPx * sin(slot.angleRadians),
        )
    }
}
