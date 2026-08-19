package dev.enthusiastdev.netinspector.core.common.icmp

import com.google.common.truth.Truth.assertThat
import dev.enthusiastdev.netinspector.core.model.diagnostics.PingProbeResult
import dev.enthusiastdev.netinspector.core.model.diagnostics.PingTier
import org.junit.Test

/**
 * Golden-file tests against real captured `ping -c 1` output - not synthesized. `toybox_*`
 * captured from a Samsung Galaxy S23 Ultra (Android 16, toybox 0.8.12-android). `iputils_*`
 * captured from a Linux desktop (iputils 20250605), standing in for an OEM/custom-ROM device
 * shipping iputils instead of toybox.
 */
class PingBinaryOutputParserTest {
    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader.getResourceAsStream("ping/$name")) { "missing fixture: $name" }
            .bufferedReader()
            .readText()

    @Test
    fun `parses a toybox reply and extracts RTT`() {
        val result = PingBinaryOutputParser.parse(fixture("toybox_reply.txt"), sequence = 3)

        assertThat(result).isEqualTo(PingProbeResult.Reply(sequence = 3, tier = PingTier.PING_BINARY, rttMs = 3.63))
    }

    @Test
    fun `parses a toybox 100 percent loss run as a timeout`() {
        val result = PingBinaryOutputParser.parse(fixture("toybox_timeout.txt"), sequence = 1)

        assertThat(result).isEqualTo(PingProbeResult.Timeout(sequence = 1, tier = PingTier.PING_BINARY))
    }

    @Test
    fun `parses an iputils reply and extracts RTT`() {
        val result = PingBinaryOutputParser.parse(fixture("iputils_reply.txt"), sequence = 7)

        assertThat(result).isEqualTo(PingProbeResult.Reply(sequence = 7, tier = PingTier.PING_BINARY, rttMs = 0.028))
    }

    @Test
    fun `parses an iputils 100 percent loss run as a timeout`() {
        val result = PingBinaryOutputParser.parse(fixture("iputils_timeout.txt"), sequence = 2)

        assertThat(result).isEqualTo(PingProbeResult.Timeout(sequence = 2, tier = PingTier.PING_BINARY))
    }

    @Test
    fun `parses a reply from the middle of a multi-line block, not just a single-line input`() {
        // Defensive: the engine execs `ping -c 1` so real input is always single-probe, but the
        // parser itself shouldn't assume that - it should find the reply line regardless of
        // surrounding header/summary lines.
        val block = fixture("toybox_reply.txt") + "\n" + fixture("iputils_timeout.txt")

        val result = PingBinaryOutputParser.parse(block, sequence = 5)

        assertThat(result).isEqualTo(PingProbeResult.Reply(sequence = 5, tier = PingTier.PING_BINARY, rttMs = 3.63))
    }
}
