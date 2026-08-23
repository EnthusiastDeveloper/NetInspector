package dev.enthusiastdev.netinspector.core.designsystem.chart

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class OccupancyPaletteTest {
    @Test
    fun `every curve gets a distinct slot while the palette has room`() {
        // Seeds deliberately chosen to collide mod 8 - the reported bug was three overlapping
        // networks all rendering the same color.
        val slots = assignPaletteSlots(seeds = listOf(3, 11, 19, 27), paletteSize = 8)
        assertThat(slots).hasSize(4)
        assertThat(slots.toSet()).hasSize(4)
    }

    @Test
    fun `a seed keeps its preferred slot when nothing has taken it`() {
        assertThat(assignPaletteSlots(seeds = listOf(5), paletteSize = 8)).containsExactly(5)
    }

    @Test
    fun `a negative seed still lands inside the palette`() {
        val slots = assignPaletteSlots(seeds = listOf(-3, -11), paletteSize = 8)
        assertThat(slots).hasSize(2)
        slots.forEach { assertThat(it).isIn(0 until 8) }
        assertThat(slots.toSet()).hasSize(2)
    }

    @Test
    fun `colors start repeating only once the palette is exhausted`() {
        val slots = assignPaletteSlots(seeds = (0 until 10).toList(), paletteSize = 8)
        assertThat(slots.take(8).toSet()).hasSize(8)
        assertThat(slots).hasSize(10)
    }

    @Test
    fun `both palettes offer the same number of slots`() {
        assertThat(CURVE_PALETTE_DARK).hasSize(CURVE_PALETTE_LIGHT.size)
    }

    @Test
    fun `an empty palette is rejected rather than dividing by zero`() {
        assertThrows(IllegalArgumentException::class.java) { assignPaletteSlots(listOf(1), paletteSize = 0) }
    }
}
