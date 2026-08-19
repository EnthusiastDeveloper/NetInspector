package dev.enthusiastdev.netinspector.core.common.icmp

import com.google.common.truth.Truth.assertThat
import dev.enthusiastdev.netinspector.core.model.diagnostics.PingTier
import org.junit.Test

class PingSummaryTest {
    // Fixture: 5 replies out of 5 probes, in probe order (not sorted) - jitter depends on order.
    private val fixtureRtts = listOf(10.0, 20.0, 15.0, 30.0, 25.0)

    @Test
    fun `computes min, max and average`() {
        val summary = summarizePing(PingTier.ICMP_SOCKET, sent = 5, rttsMs = fixtureRtts)

        assertThat(summary.minMs).isEqualTo(10.0)
        assertThat(summary.maxMs).isEqualTo(30.0)
        assertThat(summary.avgMs).isEqualTo(20.0)
    }

    @Test
    fun `computes median from the sorted fixture`() {
        val summary = summarizePing(PingTier.ICMP_SOCKET, sent = 5, rttsMs = fixtureRtts)

        // sorted: [10, 15, 20, 25, 30] -> middle element
        assertThat(summary.medianMs).isEqualTo(20.0)
    }

    @Test
    fun `computes median as the average of the two middle values for an even count`() {
        val summary = summarizePing(PingTier.ICMP_SOCKET, sent = 4, rttsMs = listOf(10.0, 20.0, 30.0, 40.0))

        assertThat(summary.medianMs).isEqualTo(25.0)
    }

    @Test
    fun `computes jitter as the mean absolute successive difference in probe order`() {
        val summary = summarizePing(PingTier.ICMP_SOCKET, sent = 5, rttsMs = fixtureRtts)

        // |20-10|=10, |15-20|=5, |30-15|=15, |25-30|=5 -> average 8.75
        assertThat(summary.jitterMs).isEqualTo(8.75)
    }

    @Test
    fun `computes sample standard deviation`() {
        val summary = summarizePing(PingTier.ICMP_SOCKET, sent = 5, rttsMs = fixtureRtts)

        assertThat(summary.stddevMs).isWithin(0.0001).of(7.905694150420949)
    }

    @Test
    fun `computes loss percentage from sent versus received`() {
        val summary = summarizePing(PingTier.ICMP_SOCKET, sent = 6, rttsMs = fixtureRtts)

        assertThat(summary.received).isEqualTo(5)
        assertThat(summary.lossPercent).isWithin(0.001).of(16.667)
    }

    @Test
    fun `total loss produces null statistics rather than dividing by zero`() {
        val summary = summarizePing(PingTier.ICMP_SOCKET, sent = 3, rttsMs = emptyList())

        assertThat(summary.minMs).isNull()
        assertThat(summary.avgMs).isNull()
        assertThat(summary.medianMs).isNull()
        assertThat(summary.stddevMs).isNull()
        assertThat(summary.jitterMs).isNull()
        assertThat(summary.lossPercent).isEqualTo(100.0)
    }

    @Test
    fun `a single reply has no jitter or standard deviation`() {
        val summary = summarizePing(PingTier.ICMP_SOCKET, sent = 1, rttsMs = listOf(12.5))

        assertThat(summary.jitterMs).isNull()
        assertThat(summary.stddevMs).isNull()
        assertThat(summary.medianMs).isEqualTo(12.5)
        assertThat(summary.lossPercent).isEqualTo(0.0)
    }
}
