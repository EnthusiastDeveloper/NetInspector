package dev.enthusiastdev.netinspector.core.model.lan

import java.net.Inet4Address

/** design §3, §8.1 - one row per IPv4 address on the LAN. [macAddress] is null for most hosts:
 * Android blocks reading the ARP table on an unrooted device (C-01). The one exception is a
 * host that answers a NetBIOS NBSTAT query - its response's STATISTICS field carries the
 * adapter's real MAC (RFC 1002 §4.2.18), independent of the blocked ARP table (docs/
 * device-identification-ideas.md A3) - so this is populated for NetBIOS-observed hosts only.
 * The UI must not render an empty "MAC" row for everyone else - it shows the identification
 * signals it actually has. */
data class Host(
    val address: Inet4Address,
    val confidence: HostConfidence,
    val evidence: List<Evidence>,
    val hostnames: Map<EvidenceSource, String>,
    val macAddress: String?,
    val vendor: String?,
    val deviceHint: DeviceHint?,
    val openPorts: List<OpenPort>,
    val services: List<DiscoveredService>,
    val icmpReplyTtl: Int?,
    val rttMedianMs: Double?,
    val isGateway: Boolean,
    val isSelf: Boolean,
)

/** design §11.3 - hostname precedence for the primary display name; every variant stays
 * visible (with its source) on the detail screen regardless of which one this picks. */
private val HOSTNAME_PRECEDENCE =
    listOf(EvidenceSource.MDNS, EvidenceSource.SSDP, EvidenceSource.NETBIOS, EvidenceSource.REVERSE_DNS)

val Host.primaryHostname: String?
    get() = HOSTNAME_PRECEDENCE.firstNotNullOfOrNull { hostnames[it] }
