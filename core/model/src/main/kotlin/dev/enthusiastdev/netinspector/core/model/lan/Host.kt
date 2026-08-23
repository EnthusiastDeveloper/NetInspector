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
    /** docs/device-identification-ideas.md D - a user-set label overriding every automated
     * naming signal. Never populated by [mergeObservation] (no observation source produces
     * one); the UI layer overlays it from [nicknameKey], since a nickname outlives any single
     * host record still being rebuilt sweep to sweep. */
    val nickname: String? = null,
)

/** design §11.3 - hostname precedence for the primary display name; every variant stays
 * visible (with its source) on the detail screen regardless of which one this picks.
 * `UPNP_HOSTS` (docs/device-identification-ideas.md C1) and `SNMP` (B1) sit alongside
 * `NETBIOS` - all three are a router/device self-reporting a name, stronger than a generic
 * reverse-DNS PTR record but not as curated as mDNS/SSDP's own friendly-name fields. */
private val HOSTNAME_PRECEDENCE =
    listOf(
        EvidenceSource.MDNS,
        EvidenceSource.SSDP,
        EvidenceSource.NETBIOS,
        EvidenceSource.UPNP_HOSTS,
        EvidenceSource.SNMP,
        EvidenceSource.REVERSE_DNS,
    )

val Host.primaryHostname: String?
    get() = HOSTNAME_PRECEDENCE.firstNotNullOfOrNull { hostnames[it] }

/** docs/device-identification-ideas.md D - "keyed by MAC when available, or a stable IP+
 * hostname combo otherwise": [macAddress] only exists for NetBIOS-observed hosts (A3), so most
 * hosts fall back to address+hostname. Plain [address] alone isn't stable across a DHCP lease
 * change, but address+hostname together are the closest stable identity available without a
 * MAC. */
fun Host.nicknameKey(): String =
    macAddress?.let { "mac:$it" } ?: "addr:${address.hostAddress}|${primaryHostname.orEmpty()}"
