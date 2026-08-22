package dev.enthusiastdev.netinspector.core.designsystem.graph

import androidx.compose.ui.geometry.Offset
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.math.PI

class NetworkMapLayoutTest {
    @Test
    fun `computeRadialSlots returns nothing for zero or negative count`() {
        assertThat(computeRadialSlots(0)).isEmpty()
        assertThat(computeRadialSlots(-3)).isEmpty()
    }

    @Test
    fun `computeRadialSlots packs every node into the first ring while under capacity`() {
        val slots = computeRadialSlots(count = 4, ringCapacity = 6)
        assertThat(slots).hasSize(4)
        assertThat(slots.map { it.ring }).containsExactly(0, 0, 0, 0)
    }

    @Test
    fun `computeRadialSlots spreads a full ring's nodes at even angular intervals`() {
        val slots = computeRadialSlots(count = 4, ringCapacity = 4)
        assertThat(slots.map { it.angleRadians })
            .containsExactly(0f, (PI / 2).toFloat(), PI.toFloat(), (3 * PI / 2).toFloat())
            .inOrder()
    }

    @Test
    fun `computeRadialSlots spills into a second ring once the first fills up`() {
        val slots = computeRadialSlots(count = 8, ringCapacity = 6)
        // Ring 0 holds 6 (its full capacity), the remaining 2 spill into ring 1.
        assertThat(slots.count { it.ring == 0 }).isEqualTo(6)
        assertThat(slots.count { it.ring == 1 }).isEqualTo(2)
    }

    @Test
    fun `computeRadialSlots rejects a non-positive ring capacity`() {
        assertThrows(IllegalArgumentException::class.java) { computeRadialSlots(count = 1, ringCapacity = 0) }
    }

    @Test
    fun `networkMapOffsets returns nothing for no slots`() {
        val offsets =
            networkMapOffsets(Offset(100f, 100f), hubRadiusPx = 20f, availableRadiusPx = 80f, slots = emptyList())
        assertThat(offsets).isEmpty()
    }

    @Test
    fun `networkMapOffsets places a single ring-0 node at angle 0 directly right of center`() {
        val center = Offset(100f, 100f)
        val slots = listOf(RadialSlot(ring = 0, angleRadians = 0f))
        val offsets = networkMapOffsets(center, hubRadiusPx = 20f, availableRadiusPx = 80f, slots = slots)
        // One ring spans the whole available radius, so the sole node sits at hub + full spacing.
        assertThat(offsets.single().x).isWithin(0.01f).of(180f)
        assertThat(offsets.single().y).isWithin(0.01f).of(100f)
    }

    @Test
    fun `networkMapOffsets places outer-ring nodes farther from center than inner-ring nodes`() {
        val center = Offset(100f, 100f)
        val slots = listOf(RadialSlot(ring = 0, angleRadians = 0f), RadialSlot(ring = 1, angleRadians = 0f))
        val offsets = networkMapOffsets(center, hubRadiusPx = 20f, availableRadiusPx = 80f, slots = slots)
        val innerDistance = offsets[0].x - center.x
        val outerDistance = offsets[1].x - center.x
        assertThat(outerDistance).isGreaterThan(innerDistance)
    }
}
