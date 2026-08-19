package dev.enthusiastdev.netinspector.data.diagnostics.icmp

import dev.enthusiastdev.netinspector.core.model.diagnostics.PingProbeResult
import dev.enthusiastdev.netinspector.core.model.diagnostics.PingTier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.ConnectException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import javax.inject.Inject

/**
 * Tier 3 (design §9.1): TCP connect RTT, for hosts with ICMP disabled entirely. Explicitly
 * tagged [PingTier.TCP_CONNECT] in the result - never presented as if it were ICMP.
 *
 * A refused connection (RST) still proves the host is up and answering, just not on this
 * port, so it's reported as a successful latency measurement - the same technique
 * `nmap -PT` style TCP ping relies on. Only a genuine timeout means "no answer at all."
 */
class TcpConnectEngine
    @Inject
    constructor() {
        suspend fun probe(
            address: Inet4Address,
            sequence: Int,
            timeoutMs: Int = 1_000,
            port: Int = 80,
        ): PingProbeResult =
            withContext(Dispatchers.IO) {
                val startNanos = System.nanoTime()
                val socket = Socket()
                try {
                    socket.connect(InetSocketAddress(address, port), timeoutMs)
                    PingProbeResult.Reply(sequence, PingTier.TCP_CONNECT, elapsedMs(startNanos))
                } catch (ignored: ConnectException) {
                    PingProbeResult.Reply(sequence, PingTier.TCP_CONNECT, elapsedMs(startNanos))
                } catch (ignored: SocketTimeoutException) {
                    PingProbeResult.Timeout(sequence, PingTier.TCP_CONNECT)
                } catch (e: IOException) {
                    PingProbeResult.Error(sequence, PingTier.TCP_CONNECT, e.message ?: "connect failed")
                } finally {
                    runCatching { socket.close() }
                }
            }

        private fun elapsedMs(startNanos: Long): Double = (System.nanoTime() - startNanos) / 1_000_000.0
    }
