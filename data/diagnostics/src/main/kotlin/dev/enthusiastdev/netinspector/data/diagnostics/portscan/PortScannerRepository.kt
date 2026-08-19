package dev.enthusiastdev.netinspector.data.diagnostics.portscan

import dev.enthusiastdev.netinspector.core.model.diagnostics.PortScanFinding
import dev.enthusiastdev.netinspector.core.model.diagnostics.PortScanProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import java.io.IOException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

data class PortScanConfig(
    val concurrency: Int = 64,
    val timeoutMs: Int = 500,
)

sealed interface PortScanEvent {
    data class Found(
        val finding: PortScanFinding,
    ) : PortScanEvent

    data class Progress(
        val progress: PortScanProgress,
    ) : PortScanEvent
}

interface PortScannerRepository {
    /** design §9.5 - TCP connect scan only (design C-07: SYN scanning needs raw sockets, which
     * this platform doesn't grant). Streams an event per port scanned (a progress tick, and a
     * [PortScanEvent.Found] only for ports that accepted the connection) rather than
     * accumulating a full result set, so the UI populates as the scan runs. */
    fun scan(
        address: Inet4Address,
        ports: List<Int>,
        config: PortScanConfig = PortScanConfig(),
    ): Flow<PortScanEvent>
}

class DefaultPortScannerRepository
    @Inject
    constructor() : PortScannerRepository {
        override fun scan(
            address: Inet4Address,
            ports: List<Int>,
            config: PortScanConfig,
        ): Flow<PortScanEvent> =
            channelFlow {
                val total = ports.size
                if (total == 0) return@channelFlow
                val scanned = AtomicInteger(0)
                // design §9.5 - "non-configurable below a floor": an unthrottled scan can trip
                // IDS on managed networks and destabilise cheap consumer routers, so the caller's
                // config is clamped rather than trusted outright.
                val concurrency = config.concurrency.coerceIn(1, MAX_CONCURRENCY)
                val timeoutMs = config.timeoutMs.coerceAtLeast(MIN_TIMEOUT_MS)
                val dispatcher = Dispatchers.IO.limitedParallelism(concurrency)

                ports
                    .map { port ->
                        async(dispatcher) {
                            val isOpen = probe(address, port, timeoutMs)
                            send(PortScanEvent.Progress(PortScanProgress(scanned.incrementAndGet(), total)))
                            if (isOpen != null) send(PortScanEvent.Found(PortScanFinding(port, isOpen)))
                        }
                    }.awaitAll()
            }

        /** `null` means the port is closed (connection refused or timed out). A non-null result
         * means it's open - the string is the banner if one was grabbed, or empty if the
         * protocol doesn't offer one (or grabbing it failed after the connection succeeded). */
        private suspend fun probe(
            address: Inet4Address,
            port: Int,
            timeoutMs: Int,
        ): String? =
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(address, port), timeoutMs)
                    socket.soTimeout = timeoutMs
                    when (port) {
                        in HTTP_PORTS -> grabHttpBanner(socket, address).orEmpty()
                        PORT_SSH, PORT_FTP, PORT_SMTP -> grabLineBanner(socket).orEmpty()
                        else -> ""
                    }
                }
            } catch (ignored: IOException) {
                null
            }

        private fun grabLineBanner(socket: Socket): String? =
            socket
                .getInputStream()
                .bufferedReader(Charsets.US_ASCII)
                .readLine()
                ?.trim()
                ?.take(MAX_BANNER_CHARS)

        private fun grabHttpBanner(
            socket: Socket,
            address: Inet4Address,
        ): String? {
            val request = "HEAD / HTTP/1.0\r\nHost: ${address.hostAddress}\r\nConnection: close\r\n\r\n"
            socket.getOutputStream().write(request.toByteArray(Charsets.US_ASCII))
            val statusLine =
                try {
                    socket.getInputStream().bufferedReader(Charsets.US_ASCII).readLine()
                } catch (ignored: IOException) {
                    null
                }
            return statusLine?.trim()?.take(MAX_BANNER_CHARS)
        }

        private companion object {
            const val MAX_CONCURRENCY = 128
            const val MIN_TIMEOUT_MS = 200
            const val MAX_BANNER_CHARS = 200
            const val PORT_SSH = 22
            const val PORT_FTP = 21
            const val PORT_SMTP = 25
            val HTTP_PORTS = setOf(80, 81, 8000, 8008, 8080, 8081, 8443, 8888)
        }
    }
