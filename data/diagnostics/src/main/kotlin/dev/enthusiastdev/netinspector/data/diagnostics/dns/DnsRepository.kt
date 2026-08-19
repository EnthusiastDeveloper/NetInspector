package dev.enthusiastdev.netinspector.data.diagnostics.dns

import android.net.DnsResolver
import android.net.Network
import android.os.CancellationSignal
import dev.enthusiastdev.netinspector.core.model.diagnostics.DnsQueryOutcome
import dev.enthusiastdev.netinspector.core.model.diagnostics.DnsRecordType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.Executor
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.random.Random

/**
 * design §9.4 - the system resolver ([DnsResolver.rawQuery]) for the default-server case, and
 * [DnsWireCodec] over a raw UDP/53 socket for a user-specified server, since the system resolver
 * cannot be redirected.
 */
interface DnsRepository {
    suspend fun querySystemResolver(
        name: String,
        type: DnsRecordType,
    ): DnsQueryOutcome

    suspend fun queryServer(
        server: InetAddress,
        name: String,
        type: DnsRecordType,
        timeoutMs: Int = 3_000,
    ): DnsQueryOutcome
}

class DefaultDnsRepository
    @Inject
    constructor() : DnsRepository {
        override suspend fun querySystemResolver(
            name: String,
            type: DnsRecordType,
        ): DnsQueryOutcome {
            val queryId = Random.nextInt(0, UShort.MAX_VALUE.toInt())
            val query = DnsWireCodec.buildQuery(name, type, queryId)
            val startNanos = System.nanoTime()
            return try {
                val response =
                    suspendCancellableCoroutine { continuation ->
                        val cancellationSignal = CancellationSignal()
                        continuation.invokeOnCancellation { cancellationSignal.cancel() }
                        DnsResolver.getInstance().rawQuery(
                            null as Network?,
                            query,
                            DnsResolver.FLAG_EMPTY,
                            Executor { it.run() },
                            cancellationSignal,
                            object : DnsResolver.Callback<ByteArray> {
                                override fun onAnswer(
                                    answer: ByteArray,
                                    rcode: Int,
                                ) {
                                    continuation.resume(answer)
                                }

                                override fun onError(error: DnsResolver.DnsException) {
                                    continuation.resumeWithException(error)
                                }
                            },
                        )
                    }
                toOutcome(response, queryId, startNanos)
            } catch (e: DnsResolver.DnsException) {
                DnsQueryOutcome.Error(e.message ?: "system resolver query failed")
            }
        }

        override suspend fun queryServer(
            server: InetAddress,
            name: String,
            type: DnsRecordType,
            timeoutMs: Int,
        ): DnsQueryOutcome =
            withContext(Dispatchers.IO) {
                val queryId = Random.nextInt(0, UShort.MAX_VALUE.toInt())
                val query = DnsWireCodec.buildQuery(name, type, queryId)
                val startNanos = System.nanoTime()
                try {
                    DatagramSocket().use { socket ->
                        socket.soTimeout = timeoutMs
                        socket.send(DatagramPacket(query, query.size, server, DNS_PORT))

                        val buffer = ByteArray(MAX_UDP_RESPONSE_BYTES)
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)

                        toOutcome(packet.data.copyOf(packet.length), queryId, startNanos)
                    }
                } catch (ignored: SocketTimeoutException) {
                    DnsQueryOutcome.Error("no response from $server within ${timeoutMs}ms")
                } catch (e: IOException) {
                    DnsQueryOutcome.Error(e.message ?: "query failed")
                }
            }

        private fun toOutcome(
            response: ByteArray,
            queryId: Int,
            startNanos: Long,
        ): DnsQueryOutcome {
            if (response.isEmpty()) return DnsQueryOutcome.Error("no response")
            val records =
                DnsWireCodec.parseResponse(response, queryId)
                    ?: return DnsQueryOutcome.Error("malformed response")
            val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000.0
            return DnsQueryOutcome.Success(records, elapsedMs)
        }

        private companion object {
            const val DNS_PORT = 53
            const val MAX_UDP_RESPONSE_BYTES = 4_096
        }
    }
