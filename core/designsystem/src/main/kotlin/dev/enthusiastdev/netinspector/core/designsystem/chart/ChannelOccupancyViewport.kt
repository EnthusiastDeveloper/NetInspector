package dev.enthusiastdev.netinspector.core.designsystem.chart

/** Zooming past this stops being useful: 5 GHz's 750 MHz axis is already down to ~47 MHz across
 * the canvas, narrower than a single 80 MHz curve. */
internal const val MAX_AXIS_ZOOM = 16f

/**
 * Which slice of the frequency axis the graph is currently showing, as a zoom factor plus the
 * MHz the view is centred on.
 *
 * The graph zooms by *remapping its X axis* rather than by scaling the drawn canvas: a
 * `graphicsLayer` scale would blow up the axis labels, SSID labels and stroke widths along with
 * the curves, and turn a 4x zoom into a blurry, unreadable version of the same picture. Narrowing
 * the axis range instead keeps every label at its normal size and simply spreads the curves out -
 * which is the entire point of zooming into a crowded 2.4 GHz band.
 *
 * Kept as a plain value type with its arithmetic in [transformedBy] so the pinch maths can be
 * unit tested without a `Canvas`, a density or a gesture.
 */
internal data class AxisViewport(
    val zoom: Float = 1f,
    val centerMhz: Float = 0f,
) {
    fun spanMhz(
        fullLowMhz: Int,
        fullHighMhz: Int,
    ): Float = (fullHighMhz - fullLowMhz).coerceAtLeast(1) / zoom.coerceAtLeast(1f)

    fun lowMhz(
        fullLowMhz: Int,
        fullHighMhz: Int,
    ): Int = clampedCenter(fullLowMhz, fullHighMhz).let { it - spanMhz(fullLowMhz, fullHighMhz) / 2f }.toInt()

    fun highMhz(
        fullLowMhz: Int,
        fullHighMhz: Int,
    ): Int = lowMhz(fullLowMhz, fullHighMhz) + spanMhz(fullLowMhz, fullHighMhz).toInt()

    /** A view narrower than the band can slide, but never off the ends of it - so zooming out
     * always lands back on exactly the full band rather than somewhere beside it. */
    private fun clampedCenter(
        fullLowMhz: Int,
        fullHighMhz: Int,
    ): Float {
        val halfSpan = spanMhz(fullLowMhz, fullHighMhz) / 2f
        val lowest = fullLowMhz + halfSpan
        val highest = fullHighMhz - halfSpan
        return if (lowest >= highest) (fullLowMhz + fullHighMhz) / 2f else centerMhz.coerceIn(lowest, highest)
    }
}

/** The viewport a pinch/drag gesture produces, given where the fingers are.
 *
 * [focusFraction] is the gesture centroid as a 0..1 position across the canvas; the frequency
 * under that point is held still while the range narrows around it, which is what makes a pinch
 * feel like it is grabbing the chart rather than nudging a zoom level. [panMhz] then slides the
 * result, so a two-finger drag also works while zoomed in. */
internal fun AxisViewport.transformedBy(
    fullLowMhz: Int,
    fullHighMhz: Int,
    focusFraction: Float,
    zoomFactor: Float,
    panFraction: Float,
): AxisViewport {
    val oldSpan = spanMhz(fullLowMhz, fullHighMhz)
    val oldLow = lowMhz(fullLowMhz, fullHighMhz).toFloat()
    val newZoom = (zoom * zoomFactor).coerceIn(1f, MAX_AXIS_ZOOM)
    val newSpan = (fullHighMhz - fullLowMhz).coerceAtLeast(1) / newZoom

    val focusMhz = oldLow + focusFraction.coerceIn(0f, 1f) * oldSpan
    val panMhz = -panFraction * newSpan
    val newCenter = focusMhz - (focusFraction.coerceIn(0f, 1f) - 0.5f) * newSpan + panMhz
    return AxisViewport(zoom = newZoom, centerMhz = newCenter)
}
