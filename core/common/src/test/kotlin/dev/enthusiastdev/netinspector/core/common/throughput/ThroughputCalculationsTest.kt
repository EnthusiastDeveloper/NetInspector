package dev.enthusiastdev.netinspector.core.common.throughput

import com.google.common.truth.Truth.assertThat
import dev.enthusiastdev.netinspector.core.model.wifi.Band
import dev.enthusiastdev.netinspector.core.model.wifi.ChannelSpan
import org.junit.Test

class ThroughputCalculationsTest {
    @Test
    fun `converts bytes and elapsed time to megabits per second`() {
        // 1,000,000 bytes in 1 second = 8 Mbps.
        val mbps = mbpsFrom(bytes = 1_000_000, elapsedNanos = 1_000_000_000L)

        assertThat(mbps).isWithin(0.001).of(8.0)
    }

    @Test
    fun `zero elapsed time reports zero rather than dividing by zero`() {
        assertThat(mbpsFrom(bytes = 1_000, elapsedNanos = 0)).isEqualTo(0.0)
    }

    @Test
    fun `summarizes a normal run's loss and average throughput`() {
        val result =
            summarizeThroughput(
                sent = 100,
                received = 95,
                roundTripBytes = 5_000_000,
                durationMs = 5_000,
                peakMbps = 12.0,
            )

        assertThat(result.lossPercent).isWithin(0.001).of(5.0)
        assertThat(result.avgMbps).isWithin(0.001).of(8.0)
        assertThat(result.peakMbps).isEqualTo(12.0)
    }

    @Test
    fun `no replies at all reports full loss without crashing`() {
        val result =
            summarizeThroughput(
                sent = 20,
                received = 0,
                roundTripBytes = 0,
                durationMs = 5_000,
                peakMbps = 0.0,
            )

        assertThat(result.lossPercent).isEqualTo(100.0)
        assertThat(result.avgMbps).isEqualTo(0.0)
    }

    @Test
    fun `cancelled before a single probe went out reports zero loss, not a crash`() {
        val result =
            summarizeThroughput(sent = 0, received = 0, roundTripBytes = 0, durationMs = 0, peakMbps = 0.0)

        assertThat(result.lossPercent).isEqualTo(0.0)
        assertThat(result.avgMbps).isEqualTo(0.0)
    }

    @Test
    fun `peak never reports below the computed average`() {
        // A caller-supplied peak that somehow undershoots the average (e.g. a single coarse
        // sample) is clamped up rather than shown as a contradiction ("peak: 2, avg: 8").
        val result =
            summarizeThroughput(
                sent = 10,
                received = 10,
                roundTripBytes = 5_000_000,
                durationMs = 5_000,
                peakMbps = 2.0,
            )

        assertThat(result.peakMbps).isEqualTo(result.avgMbps)
    }

    private fun span(
        centerMhz: Int,
        widthMhz: Int,
    ) = ChannelSpan(centerMhz = centerMhz, widthMhz = widthMhz, primaryChannel = 1, band = Band.GHZ_5)

    @Test
    fun `counts another AP whose span fully overlaps the connected channel`() {
        val connected = span(centerMhz = 5220, widthMhz = 40) // 5200-5240
        val others = listOf(span(centerMhz = 5220, widthMhz = 20)) // 5210-5230, inside

        assertThat(overlappingChannelCount(connected, others)).isEqualTo(1)
    }

    @Test
    fun `does not count an AP on a disjoint channel`() {
        val connected = span(centerMhz = 5220, widthMhz = 20) // 5210-5230
        val others = listOf(span(centerMhz = 5745, widthMhz = 20)) // 5735-5755, far away

        assertThat(overlappingChannelCount(connected, others)).isEqualTo(0)
    }

    @Test
    fun `edges that only touch do not count as overlapping`() {
        val connected = span(centerMhz = 5210, widthMhz = 20) // 5200-5220
        val others = listOf(span(centerMhz = 5230, widthMhz = 20)) // 5220-5240, touches at 5220

        assertThat(overlappingChannelCount(connected, others)).isEqualTo(0)
    }

    @Test
    fun `a partial overlap still counts`() {
        val connected = span(centerMhz = 5210, widthMhz = 20) // 5200-5220
        val others = listOf(span(centerMhz = 5225, widthMhz = 20)) // 5215-5235, overlaps 5215-5220

        assertThat(overlappingChannelCount(connected, others)).isEqualTo(1)
    }
}
