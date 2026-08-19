package dev.enthusiastdev.netinspector.core.common.icmp

import com.google.common.truth.Truth.assertThat
import dev.enthusiastdev.netinspector.core.model.diagnostics.TracerouteProbe
import dev.enthusiastdev.netinspector.core.model.diagnostics.TracerouteTier
import org.junit.Test

class TracerouteHopStatsTest {
    private val tier = TracerouteTier.ICMP_ERROR_QUEUE

    @Test
    fun `computes min avg max from replies, ignoring timeouts and errors`() {
        val probes =
            listOf(
                TracerouteProbe.Reply(tier, "10.0.0.1", 10.0, reachedTarget = false),
                TracerouteProbe.Timeout(tier),
                TracerouteProbe.Reply(tier, "10.0.0.1", 20.0, reachedTarget = false),
                TracerouteProbe.Error(tier, "boom"),
                TracerouteProbe.Reply(tier, "10.0.0.1", 30.0, reachedTarget = false),
            )

        val stats = summarizeHop(probes)

        assertThat(stats).isEqualTo(TracerouteHopStats(minMs = 10.0, avgMs = 20.0, maxMs = 30.0))
    }

    @Test
    fun `returns all-null stats when every probe timed out`() {
        val stats = summarizeHop(listOf(TracerouteProbe.Timeout(tier), TracerouteProbe.Timeout(tier)))

        assertThat(stats).isEqualTo(TracerouteHopStats(null, null, null))
    }
}
