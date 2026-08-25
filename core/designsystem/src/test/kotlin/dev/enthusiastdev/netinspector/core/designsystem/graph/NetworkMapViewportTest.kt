package dev.enthusiastdev.netinspector.core.designsystem.graph

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** [NetworkMapViewport.transformedBy] backs the network map graph's pinch-zoom-and-pan
 * interaction (docs/testing.md §4). Unlike [hitTestNode], this maths lived inline in the
 * composable with no direct test before this file. */
class NetworkMapViewportTest {
    @Test
    fun `transformedBy clamps scale at the zoomed-in ceiling`() {
        val viewport = NetworkMapViewport(scale = MAX_SCALE)

        val result =
            viewport.transformedBy(
                pan = Offset.Zero,
                zoom = 2f,
                contentRadiusPx = 500f,
                containerSize = IntSize(400, 400),
            )

        assertThat(result.scale).isEqualTo(MAX_SCALE)
    }

    @Test
    fun `transformedBy clamps scale at the zoomed-out floor`() {
        val viewport = NetworkMapViewport(scale = MIN_SCALE)

        val result =
            viewport.transformedBy(
                pan = Offset.Zero,
                zoom = 0.1f,
                contentRadiusPx = 500f,
                containerSize = IntSize(400, 400),
            )

        assertThat(result.scale).isEqualTo(MIN_SCALE)
    }

    @Test
    fun `transformedBy clamps pan to how far the oversized content extends past the viewport`() {
        // A drawing twice the container's radius at 1x scale can be dragged, but only until its
        // edge reaches the viewport edge - never further, or the map would drift off entirely.
        val viewport = NetworkMapViewport(scale = 1f)

        val result =
            viewport.transformedBy(
                pan = Offset(10_000f, 10_000f),
                zoom = 1f,
                contentRadiusPx = 300f,
                containerSize = IntSize(400, 400),
            )

        val expectedMaxOffset = 300f - 400f / 2f
        assertThat(result.offset.x).isEqualTo(expectedMaxOffset)
        assertThat(result.offset.y).isEqualTo(expectedMaxOffset)
    }

    @Test
    fun `transformedBy collapses pan to zero once zoomed-out content is smaller than the viewport`() {
        // A drawing that fits entirely within the viewport has nowhere to pan to - the max
        // offset coerces to 0 rather than going negative, so any pan attempt is fully absorbed.
        val viewport = NetworkMapViewport(scale = 1f, offset = Offset(50f, 50f))

        val result =
            viewport.transformedBy(
                pan = Offset(20f, 20f),
                zoom = 1f,
                contentRadiusPx = 100f,
                containerSize = IntSize(400, 400),
            )

        assertThat(result.offset).isEqualTo(Offset.Zero)
    }

    @Test
    fun `transformedBy applies zoom and pan from one gesture callback together`() {
        // A real two-finger gesture reports zoom and pan in the same callback invocation, not
        // as two separate steps - the maths has to combine them in one call.
        val viewport = NetworkMapViewport(scale = 1f, offset = Offset.Zero)

        val result =
            viewport.transformedBy(
                pan = Offset(5f, 0f),
                zoom = 1.5f,
                contentRadiusPx = 300f,
                containerSize = IntSize(400, 400),
            )

        assertThat(result.scale).isEqualTo(1.5f)
        assertThat(result.offset.x).isEqualTo(5f)
        assertThat(result.offset.y).isEqualTo(0f)
    }
}
