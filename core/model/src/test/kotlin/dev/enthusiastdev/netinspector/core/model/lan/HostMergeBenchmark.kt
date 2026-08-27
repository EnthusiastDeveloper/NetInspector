package dev.enthusiastdev.netinspector.core.model.lan

import dev.enthusiastdev.netinspector.core.model.benchmark.Benchmark
import org.junit.Test
import java.net.Inet4Address
import java.net.InetAddress
import java.time.Instant

private fun addr(host: String) = InetAddress.getByName(host) as Inet4Address

/**
 * ideas.md #32 - [mergeObservation] is called once per probe result, streamed as
 * the sweep runs (design §8.2), so its cost is paid `addresses probed` times per sweep, not
 * once. It also rebuilds the whole host map on every call (`current + (address to merged)`),
 * which is O(host count) - benchmarking the full stream of a /24 sweep's worth of
 * observations (design §12's reference case, 254 addresses) makes that O(n^2) shape visible as
 * a rising per-call cost rather than hiding it behind a single-observation microbenchmark.
 */
class HostMergeBenchmark {
    @Test
    fun `mergeObservation - full 254-address sweep worth of observations`() {
        val observations = syntheticSweepObservations(hostCount = REFERENCE_24_HOST_COUNT)

        Benchmark.run("HostMerge.mergeObservation.sweepOf254", warmupIterations = 3, iterations = 10) {
            var hosts = emptyMap<Inet4Address, Host>()
            for (observation in observations) {
                hosts = mergeObservation(hosts, observation)
            }
        }
    }

    @Test
    fun `finalizeSweep - 254-host map, half stale`() {
        val hosts =
            (1..REFERENCE_24_HOST_COUNT).associate { i ->
                val address = addr("192.168.1.$i")
                address to sampleHost(address)
            }
        val observedThisSweep = hosts.keys.filterIndexed { index, _ -> index % 2 == 0 }.toSet()

        Benchmark.run("HostMerge.finalizeSweep.hosts254") {
            finalizeSweep(hosts, observedThisSweep)
        }
    }

    @Test
    fun `primaryHostname - host with every naming source populated`() {
        val host =
            sampleHost(addr("192.168.1.1")).copy(
                hostnames =
                    mapOf(
                        EvidenceSource.SSDP to "living-room-tv",
                        EvidenceSource.NETBIOS to "LIVINGROOM-TV",
                        EvidenceSource.UPNP_HOSTS to "living-room-tv.lan",
                        EvidenceSource.SNMP to "LivingRoomTV",
                        EvidenceSource.REVERSE_DNS to "192-168-1-1.lan",
                    ),
            )

        Benchmark.run("Host.primaryHostname.allSourcesPopulated") {
            host.primaryHostname
        }
    }

    /** One `HostObservation` per address, mirroring pass 1's all-ICMP-replies happy path -
     * the single largest batch of `mergeObservation` calls a real sweep makes (design §12's
     * timing table: 254 of 254 addresses answer pass 1 before any retry is needed). */
    private fun syntheticSweepObservations(hostCount: Int): List<HostObservation> =
        (1..hostCount).map { i ->
            HostObservation(
                address = addr("192.168.1.$i"),
                evidence = listOf(Evidence(EvidenceSource.ICMP, Instant.EPOCH)),
                rttSamplesMs = listOf(SIMULATED_RTT_MS),
                icmpReplyTtl = SIMULATED_TTL,
            )
        }

    private fun sampleHost(address: Inet4Address) =
        Host(
            address = address,
            confidence = HostConfidence.CONFIRMED,
            evidence = listOf(Evidence(EvidenceSource.ICMP, Instant.EPOCH)),
            hostnames = emptyMap(),
            macAddress = null,
            vendor = null,
            deviceHint = null,
            openPorts = emptyList(),
            services = emptyList(),
            icmpReplyTtl = SIMULATED_TTL,
            rttMedianMs = SIMULATED_RTT_MS,
            isGateway = false,
            isSelf = false,
        )

    private companion object {
        const val REFERENCE_24_HOST_COUNT = 254
        const val SIMULATED_RTT_MS = 4.2
        const val SIMULATED_TTL = 64
    }
}
