package dev.enthusiastdev.netinspector.data.lan.sweep

import dev.enthusiastdev.netinspector.core.common.net.Ipv4Subnet
import dev.enthusiastdev.netinspector.data.lan.benchmark.Benchmark
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.net.Inet4Address
import java.net.InetAddress
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * improvement-ideas.md #32 - benchmarks [HostSweeper]'s three-pass scheduling (design §8.2
 * Stage B): the concurrency-limited fan-out across passes, not probe latency itself, which
 * cannot be benchmarked on the JVM at all - [IcmpSweepProbe] needs `android.system.Os`
 * (design C-07), unavailable outside a real device. [IcmpSweepProbe] and [TcpSweepProbe] are
 * mocked with a small fixed simulated delay standing in for real network RTT, so this
 * measures the real `HostSweeper` orchestration code (dispatcher fan-out, `AtomicInteger`
 * progress, `awaitAll`/`filterNotNull` per pass) at design §12's reference /24 scale (254
 * addresses), not a reimplementation of it.
 */
class HostSweeperBenchmark {
    @Test
    fun `sweep - reference 24 scale, mixed pass1-2-3 responders`() {
        val icmpProbe = mockk<IcmpSweepProbe>()
        val tcpProbe = mockk<TcpSweepProbe>()
        val clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)

        coEvery { icmpProbe.probe(any(), any()) } coAnswers {
            val address = firstArg<Inet4Address>()
            delay(SIMULATED_ICMP_DELAY_MS)
            if (respondsToIcmp(address)) IcmpEchoResult(rttMs = SIMULATED_RTT_MS, replyTtl = SIMULATED_TTL) else null
        }
        coEvery { tcpProbe.probe(any(), any(), any()) } coAnswers {
            val address = firstArg<Inet4Address>()
            delay(SIMULATED_TCP_DELAY_MS)
            if (respondsToTcp(address)) SIMULATED_RTT_MS else null
        }

        val sweeper = HostSweeper(icmpProbe, tcpProbe, clock)
        // design §12's reference case: 192.168.x.0/24, 254 usable host addresses.
        val subnet = Ipv4Subnet(addr("192.168.1.0"), PREFIX_LENGTH_24)

        Benchmark.run("HostSweeper.sweep.reference24", warmupIterations = 3, iterations = 10) {
            runBlocking {
                sweeper.sweep(subnet, onObservation = {}, onProgress = { _, _ -> })
            }
        }
    }

    /** Deterministic ~90% ICMP response rate, close to design §12's reference /24 where pass
     * 1 answers almost every address and passes 2/3 exist to chase down the remainder. */
    private fun respondsToIcmp(address: Inet4Address) = lastOctet(address) % NON_RESPONDER_STRIDE != 0

    /** Of the ~10% that never answer ICMP, most answer a TCP connect instead - design §8.2
     * pass 3's reason for existing: hosts with ICMP disabled. A small remainder answers
     * neither, exercising the "still silent after every pass" branch too. */
    private fun respondsToTcp(address: Inet4Address): Boolean {
        val octet = lastOctet(address)
        return octet % NON_RESPONDER_STRIDE == 0 && octet % SILENT_STRIDE != 0
    }

    private fun lastOctet(address: Inet4Address) = address.address[3].toInt() and 0xFF

    private fun addr(host: String) = InetAddress.getByName(host) as Inet4Address

    private companion object {
        const val PREFIX_LENGTH_24 = 24
        const val NON_RESPONDER_STRIDE = 10
        const val SILENT_STRIDE = 25
        const val SIMULATED_ICMP_DELAY_MS = 2L
        const val SIMULATED_TCP_DELAY_MS = 1L
        const val SIMULATED_RTT_MS = 4.2
        const val SIMULATED_TTL = 64
    }
}
