package dev.enthusiastdev.netinspector.core.common.icmp

import com.google.common.truth.Truth.assertThat
import dev.enthusiastdev.netinspector.core.model.diagnostics.TracerouteProbe
import dev.enthusiastdev.netinspector.core.model.diagnostics.TracerouteTier
import org.junit.Test

/**
 * Golden-file tests. `toybox_time_exceeded.txt` and the reused `ping/toybox_reply.txt` were
 * captured live from the S21 Ultra test device (Android 15, toybox) via
 * `ping -c 1 -W 2 -t 1 8.8.8.8`; `iputils_time_exceeded.txt` is a reference capture standing in
 * for an OEM/custom-ROM device shipping iputils instead of toybox - not yet confirmed on this
 * app's own device matrix.
 */
class TracerouteBinaryOutputParserTest {
    private fun fixture(
        dir: String,
        name: String,
    ): String =
        checkNotNull(javaClass.classLoader.getResourceAsStream("$dir/$name")) { "missing fixture: $dir/$name" }
            .bufferedReader()
            .readText()

    @Test
    fun `parses a toybox TTL-exceeded reply as an intermediate hop`() {
        val output = fixture("traceroute", "toybox_time_exceeded.txt")
        val result = TracerouteBinaryOutputParser.parse(output, measuredRttMs = 12.0)

        assertThat(result).isEqualTo(
            TracerouteProbe.Reply(TracerouteTier.PING_BINARY, "192.168.8.1", 12.0, reachedTarget = false),
        )
    }

    @Test
    fun `parses an iputils TTL-exceeded reply as an intermediate hop`() {
        val output = fixture("traceroute", "iputils_time_exceeded.txt")
        val result = TracerouteBinaryOutputParser.parse(output, measuredRttMs = 9.0)

        assertThat(result).isEqualTo(
            TracerouteProbe.Reply(TracerouteTier.PING_BINARY, "192.168.1.1", 9.0, reachedTarget = false),
        )
    }

    @Test
    fun `parses a normal reply as reaching the target, using the binary's own timing`() {
        val result = TracerouteBinaryOutputParser.parse(fixture("ping", "toybox_reply.txt"), measuredRttMs = 999.0)

        assertThat(result).isEqualTo(
            TracerouteProbe.Reply(TracerouteTier.PING_BINARY, "192.168.8.1", 3.63, reachedTarget = true),
        )
    }

    @Test
    fun `parses a 100 percent loss run as a timeout`() {
        val result = TracerouteBinaryOutputParser.parse(fixture("ping", "toybox_timeout.txt"), measuredRttMs = 1_000.0)

        assertThat(result).isEqualTo(TracerouteProbe.Timeout(TracerouteTier.PING_BINARY))
    }
}
