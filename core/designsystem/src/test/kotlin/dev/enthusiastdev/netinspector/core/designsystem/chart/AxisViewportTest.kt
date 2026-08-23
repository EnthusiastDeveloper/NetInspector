package dev.enthusiastdev.netinspector.core.designsystem.chart

import com.google.common.truth.Truth.assertThat
import org.junit.Test

private const val BAND_LOW = 2400
private const val BAND_HIGH = 2500

class AxisViewportTest {
    @Test
    fun `an untouched viewport shows the whole band`() {
        val viewport = AxisViewport()
        assertThat(viewport.lowMhz(BAND_LOW, BAND_HIGH)).isEqualTo(BAND_LOW)
        assertThat(viewport.highMhz(BAND_LOW, BAND_HIGH)).isEqualTo(BAND_HIGH)
    }

    @Test
    fun `pinching in halves the visible span`() {
        val zoomed =
            AxisViewport().transformedBy(
                fullLowMhz = BAND_LOW,
                fullHighMhz = BAND_HIGH,
                focusFraction = 0.5f,
                zoomFactor = 2f,
                panFraction = 0f,
            )
        assertThat(zoomed.spanMhz(BAND_LOW, BAND_HIGH)).isWithin(0.01f).of(50f)
        assertThat(zoomed.lowMhz(BAND_LOW, BAND_HIGH)).isEqualTo(2425)
        assertThat(zoomed.highMhz(BAND_LOW, BAND_HIGH)).isEqualTo(2475)
    }

    @Test
    fun `the frequency under the pinch centroid stays put`() {
        // Centroid at 25% across the full band is 2425 MHz; after zooming it must still sit at
        // 25% across the narrower view, which is what makes the gesture feel anchored.
        val zoomed =
            AxisViewport().transformedBy(
                fullLowMhz = BAND_LOW,
                fullHighMhz = BAND_HIGH,
                focusFraction = 0.25f,
                zoomFactor = 4f,
                panFraction = 0f,
            )
        val span = zoomed.spanMhz(BAND_LOW, BAND_HIGH)
        val underCentroid = zoomed.lowMhz(BAND_LOW, BAND_HIGH) + 0.25f * span
        // Tolerance covers the axis being addressed in whole MHz - the viewport's edges are
        // truncated to integers before they reach the mapper.
        assertThat(underCentroid).isWithin(1.5f).of(2425f)
    }

    @Test
    fun `panning while zoomed in slides the view along the axis`() {
        val zoomed =
            AxisViewport(zoom = 4f, centerMhz = 2450f)
                .transformedBy(
                    fullLowMhz = BAND_LOW,
                    fullHighMhz = BAND_HIGH,
                    focusFraction = 0.5f,
                    zoomFactor = 1f,
                    // A finger dragging right by a quarter of the canvas reveals lower frequencies.
                    panFraction = 0.25f,
                )
        assertThat(zoomed.centerMhz).isLessThan(2450f)
        assertThat(zoomed.zoom).isWithin(0.01f).of(4f)
    }

    @Test
    fun `the view never slides off the end of the band`() {
        val panned =
            AxisViewport(zoom = 4f, centerMhz = 2450f)
                .transformedBy(BAND_LOW, BAND_HIGH, focusFraction = 0.5f, zoomFactor = 1f, panFraction = 10f)
        assertThat(panned.lowMhz(BAND_LOW, BAND_HIGH)).isAtLeast(BAND_LOW)
        assertThat(panned.highMhz(BAND_LOW, BAND_HIGH)).isAtMost(BAND_HIGH)
    }

    @Test
    fun `zooming out never goes below the full band`() {
        val zoomedOut =
            AxisViewport(zoom = 2f, centerMhz = 2450f)
                .transformedBy(BAND_LOW, BAND_HIGH, focusFraction = 0.5f, zoomFactor = 0.01f, panFraction = 0f)
        assertThat(zoomedOut.zoom).isWithin(0.001f).of(1f)
        assertThat(zoomedOut.lowMhz(BAND_LOW, BAND_HIGH)).isEqualTo(BAND_LOW)
        assertThat(zoomedOut.highMhz(BAND_LOW, BAND_HIGH)).isEqualTo(BAND_HIGH)
    }

    @Test
    fun `zooming in stops at the maximum`() {
        val zoomedIn =
            AxisViewport(zoom = MAX_AXIS_ZOOM, centerMhz = 2450f)
                .transformedBy(BAND_LOW, BAND_HIGH, focusFraction = 0.5f, zoomFactor = 100f, panFraction = 0f)
        assertThat(zoomedIn.zoom).isWithin(0.001f).of(MAX_AXIS_ZOOM)
    }
}
