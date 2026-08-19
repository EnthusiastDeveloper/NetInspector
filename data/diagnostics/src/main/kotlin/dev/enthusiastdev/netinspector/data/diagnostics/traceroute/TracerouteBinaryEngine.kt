package dev.enthusiastdev.netinspector.data.diagnostics.traceroute

import dev.enthusiastdev.netinspector.core.common.icmp.TracerouteBinaryOutputParser
import dev.enthusiastdev.netinspector.core.model.diagnostics.TracerouteProbe
import dev.enthusiastdev.netinspector.core.model.diagnostics.TracerouteTier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.net.Inet4Address
import javax.inject.Inject

/**
 * Fallback tier (design §9.3/C-08) for devices where [TracerouteSocketEngine]'s error-queue read
 * proves unreliable: `/system/bin/ping -c 1 -t <ttl>`. Unlike a normal reply, the binary's
 * "Time to live exceeded" line carries no `time=` field in either toybox or iputils, so this
 * measures wall-clock time around the whole process as the RTT for that case - less accurate
 * than tier 1's own send/receive timestamps (it includes process spawn overhead), which is why
 * tier 1 is preferred whenever available rather than this being the primary path.
 */
class TracerouteBinaryEngine
    @Inject
    constructor() {
        suspend fun probeHop(
            target: Inet4Address,
            ttl: Int,
            timeoutMs: Int,
        ): TracerouteProbe =
            withContext(Dispatchers.IO) {
                val timeoutSeconds = ((timeoutMs + 999) / 1_000).coerceAtLeast(1)
                var process: Process? = null
                try {
                    val startedAtNanos = System.nanoTime()
                    process =
                        ProcessBuilder(
                            "/system/bin/ping",
                            "-c",
                            "1",
                            "-W",
                            timeoutSeconds.toString(),
                            "-t",
                            ttl.toString(),
                            target.hostAddress,
                        ).redirectErrorStream(true).start()

                    val output =
                        withTimeout((timeoutMs + 2_000).toLong()) {
                            process.inputStream.bufferedReader().readText()
                        }
                    process.waitFor()
                    val elapsedMs = (System.nanoTime() - startedAtNanos) / 1_000_000.0

                    TracerouteBinaryOutputParser.parse(output, elapsedMs)
                } catch (ignored: TimeoutCancellationException) {
                    TracerouteProbe.Timeout(TracerouteTier.PING_BINARY)
                } catch (e: IOException) {
                    TracerouteProbe.Error(TracerouteTier.PING_BINARY, e.message ?: "exec failed")
                } finally {
                    process?.destroy()
                }
            }
    }
