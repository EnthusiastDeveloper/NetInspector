package dev.enthusiastdev.netinspector.core.designsystem.chart

import androidx.compose.ui.geometry.Offset
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** [hitTestCurve] backs the channel occupancy graph's tap-to-highlight interaction (docs
 * §12 -> docs/testing.md §4). Unlike [AxisViewport]'s pinch/pan maths, this pure function had
 * no direct test before this file - only indirect coverage through the composable itself. */
class ChannelOccupancyGraphHitTestTest {
    private val mapper =
        AxisMapper(
            axisLowMhz = 2400,
            axisHighMhz = 2500,
            widthPx = 1000f,
            topInsetPx = 0f,
            bottomInsetPx = 0f,
            heightPx = 100f,
        )

    private fun curveAt(
        offsetMhz: Int,
        curve: OccupancyCurve,
    ): Offset {
        val span = curve.primary
        val halfWidth = (span.highMhz - span.lowMhz) / 2f
        val t = (offsetMhz - span.centerMhz) / halfWidth
        val dbm = curve.rssiDbm - (curve.rssiDbm - Y_MIN_DBM) * (t * t)
        return Offset(mapper.xPx(offsetMhz), mapper.yPx(dbm))
    }

    @Test
    fun `hitTestCurve returns the curve when the tap lands on its primary span's peak`() {
        val curve =
            OccupancyCurve(
                primary = OccupancySpan(lowMhz = 2430, centerMhz = 2450, highMhz = 2470),
                secondary = null,
                rssiDbm = -40,
                label = "AP-A",
                colorSeed = 0,
            )

        val result = hitTestCurve(listOf(curve), curveAt(2450, curve), mapper)

        assertThat(result).isEqualTo("AP-A")
    }

    @Test
    fun `hitTestCurve hits the secondary span of an 80+80 curve`() {
        val curve =
            OccupancyCurve(
                primary = OccupancySpan(lowMhz = 2400, centerMhz = 2410, highMhz = 2420),
                secondary = OccupancySpan(lowMhz = 2460, centerMhz = 2470, highMhz = 2480),
                rssiDbm = -50,
                label = "AP-80+80",
                colorSeed = 0,
            )
        val secondaryPeak =
            Offset(mapper.xPx(2470), mapper.yPx(curve.rssiDbm.toFloat()))

        val result = hitTestCurve(listOf(curve), secondaryPeak, mapper)

        assertThat(result).isEqualTo("AP-80+80")
    }

    @Test
    fun `hitTestCurve picks whichever overlapping curve's edge sits closer to the tap`() {
        val curveA =
            OccupancyCurve(
                primary = OccupancySpan(lowMhz = 2430, centerMhz = 2450, highMhz = 2470),
                secondary = null,
                rssiDbm = -40,
                label = "AP-A",
                colorSeed = 0,
            )
        val curveB =
            OccupancyCurve(
                primary = OccupancySpan(lowMhz = 2440, centerMhz = 2460, highMhz = 2480),
                secondary = null,
                rssiDbm = -70,
                label = "AP-B",
                colorSeed = 1,
            )
        // At 2460 MHz both spans are in range; nudge the tap one pixel above B's exact curve
        // point (and well away from A's) so B is unambiguously the closer edge.
        val tapNearB = curveAt(2460, curveB).copy(y = curveAt(2460, curveB).y - 1f)

        val result = hitTestCurve(listOf(curveA, curveB), tapNearB, mapper)

        assertThat(result).isEqualTo("AP-B")
    }

    @Test
    fun `hitTestCurve returns null when the tap misses every curve's span`() {
        val curve =
            OccupancyCurve(
                primary = OccupancySpan(lowMhz = 2430, centerMhz = 2450, highMhz = 2470),
                secondary = null,
                rssiDbm = -40,
                label = "AP-A",
                colorSeed = 0,
            )
        // 2500 MHz sits at the axis's own edge, well outside the curve's 2430-2470 span.
        val tapOutsideSpan = Offset(mapper.xPx(2500), mapper.yPx(-40f))

        val result = hitTestCurve(listOf(curve), tapOutsideSpan, mapper)

        assertThat(result).isNull()
    }

    @Test
    fun `hitTestCurve hits a tap landing exactly on a span boundary`() {
        val curve =
            OccupancyCurve(
                primary = OccupancySpan(lowMhz = 2430, centerMhz = 2450, highMhz = 2470),
                secondary = null,
                rssiDbm = -40,
                label = "AP-A",
                colorSeed = 0,
            )

        val result = hitTestCurve(listOf(curve), curveAt(2430, curve), mapper)

        assertThat(result).isEqualTo("AP-A")
    }
}
