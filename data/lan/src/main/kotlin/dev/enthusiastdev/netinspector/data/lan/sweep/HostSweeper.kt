package dev.enthusiastdev.netinspector.data.lan.sweep

import dev.enthusiastdev.netinspector.core.common.net.Ipv4Subnet
import dev.enthusiastdev.netinspector.core.model.lan.Evidence
import dev.enthusiastdev.netinspector.core.model.lan.EvidenceSource
import dev.enthusiastdev.netinspector.core.model.lan.HostObservation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.net.Inet4Address
import java.time.Clock
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

/**
 * design §8.2 Stage B - the three-pass active sweep: ICMP, an ICMP retry for non-responders,
 * then a TCP connect fallback for whatever is still silent. Streams each responder to
 * [sweep]'s `onObservation` callback as it's found rather than waiting for the whole sweep,
 * so the UI populates progressively.
 */
class HostSweeper
    @Inject
    constructor(
        private val icmpProbe: IcmpSweepProbe,
        private val tcpProbe: TcpSweepProbe,
        private val clock: Clock,
    ) {
        suspend fun sweep(
            subnet: Ipv4Subnet,
            onObservation: suspend (HostObservation) -> Unit,
            onProgress: (probed: Int, total: Int) -> Unit,
        ) {
            val addresses = subnet.hostSequence().toList()
            val total = addresses.size
            if (total == 0) return
            val probed = AtomicInteger(0)
            val reportProgress: suspend () -> Unit = { onProgress(probed.incrementAndGet(), total) }

            val pass1NonResponders =
                probeIcmp(addresses, PASS1_TIMEOUT_MS, PASS1_CONCURRENCY, onObservation, reportProgress)
            // design §8.2 - recovers hosts lost to transient wireless loss; the accuracy
            // priority's most direct expression in this pipeline.
            val pass2NonResponders =
                probeIcmp(pass1NonResponders, PASS2_TIMEOUT_MS, PASS2_CONCURRENCY, onObservation, reportProgress)
            probeTcp(pass2NonResponders, onObservation, reportProgress)
        }

        private suspend fun probeIcmp(
            addresses: List<Inet4Address>,
            timeoutMs: Int,
            concurrency: Int,
            onObservation: suspend (HostObservation) -> Unit,
            onProbed: suspend () -> Unit,
        ): List<Inet4Address> =
            coroutineScope {
                val dispatcher = Dispatchers.IO.limitedParallelism(concurrency)
                addresses
                    .map { address ->
                        async(dispatcher) {
                            val result = icmpProbe.probe(address, timeoutMs)
                            onProbed()
                            if (result != null) {
                                onObservation(
                                    HostObservation(
                                        address = address,
                                        evidence = listOf(Evidence(EvidenceSource.ICMP, clock.instant())),
                                        rttSamplesMs = listOf(result.rttMs),
                                        icmpReplyTtl = result.replyTtl,
                                    ),
                                )
                                null
                            } else {
                                address
                            }
                        }
                    }.awaitAll()
                    .filterNotNull()
            }

        /** design §8.2 - "all ports for a given host are probed concurrently," which is why the
         * per-host fan-out below uses the plain (unlimited) IO dispatcher rather than sharing
         * the host-level [PASS3_HOST_CONCURRENCY] limiter: it caps how many hosts are in
         * flight, not how many of one host's 8 ports run at once. */
        private suspend fun probeTcp(
            addresses: List<Inet4Address>,
            onObservation: suspend (HostObservation) -> Unit,
            onProbed: suspend () -> Unit,
        ) = coroutineScope {
            val hostDispatcher = Dispatchers.IO.limitedParallelism(PASS3_HOST_CONCURRENCY)
            addresses
                .map { address ->
                    async(hostDispatcher) {
                        val rttMs =
                            coroutineScope {
                                FALLBACK_PORTS
                                    .map { port ->
                                        async(Dispatchers.IO) { tcpProbe.probe(address, port, PASS3_TIMEOUT_MS) }
                                    }.awaitAll()
                                    .filterNotNull()
                                    .minOrNull()
                            }
                        onProbed()
                        if (rttMs != null) {
                            onObservation(
                                HostObservation(
                                    address = address,
                                    evidence = listOf(Evidence(EvidenceSource.TCP_CONNECT, clock.instant())),
                                    rttSamplesMs = listOf(rttMs),
                                ),
                            )
                        }
                    }
                }.awaitAll()
        }

        private companion object {
            const val PASS1_TIMEOUT_MS = 1_000
            const val PASS1_CONCURRENCY = 64
            const val PASS2_TIMEOUT_MS = 2_000
            const val PASS2_CONCURRENCY = 32
            const val PASS3_TIMEOUT_MS = 400
            const val PASS3_HOST_CONCURRENCY = 64

            // design §8.2 - connect-scan targets chosen to catch hosts with ICMP disabled:
            // web UIs, SMB/NetBIOS, Chromecast, iOS pairing, ADB.
            val FALLBACK_PORTS = listOf(80, 443, 22, 445, 139, 8009, 62078, 5555)
        }
    }
