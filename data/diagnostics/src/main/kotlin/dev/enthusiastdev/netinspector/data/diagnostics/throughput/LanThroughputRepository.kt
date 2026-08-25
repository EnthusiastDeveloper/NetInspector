package dev.enthusiastdev.netinspector.data.diagnostics.throughput

import android.system.ErrnoException
import dev.enthusiastdev.netinspector.core.common.icmp.IcmpPacket
import dev.enthusiastdev.netinspector.core.common.throughput.mbpsFrom
import dev.enthusiastdev.netinspector.core.common.throughput.summarizeThroughput
import dev.enthusiastdev.netinspector.core.model.diagnostics.PingProbeResult
import dev.enthusiastdev.netinspector.core.model.diagnostics.ThroughputResult
import dev.enthusiastdev.netinspector.core.model.diagnostics.ThroughputSample
import dev.enthusiastdev.netinspector.data.diagnostics.icmp.IcmpSocketEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

data class ThroughputConfig(
    val durationMs: Long = 5_000,
    val concurrency: Int = 4,
    // Just under the standard Ethernet/Wi-Fi 1500-byte MTU once the 8-byte ICMP header and a
    // 20-byte IPv4 header are added, so a probe never needs IP fragmentation - fragmentation
    // would let one lost fragment sink an otherwise-successful probe and understate throughput
    // for reasons that have nothing to do with the link itself.
    val payloadSize: Int = 1_400,
    val timeoutMs: Int = 500,
)

sealed interface ThroughputEvent {
    data class Sample(
        val sample: ThroughputSample,
    ) : ThroughputEvent

    data class Complete(
        val result: ThroughputResult,
    ) : ThroughputEvent

    /** ICMP sockets aren't available on this device (design §9.1's tier-1 capability check) -
     * unlike ping, there's no meaningful tier-2/3 fallback for a throughput estimate: an exec'd
     * ping binary can't be pipelined, and a bare TCP connect transfers no data to time. */
    data class Unsupported(
        val reason: String,
    ) : ThroughputEvent
}

interface LanThroughputRepository {
    /**
     * docs/adr/0009-lan-throughput-icmp-burst-estimate.md - streams a [ThroughputEvent.Sample]
     * roughly every quarter-second while [ThroughputConfig.concurrency] workers each pipeline
     * ICMP echo probes on their own socket for [ThroughputConfig.durationMs], then a single
     * [ThroughputEvent.Complete] with the run's totals. Cancelling the collecting coroutine (the
     * screen's lifecycle scope, same convention as `LanDiscoveryRepository.sweep`) stops every
     * in-flight worker and closes its socket.
     */
    fun measure(
        address: Inet4Address,
        config: ThroughputConfig = ThroughputConfig(),
    ): Flow<ThroughputEvent>
}

/** The three running totals a burst run tracks, shared (atomically) between every worker and
 * the sampler - split out of [DefaultLanThroughputRepository.measure] purely so that function's
 * own parameter/local count stays readable rather than for any independent reason. */
private class ThroughputCounters {
    val sent = AtomicInteger(0)
    val received = AtomicInteger(0)
    val roundTripBytes = AtomicLong(0)

    // Written by the sampler coroutine as it goes, not returned from it at the end - that
    // coroutine is always stopped by cancellation (an unbounded `while (isActive)` loop never
    // returns on its own), and a cancelled suspend function's return value is never reached, so
    // a plain "return the final value" design here would silently always report zero.
    val peakMbps = AtomicReference(0.0)
}

/** Everything one [DefaultLanThroughputRepository.runWorker] call needs, bundled so adding a
 * field there later doesn't grow that function's own parameter list. */
private data class BurstContext(
    val config: ThroughputConfig,
    val payloadSize: Int,
    val startNanos: Long,
    val counters: ThroughputCounters,
    val roundTripBytesPerReply: Long,
)

class DefaultLanThroughputRepository
    @Inject
    constructor(
        private val icmpSocketEngine: IcmpSocketEngine,
    ) : LanThroughputRepository {
        // Same "checked once per process" reasoning as DefaultPingRepository: a capability gap,
        // not a transient network condition.
        private val icmpSocketSupported by lazy { icmpSocketEngine.isSupported() }

        override fun measure(
            address: Inet4Address,
            config: ThroughputConfig,
        ): Flow<ThroughputEvent> =
            channelFlow {
                if (!icmpSocketSupported) {
                    send(ThroughputEvent.Unsupported("This device doesn't support unprivileged ICMP sockets"))
                    return@channelFlow
                }

                val payloadSize = config.payloadSize.coerceIn(MIN_PAYLOAD_SIZE, MAX_PAYLOAD_SIZE)
                val context =
                    BurstContext(
                        config = config,
                        payloadSize = payloadSize,
                        startNanos = System.nanoTime(),
                        counters = ThroughputCounters(),
                        roundTripBytesPerReply = (IcmpPacket.HEADER_SIZE + payloadSize).toLong() * 2,
                    )

                val samplingJob = launch { runSampler(context) }

                coroutineScope {
                    val concurrency = config.concurrency.coerceIn(1, MAX_CONCURRENCY)
                    List(concurrency) { async(Dispatchers.IO) { runWorker(address, context) } }.awaitAll()
                }
                // Joins rather than a bare cancel(), so the read of counters.peakMbps below is
                // guaranteed to happen after the sampler's own last write, not racing it.
                samplingJob.cancelAndJoin()

                val durationMs = (System.nanoTime() - context.startNanos) / 1_000_000
                val counters = context.counters
                send(
                    ThroughputEvent.Complete(
                        summarizeThroughput(
                            counters.sent.get(),
                            counters.received.get(),
                            counters.roundTripBytes.get(),
                            durationMs,
                            counters.peakMbps.get(),
                        ),
                    ),
                )
            }

        /** Runs until cancelled by [measure] once every worker finishes, updating
         * [ThroughputCounters.peakMbps] as it goes (see that field's own doc comment for why a
         * return value here wouldn't work). */
        private suspend fun ProducerScope<ThroughputEvent>.runSampler(context: BurstContext) {
            var lastBytes = 0L
            var lastNanos = context.startNanos
            while (currentCoroutineContext().isActive) {
                delay(SAMPLE_INTERVAL_MS)
                val nowNanos = System.nanoTime()
                val bytesNow = context.counters.roundTripBytes.get()
                val instantMbps = mbpsFrom(bytesNow - lastBytes, nowNanos - lastNanos)
                context.counters.peakMbps.updateAndGet { current -> maxOf(current, instantMbps) }
                send(
                    ThroughputEvent.Sample(
                        ThroughputSample(
                            elapsedMs = (nowNanos - context.startNanos) / 1_000_000,
                            instantMbps = instantMbps,
                            packetsSent = context.counters.sent.get(),
                            packetsReceived = context.counters.received.get(),
                        ),
                    ),
                )
                lastBytes = bytesNow
                lastNanos = nowNanos
            }
        }

        private suspend fun runWorker(
            address: Inet4Address,
            context: BurstContext,
        ) {
            val fd =
                try {
                    icmpSocketEngine.openSocket(context.config.timeoutMs, ttl = 64)
                } catch (ignored: ErrnoException) {
                    return
                }
            try {
                // Sequence numbers reset per worker rather than sharing one counter: each worker
                // has its own socket, and IcmpSocketEngine matches a reply on sequence number
                // *and* which bound socket recvfrom() returned it on (see that file's doc
                // comment) - the kernel routes replies to the right socket by the identifier it
                // assigned at openSocket(), so two workers reusing the same sequence number never
                // collide.
                var sequence = 0
                val deadlineNanos = context.startNanos + context.config.durationMs * 1_000_000L
                while (System.nanoTime() < deadlineNanos) {
                    val result = icmpSocketEngine.probeOnSocket(fd, address, sequence, context.payloadSize)
                    context.counters.sent.incrementAndGet()
                    if (result is PingProbeResult.Reply) {
                        context.counters.received.incrementAndGet()
                        context.counters.roundTripBytes.addAndGet(context.roundTripBytesPerReply)
                    }
                    // IcmpPacket packs sequence into a 16-bit field - wraps rather than
                    // overflowing into the identifier bytes on a run long enough to send more
                    // than 65536 probes.
                    sequence = (sequence + 1) and 0xFFFF
                }
            } finally {
                icmpSocketEngine.closeSocket(fd)
            }
        }

        private companion object {
            const val SAMPLE_INTERVAL_MS = 250L
            const val MAX_CONCURRENCY = 8
            const val MIN_PAYLOAD_SIZE = 32
            const val MAX_PAYLOAD_SIZE = 1_400
        }
    }
