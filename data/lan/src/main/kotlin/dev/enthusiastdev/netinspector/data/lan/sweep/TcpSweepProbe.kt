package dev.enthusiastdev.netinspector.data.lan.sweep

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
 * design §8.2 Stage B, pass 3 - TCP connect fallback for hosts with ICMP disabled. A refused
 * connection (RST) still proves the host is up, so it counts as a reply just like a successful
 * connect - only a genuine timeout means "no answer." Returns the RTT in milliseconds, or
 * `null` if nothing answered.
 */
class TcpSweepProbe
    @Inject
    constructor() {
        suspend fun probe(
            address: Inet4Address,
            port: Int,
            timeoutMs: Int,
        ): Double? =
            withContext(Dispatchers.IO) {
                val startNanos = System.nanoTime()
                val socket = Socket()
                try {
                    socket.connect(InetSocketAddress(address, port), timeoutMs)
                    elapsedMs(startNanos)
                } catch (ignored: ConnectException) {
                    elapsedMs(startNanos)
                } catch (ignored: SocketTimeoutException) {
                    null
                } catch (ignored: IOException) {
                    null
                } finally {
                    runCatching { socket.close() }
                }
            }

        private fun elapsedMs(startNanos: Long): Double = (System.nanoTime() - startNanos) / 1_000_000.0
    }
