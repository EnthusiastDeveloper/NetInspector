package dev.enthusiastdev.netinspector.data.lan.enrich

import dev.enthusiastdev.netinspector.core.model.lan.DeviceHint
import dev.enthusiastdev.netinspector.core.model.lan.Evidence
import dev.enthusiastdev.netinspector.core.model.lan.EvidenceSource
import dev.enthusiastdev.netinspector.core.model.lan.Host
import dev.enthusiastdev.netinspector.core.model.lan.HostObservation
import dev.enthusiastdev.netinspector.core.model.lan.deviceHintFor
import dev.enthusiastdev.netinspector.data.lan.snmp.SnmpProbe
import dev.enthusiastdev.netinspector.data.lan.sweep.IcmpSweepProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.net.Inet4Address
import java.time.Clock
import javax.inject.Inject

/**
 * design §8.2 Stage C - enrichment for confirmed hosts only: reverse DNS, the extended port
 * probe with banner grab, and the ICMP-reply-TTL/port-signature [DeviceHint]. Runs after Stage
 * B, bounded to [HOST_CONCURRENCY] hosts at once - Stage C's targets are a small, already-known
 * subset of the subnet, not the full address space Stage B swept, so it needs nowhere near
 * Stage B's 64-way concurrency.
 */
class HostEnricher
    @Inject
    constructor(
        private val reverseDnsProbe: ReverseDnsProbe,
        private val extendedPortProbe: ExtendedPortProbe,
        private val icmpTtlProbe: IcmpSweepProbe,
        private val snmpProbe: SnmpProbe,
        private val tlsCertificateProbe: TlsCertificateProbe,
        private val clock: Clock,
    ) {
        suspend fun enrich(
            hosts: List<Host>,
            gateway: Inet4Address?,
            onObservation: suspend (HostObservation) -> Unit,
        ) = coroutineScope {
            val dispatcher = Dispatchers.IO.limitedParallelism(HOST_CONCURRENCY)
            hosts
                .map { host -> async(dispatcher) { enrichOne(host, gateway, onObservation) } }
                .awaitAll()
        }

        private suspend fun enrichOne(
            host: Host,
            gateway: Inet4Address?,
            onObservation: suspend (HostObservation) -> Unit,
        ) = coroutineScope {
            val address = host.address
            // design §8.2 - HOST_CONCURRENCY caps how many *hosts* enrich at once; these
            // sub-tasks must not inherit that same limited dispatcher, or a burst of hosts each
            // launching more tasks oversubscribes it and a DNS lookup that would otherwise take
            // milliseconds can sit queued for a slot long enough to blow its own timeout -
            // reproduced on-device as a handful of hosts silently, deterministically never
            // resolving. Dispatchers.IO's own (much larger) pool has room for this.
            val hostnameJob = async(Dispatchers.IO) { resolveHostname(address, gateway) }
            val portsJob = async(Dispatchers.IO) { probePorts(address) }
            val ttlJob = async(Dispatchers.IO) { resolveTtl(host) }
            val snmpJob = async(Dispatchers.IO) { snmpProbe.query(address, SNMP_TIMEOUT_MS) }
            val tlsJob = async(Dispatchers.IO) { tlsCertificateProbe.subjectCommonName(address, TLS_TIMEOUT_MS) }

            val hostname = hostnameJob.await()
            val openPorts = portsJob.await()
            val icmpReplyTtl = ttlJob.await()
            val snmpResult = snmpJob.await()
            val tlsCommonName = tlsJob.await()
            val hasNothing =
                hostname == null &&
                    openPorts.isEmpty() &&
                    icmpReplyTtl == null &&
                    snmpResult == null &&
                    tlsCommonName == null
            if (hasNothing) return@coroutineScope
            val deviceHint = deviceHintFor(openPorts, icmpReplyTtl, snmpResult?.sysDescr, tlsCommonName)

            onObservation(
                HostObservation(
                    address = address,
                    evidence = reverseDnsEvidence(hostname) + snmpEvidence(snmpResult),
                    hostnames = hostnames(hostname, snmpResult),
                    openPorts = openPorts,
                    deviceHint = deviceHint,
                    icmpReplyTtl = icmpReplyTtl,
                ),
            )
        }

        private fun hostnames(
            hostname: String?,
            snmpResult: SnmpProbe.Result?,
        ): Map<EvidenceSource, String> =
            (hostname?.let { mapOf(EvidenceSource.REVERSE_DNS to it) }.orEmpty()) +
                (snmpResult?.sysName?.let { mapOf(EvidenceSource.SNMP to it) }.orEmpty())

        private fun snmpEvidence(result: SnmpProbe.Result?): List<Evidence> {
            if (result == null) return emptyList()
            return listOf(Evidence(EvidenceSource.SNMP, clock.instant(), detail = result.sysDescr))
        }

        /** A single retry - under Stage C's [HOST_CONCURRENCY]-way concurrent burst (each host
         * also fanning out ~30 port probes at once), a query can occasionally lose the race for
         * Wi-Fi airtime or a timely server reply even though the query itself is sound; the
         * retry recovers those the same way pass 2 of the Stage B sweep recovers hosts lost to
         * transient wireless loss. Only once both system-resolver attempts come back empty does
         * this fall back to [ReverseDnsProbe.resolveViaGateway] - see that function's doc
         * comment for why the system resolver alone isn't always enough (Private DNS). */
        private suspend fun resolveHostname(
            address: Inet4Address,
            gateway: Inet4Address?,
        ): String? {
            val systemResult =
                runCatching { reverseDnsProbe.resolve(address, DNS_TIMEOUT_MS) }.getOrNull()
                    ?: runCatching { reverseDnsProbe.resolve(address, DNS_TIMEOUT_MS) }.getOrNull()
            if (systemResult != null) return systemResult
            return gateway?.let {
                runCatching { reverseDnsProbe.resolveViaGateway(address, it, DNS_TIMEOUT_MS) }.getOrNull()
            }
        }

        /** design §8.2 - TTL is free from Stage B for hosts that answered ICMP there; only a
         * TCP-only confirmed host needs a dedicated probe here. */
        private suspend fun resolveTtl(host: Host): Int? =
            host.icmpReplyTtl ?: runCatching { icmpTtlProbe.probe(host.address, TTL_TIMEOUT_MS)?.replyTtl }.getOrNull()

        private fun reverseDnsEvidence(hostname: String?) =
            if (hostname != null) listOf(Evidence(EvidenceSource.REVERSE_DNS, clock.instant())) else emptyList()

        /** design §8.2 - "all ports for a given host are probed concurrently," the same pattern
         * Stage B's pass 3 uses (design's own note there on why serial probing blows the sweep's
         * time budget applies here too, just against a much smaller port set). */
        private suspend fun probePorts(address: Inet4Address) =
            coroutineScope {
                ExtendedPortProbe.PORTS
                    .map { port -> async(Dispatchers.IO) { extendedPortProbe.probe(address, port, PORT_TIMEOUT_MS) } }
                    .awaitAll()
                    .filterNotNull()
                    .sortedBy { it.port }
            }

        private companion object {
            const val HOST_CONCURRENCY = 16
            const val DNS_TIMEOUT_MS = 2_000
            const val PORT_TIMEOUT_MS = 500
            const val TTL_TIMEOUT_MS = 1_000
            const val SNMP_TIMEOUT_MS = 800
            const val TLS_TIMEOUT_MS = 1_500
        }
    }
