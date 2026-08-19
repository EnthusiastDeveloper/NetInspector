package dev.enthusiastdev.netinspector.core.model.lan

import java.net.Inet4Address

/** design §3, §8.1 - one row per IPv4 address on the LAN. [macAddress] stays null in
 * practice: Android blocks reading the ARP table on an unrooted device (C-01), so it exists
 * only so a future rooted/privileged build can populate it without a model change. The UI
 * must not render an empty "MAC" row - it shows the identification signals it actually has. */
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
