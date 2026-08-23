package dev.enthusiastdev.netinspector.data.lan.enrich

import android.net.DnsResolver
import android.os.CancellationSignal
import dev.enthusiastdev.netinspector.core.common.dns.DnsPtrQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.util.concurrent.Executor
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.random.Random

/**
 * design §8.2 Stage C - reverse DNS via `DnsResolver`. `DnsResolver`'s typed `query()` only
 * returns A/AAAA answers (design's own note on this: the general encoder/decoder it envisions
 * for the DNS tool is Phase 7 work in a different module), so a PTR lookup goes through
 * `rawQuery(byte[])` with [DnsPtrQuery] doing the encoding/decoding.
 *
 * The callback executor is a direct/inline `Executor`, not `Dispatchers.IO.asExecutor()` -
 * Stage C's extended port probe saturates `Dispatchers.IO` with hundreds of concurrent blocking
 * socket connects (up to [dev.enthusiastdev.netinspector.data.lan.enrich.HostEnricher]'s
 * `HOST_CONCURRENCY` hosts × `ExtendedPortProbe.PORTS`), so a callback merely *queued* on that
 * pool can starve past the query's own timeout even though the resolver answered in
 * milliseconds - reproduced on-device: every query dispatched cleanly but not one callback ever
 * fired before cancellation. Matches [dev.enthusiastdev.netinspector.data.diagnostics.dns.DefaultDnsRepository]'s
 * `Executor { it.run() }`, which never hits this because it isn't competing with Stage C's probes.
 */
class ReverseDnsProbe
    @Inject
    constructor() {
        suspend fun resolve(
            address: Inet4Address,
            timeoutMs: Int,
        ): String? =
            withTimeoutOrNull(timeoutMs.toLong()) {
                val queryId = Random.nextInt(0, UShort.MAX_VALUE.toInt())
                val query = DnsPtrQuery.buildQuery(address, queryId)
                val signal = CancellationSignal()
                try {
                    suspendCancellableCoroutine { continuation ->
                        continuation.invokeOnCancellation { signal.cancel() }
                        DnsResolver.getInstance().rawQuery(
                            // network =
                            null,
                            query,
                            DnsResolver.FLAG_EMPTY,
                            DIRECT_EXECUTOR,
                            signal,
                            object : DnsResolver.Callback<ByteArray> {
                                override fun onAnswer(
                                    answer: ByteArray,
                                    rcode: Int,
                                ) {
                                    continuation.resume(DnsPtrQuery.parseAnswer(answer, queryId))
                                }

                                override fun onError(error: DnsResolver.DnsException) {
                                    continuation.resume(null)
                                }
                            },
                        )
                    }
                } finally {
                    signal.cancel()
                }
            }

        /**
         * docs/adr/c-19-private-dns-breaks-reverse-lookup.md - [resolve] goes through the
         * system `DnsResolver`, which Android's "Private DNS" setting forces through the
         * user's chosen DoT/DoH resolver system-wide; that resolver has no records for a
         * private `in-addr.arpa` name, so every reverse lookup fails whenever Private DNS is
         * on, regardless of the LAN's own DNS server. [HostEnricher] calls this only once
         * [resolve] has already come back empty, sending the exact same [DnsPtrQuery]-encoded
         * query directly to the LAN gateway over a raw UDP socket instead - a plain local
         * network request the system resolver setting has no say over, the same way
         * [dev.enthusiastdev.netinspector.data.lan.netbios.NetBiosProbe]/
         * [dev.enthusiastdev.netinspector.data.lan.ssdp.SsdpProbe] already talk to the LAN
         * directly rather than through a platform convenience API. Most home routers also run
         * a caching/forwarding DNS server reachable at their own address, but that's not
         * guaranteed - a gateway that isn't also a DNS server just times out here exactly like
         * a host with no PTR record would.
         */
        suspend fun resolveViaGateway(
            address: Inet4Address,
            gateway: Inet4Address,
            timeoutMs: Int,
            port: Int = DNS_PORT,
        ): String? =
            withContext(Dispatchers.IO) {
                val queryId = Random.nextInt(0, UShort.MAX_VALUE.toInt())
                val query = DnsPtrQuery.buildQuery(address, queryId)
                val socket = DatagramSocket()
                try {
                    socket.soTimeout = timeoutMs
                    socket.send(DatagramPacket(query, query.size, gateway, port))
                    val buffer = ByteArray(RECEIVE_BUFFER_SIZE)
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    DnsPtrQuery.parseAnswer(packet.data.copyOf(packet.length), queryId)
                } catch (ignored: IOException) {
                    null
                } finally {
                    runCatching { socket.close() }
                }
            }

        private companion object {
            val DIRECT_EXECUTOR = Executor { it.run() }
            const val DNS_PORT = 53
            const val RECEIVE_BUFFER_SIZE = 512
        }
    }
