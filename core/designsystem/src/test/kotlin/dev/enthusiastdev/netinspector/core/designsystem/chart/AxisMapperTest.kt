package dev.enthusiastdev.netinspector.core.designsystem.chart

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AxisMapperTest {
    /** Regression coverage for the crash this precondition caused: `drawCurveLabels` places a
     * label at `mapper.xPx(curve.centerMhz) + inset`, on the assumption a curve well outside the
     * visible axis range - which, per `ChannelOccupancyGraph`'s "curves outside the zoomed range
     * are still drawn" contract, is a normal state at [MAX_AXIS_ZOOM] - can land past the canvas
     * edge. `xPx` is deliberately unbounded (it must be, to place a point that is genuinely
     * off-canvas), so any caller that feeds it into a `drawText` constraint has to check the
     * result itself rather than assume it is clamped - which is exactly what the `x > size.width`
     * guard in `drawCurveLabels` does. This test exists so that guard's precondition cannot
     * silently stop being true out from under it. */
    @Test
    fun `xPx is unbounded - a curve far outside a zoomed-in viewport lands past the canvas edge`() {
        val widthPx = 1000f
        // A narrow axis window representative of a MAX_AXIS_ZOOM viewport on the 750 MHz-wide
        // 5 GHz band: 750 / MAX_AXIS_ZOOM is under 47 MHz across the whole canvas width.
        val mapper =
            AxisMapper(
                axisLowMhz = 5500,
                axisHighMhz = 5500 + (750f / MAX_AXIS_ZOOM).toInt(),
                widthPx = widthPx,
                topInsetPx = 0f,
                bottomInsetPx = 0f,
                heightPx = 100f,
            )

        // A curve near the opposite edge of the full band - still drawn, just nowhere near this
        // narrow window.
        val x = mapper.xPx(5875)

        assertThat(x).isGreaterThan(widthPx)
    }

    @Test
    fun `yPx stays within the plot area even for a dBm value outside the graph's domain`() {
        val mapper =
            AxisMapper(
                axisLowMhz = 2400,
                axisHighMhz = 2483,
                widthPx = 1000f,
                topInsetPx = 10f,
                bottomInsetPx = 10f,
                heightPx = 200f,
            )

        assertThat(mapper.yPx(-200f)).isEqualTo(mapper.bottomPx)
        assertThat(mapper.yPx(0f)).isEqualTo(mapper.topPx)
    }
}
