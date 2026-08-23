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

    @Test
    fun `nodeScaleFor stays at full size within the first two rings' capacity`() {
        assertThat(nodeScaleFor(0)).isEqualTo(1f)
        assertThat(nodeScaleFor(FULL_SIZE_SPOKE_CAPACITY)).isEqualTo(1f)
    }

    @Test
    fun `nodeScaleFor shrinks once spoke count exceeds the first two rings' capacity`() {
        val scale = nodeScaleFor(FULL_SIZE_SPOKE_CAPACITY * 2)
        assertThat(scale).isLessThan(1f)
        assertThat(scale).isWithin(0.001f).of(kotlin.math.sqrt(0.5f))
    }

    @Test
    fun `nodeScaleFor never shrinks below the minimum even for a very large sweep`() {
        assertThat(nodeScaleFor(10_000)).isEqualTo(MIN_NODE_SCALE)
    }

    @Test
    fun `ring spacing fits the viewport when there is room to spare`() {
        val spacing =
            networkMapRingSpacingPx(hubRadiusPx = 20f, availableRadiusPx = 120f, ringCount = 2, minRingSpacingPx = 10f)
        assertThat(spacing).isWithin(0.01f).of(50f)
    }

    @Test
    fun `ring spacing never drops below the readable minimum`() {
        // Five rings of hosts in a viewport that could only give them 16px each - the dense-map
        // case where fitting everything on screen made the nodes unreadable.
        val spacing =
            networkMapRingSpacingPx(hubRadiusPx = 20f, availableRadiusPx = 100f, ringCount = 5, minRingSpacingPx = 55f)
        assertThat(spacing).isWithin(0.01f).of(55f)
    }

    @Test
    fun `ring spacing is zero when there are no rings to space`() {
        assertThat(networkMapRingSpacingPx(hubRadiusPx = 20f, availableRadiusPx = 100f, ringCount = 0))
            .isWithin(0.01f)
            .of(0f)
    }

    @Test
    fun `a crowded map is laid out larger than the viewport that has to show it`() {
        val ringCount = networkMapRingCount(count = 40)
        val spacing =
            networkMapRingSpacingPx(
                hubRadiusPx = 20f,
                availableRadiusPx = 200f,
                ringCount = ringCount,
                minRingSpacingPx = 55f,
            )
        val contentRadius = networkMapContentRadiusPx(20f, 11f, spacing, ringCount)
        assertThat(contentRadius).isGreaterThan(200f)
    }

    @Test
    fun `networkMapRingCount matches the rings the slots actually occupy`() {
        assertThat(networkMapRingCount(0)).isEqualTo(0)
        assertThat(networkMapRingCount(count = 6, ringCapacity = 6)).isEqualTo(1)
        assertThat(networkMapRingCount(count = 7, ringCapacity = 6)).isEqualTo(2)
    }

    @Test
    fun `the minimum spacing pushes outer rings further out than a pure fit would`() {
        val center = Offset(0f, 0f)
        val slots = listOf(RadialSlot(ring = 1, angleRadians = 0f))
        val fitted = networkMapOffsets(center, hubRadiusPx = 20f, availableRadiusPx = 80f, slots = slots)
        val spaced =
            networkMapOffsets(
                center,
                hubRadiusPx = 20f,
                availableRadiusPx = 80f,
                slots = slots,
                minRingSpacingPx = 60f,
            )
        assertThat(spaced.single().x).isGreaterThan(fitted.single().x)
    }
}
