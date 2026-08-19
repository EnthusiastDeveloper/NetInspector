package dev.enthusiastdev.netinspector.data.lan.enrich

import android.net.DnsResolver
import android.os.CancellationSignal
import dev.enthusiastdev.netinspector.core.common.dns.DnsPtrQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.random.Random

/**
 * design §8.2 Stage C - reverse DNS via `DnsResolver`. `DnsResolver`'s typed `query()` only
 * returns A/AAAA answers (design's own note on this: the general encoder/decoder it envisions
 * for the DNS tool is Phase 7 work in a different module), so a PTR lookup goes through
 * `rawQuery(byte[])` with [DnsPtrQuery] doing the encoding/decoding.
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
                            Dispatchers.IO.asExecutor(),
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
    }
