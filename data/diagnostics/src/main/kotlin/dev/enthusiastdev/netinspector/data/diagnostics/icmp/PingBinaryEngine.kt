package dev.enthusiastdev.netinspector.data.diagnostics.icmp

import dev.enthusiastdev.netinspector.core.common.icmp.PingBinaryOutputParser
import dev.enthusiastdev.netinspector.core.model.diagnostics.PingProbeResult
import dev.enthusiastdev.netinspector.core.model.diagnostics.PingTier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.net.Inet4Address
import javax.inject.Inject

/**
 * Tier 2 (design §9.1): `/system/bin/ping -c 1`, used only if tier 1's ICMP socket path
 * fails at socket creation on a given device. One process per probe, matching tier 1's
 * per-probe granularity (`PingRepository` drives the count/interval loop either way).
 */
class PingBinaryEngine
    @Inject
    constructor() {
        suspend fun probe(
            address: Inet4Address,
            sequence: Int,
            timeoutMs: Int = 1_000,
        ): PingProbeResult =
            withContext(Dispatchers.IO) {
                val timeoutSeconds = ((timeoutMs + 999) / 1_000).coerceAtLeast(1)
                var process: Process? = null
                try {
                    process =
                        ProcessBuilder(
                            "/system/bin/ping",
                            "-c",
                            "1",
                            "-W",
                            timeoutSeconds.toString(),
                            address.hostAddress,
                        ).redirectErrorStream(true).start()

                    // The binary's own -W bounds it, but a hung exec shouldn't hang the probe
                    // loop indefinitely - a small margin over the binary's own timeout.
                    val output =
                        withTimeout((timeoutMs + 2_000).toLong()) {
                            process.inputStream.bufferedReader().readText()
                        }
                    process.waitFor()

                    PingBinaryOutputParser.parse(output, sequence)
                } catch (ignored: TimeoutCancellationException) {
                    PingProbeResult.Timeout(sequence, PingTier.PING_BINARY)
                } catch (e: IOException) {
                    PingProbeResult.Error(sequence, PingTier.PING_BINARY, e.message ?: "exec failed")
                } finally {
                    process?.destroy()
                }
            }
    }
