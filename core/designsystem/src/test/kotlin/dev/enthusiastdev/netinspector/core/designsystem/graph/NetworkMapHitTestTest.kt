package dev.enthusiastdev.netinspector.core.designsystem.graph

import androidx.compose.ui.geometry.Offset
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NetworkMapHitTestTest {
    private val hub = "hub" to Offset(100f, 100f)
    private val spokes = listOf("spoke-a" to Offset(180f, 100f), "spoke-b" to Offset(20f, 100f))

    @Test
    fun `hitTestNode returns the hub when the tap lands on it`() {
        val result = hitTestNode(tap = Offset(102f, 100f), hub = hub, spokes = spokes, hitRadiusPx = 16f)
        assertThat(result).isEqualTo("hub")
    }

    @Test
    fun `hitTestNode returns the nearest spoke when the tap lands on it`() {
        val result = hitTestNode(tap = Offset(182f, 100f), hub = hub, spokes = spokes, hitRadiusPx = 16f)
        assertThat(result).isEqualTo("spoke-a")
    }

    @Test
    fun `hitTestNode returns null when the tap misses every node`() {
        val result = hitTestNode(tap = Offset(50f, 50f), hub = hub, spokes = spokes, hitRadiusPx = 16f)
        assertThat(result).isNull()
    }

    @Test
    fun `hitTestNode returns null with no hub and an empty spoke list`() {
        val result = hitTestNode(tap = Offset(100f, 100f), hub = null, spokes = emptyList(), hitRadiusPx = 16f)
        assertThat(result).isNull()
    }

    @Test
    fun `hitTestNode picks the closer node when two are within range`() {
        val closeHub = "hub" to Offset(100f, 100f)
        val closeSpokes = listOf("spoke" to Offset(115f, 100f))
        val result = hitTestNode(tap = Offset(105f, 100f), hub = closeHub, spokes = closeSpokes, hitRadiusPx = 20f)
        assertThat(result).isEqualTo("hub")
    }
}
