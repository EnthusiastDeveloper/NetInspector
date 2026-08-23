package dev.enthusiastdev.netinspector.core.designsystem.chart

import androidx.compose.ui.graphics.Color

/**
 * Hand-picked curve colors rather than a generated hue.
 *
 * Colors used to come from `Color.hsv(seed % 360, ...)` on a BSSID hash, which had two problems
 * a real scan runs into immediately: nothing stopped two APs landing on near-identical hues (the
 * reported symptom - three overlapping networks all drawn green), and an unconstrained hue wheel
 * includes stretches that read badly against the app's surfaces at a translucent fill.
 *
 * These are Okabe-Ito-derived: eight hues chosen to stay distinguishable from one another
 * including for the common forms of color-vision deficiency, in a light and a dark variant so the
 * curves keep their contrast against either surface.
 */
internal val CURVE_PALETTE_LIGHT =
    listOf(
        Color(0xFF0072B2), // blue
        Color(0xFFD55E00), // vermillion
        Color(0xFF009E73), // bluish green
        Color(0xFFCC79A7), // reddish purple
        Color(0xFF6A4C93), // violet
        Color(0xFF3AA6D0), // sky blue
        Color(0xFFB07302), // ochre
        Color(0xFF8C564B), // brown
    )

internal val CURVE_PALETTE_DARK =
    listOf(
        Color(0xFF7FB8FF), // blue
        Color(0xFFFFB067), // orange
        Color(0xFF5FD3A6), // green
        Color(0xFFF2A2C6), // pink
        Color(0xFFB9A5FF), // violet
        Color(0xFF6FE0E8), // cyan
        Color(0xFFE9D372), // yellow
        Color(0xFFD9A38C), // tan
    )

/**
 * Picks a palette slot per curve, in the order given, so no two curves drawn together share a
 * color while there are still unused slots.
 *
 * Each seed proposes its own slot (so a given AP keeps the same color from one scan to the next
 * as long as its neighbours don't change), and a taken slot is resolved by walking forward to the
 * next free one - the standard linear-probe. Beyond [paletteSize] curves the colors have to start
 * repeating; the walk wraps rather than failing, and the repeats are at least spread evenly
 * instead of clustering.
 */
internal fun assignPaletteSlots(
    seeds: List<Int>,
    paletteSize: Int,
): List<Int> {
    require(paletteSize > 0) { "paletteSize must be positive, was $paletteSize" }
    val taken = HashSet<Int>()
    return seeds.map { seed ->
        if (taken.size >= paletteSize) taken.clear()
        val preferred = ((seed % paletteSize) + paletteSize) % paletteSize
        var slot = preferred
        var probe = 0
        while (probe < paletteSize && slot in taken) {
            probe++
            slot = (preferred + probe) % paletteSize
        }
        taken += slot
        slot
    }
}
