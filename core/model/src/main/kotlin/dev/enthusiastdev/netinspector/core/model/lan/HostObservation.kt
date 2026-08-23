package dev.enthusiastdev.netinspector.core.model.lan

import java.net.Inet4Address

/** design §8.2/§8.3 - what one probe (or the Stage B sweep) found about one address, before
 * it is folded into the evolving [Host] map. A single probe usually produces exactly one of
 * these; [evidence] can hold more than one entry only when a single mechanism legitimately
 * observed the host more than once in the same pass. */
data class HostObservation(
    val address: Inet4Address,
    val evidence: List<Evidence>,
    val hostnames: Map<EvidenceSource, String> = emptyMap(),
    val services: List<DiscoveredService> = emptyList(),
    val rttSamplesMs: List<Double> = emptyList(),
    val isGateway: Boolean = false,
    val isSelf: Boolean = false,
    /** design §8.2 Stage C - populated by the confirmed-hosts-only enrichment pass; absent
     * (`null`/empty) from every Stage A/B observation. */
    val openPorts: List<OpenPort> = emptyList(),
    val deviceHint: DeviceHint? = null,
    val icmpReplyTtl: Int? = null,
    /** docs/device-identification-ideas.md A3 - only ever set by [dev.enthusiastdev.netinspector
     * .data.lan.netbios.NetBiosProbe], from the NBSTAT response's STATISTICS field (RFC 1002
     * §4.2.18) rather than the ARP table blocked by C-01. `null` for every other source. */
    val macAddress: String? = null,
    val vendor: String? = null,
)
