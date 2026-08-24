package dev.enthusiastdev.netinspector.core.designsystem.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Rule
import org.junit.Test

/** Regression test for the crash fixed alongside [AxisMapperTest]'s boundary-precondition test:
 * zooming the channel graph to [MAX_AXIS_ZOOM] while a curve sits well outside the visible slice
 * used to crash with `IllegalArgumentException: maxWidth must be >= than minWidth` out of
 * `drawText`, because `drawCurveLabels` placed the label's x past the canvas edge with nothing
 * to catch it. [AxisMapperTest] proves the pure-math precondition (`xPx` landing past the canvas
 * width) is real; this exercises the actual drawing code path so the `x > size.width` guard in
 * `drawCurveLabels` is proven to prevent the crash, not just assumed to. Needs a device or
 * emulator to run (`connectedDebugAndroidTest`) - no Wi-Fi radio required, this is pure Canvas
 * rendering, same as the rest of the layout test surface design.md §12 carves out for emulators. */
class ChannelOccupancyGraphRenderTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `a curve far outside a max-zoom viewport does not crash the label pass`() {
        val farOutsideCurve =
            OccupancyCurve(
                primary = OccupancySpan(lowMhz = 5865, centerMhz = 5875, highMhz = 5885),
                secondary = null,
                rssiDbm = -50,
                label = "Far Network",
                colorSeed = 1,
            )

        composeTestRule.setContent {
            NarrowZoomedGraph(curves = listOf(farOutsideCurve))
        }

        composeTestRule.waitForIdle()
    }

    @Composable
    private fun NarrowZoomedGraph(curves: List<OccupancyCurve>) {
        val textMeasurer = rememberTextMeasurer()
        val labelStyle = TextStyle(fontSize = 10.sp)
        val labelStyles = LabelStyles(default = labelStyle, highlighted = labelStyle)

        Canvas(modifier = Modifier.fillMaxWidth().height(220.dp)) {
            // A MAX_AXIS_ZOOM-narrow window on the 5 GHz band - the same reproduction of the
            // crash precondition AxisMapperTest uses, rendered here instead of just measured.
            val mapper =
                AxisMapper(
                    axisLowMhz = 5500,
                    axisHighMhz = 5500 + (750f / MAX_AXIS_ZOOM).toInt(),
                    widthPx = size.width,
                    topInsetPx = 18.dp.toPx(),
                    bottomInsetPx = 28.dp.toPx(),
                    heightPx = size.height,
                )
            val paint =
                GraphPaint(
                    gridColor = Color.Gray,
                    textMeasurer = textMeasurer,
                    labelStyles = labelStyles,
                    curveColors = emptyMap(),
                )
            drawOccupancyGraph(
                curves = curves,
                highlightedKey = null,
                mapper = mapper,
                minTickSpacingPx = 56.dp.toPx(),
                paint = paint,
            )
        }
    }
}
