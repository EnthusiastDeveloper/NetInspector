package dev.enthusiastdev.netinspector.ui.screens.devices

import dev.enthusiastdev.netinspector.core.model.lan.Certainty
import dev.enthusiastdev.netinspector.core.model.lan.EvidenceSource
import dev.enthusiastdev.netinspector.core.model.lan.HostConfidence
import dev.enthusiastdev.netinspector.core.model.lan.HygieneRating
import dev.enthusiastdev.netinspector.core.model.lan.HygieneScore
import dev.enthusiastdev.netinspector.core.model.lan.OpenPort
import java.net.Inet4Address
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Numeric quad comparison, not lexicographic (`"10"` must sort after `"9"`, not before it). */
internal fun Inet4Address.toSortableString(): String =
    address.joinToString(".") {
        (it.toInt() and 0xFF).toString().padStart(3, '0')
    }

private val CLOCK_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

internal fun Instant.asClockTime(): String = CLOCK_FORMAT.format(this)

/** `InetAddress.getHostAddress()` is a Java platform type (`String!`) rather than a
 * Kotlin-checked nullable - every navigation key and deep-link argument built from a [Host]'s
 * address goes through this so a genuinely-null result degrades to an empty string instead of
 * an unchecked NPE risk at the call site. */
internal val Inet4Address.addressString: String get() = hostAddress.orEmpty()

internal fun Duration.asElapsedSeconds(): String = "${seconds.coerceAtLeast(0)}s"

internal fun HostConfidence.label(): String =
    when (this) {
        HostConfidence.CONFIRMED -> "Confirmed"
        HostConfidence.ANNOUNCED -> "Announced only"
        HostConfidence.STALE -> "Not seen this sweep"
    }

internal fun HostConfidence.explanation(): String =
    when (this) {
        HostConfidence.CONFIRMED ->
            "This device answered directly during the scan - a ping reply or a successful " +
                "TCP connection - so it's definitely live and reachable right now."
        HostConfidence.ANNOUNCED ->
            "This device was only seen broadcasting a service announcement (mDNS, SSDP, or " +
                "NetBIOS) - it never answered a direct probe. It may still be reachable but " +
                "have ICMP/TCP probing blocked, or the announcement could be stale."
        HostConfidence.STALE ->
            "This device was seen in a previous scan but not in the most recent one - it's " +
                "probably offline or out of range now. It stays visible, greyed out, for one " +
                "more scan before being dropped from the list."
    }

internal fun EvidenceSource.label(): String =
    when (this) {
        EvidenceSource.ICMP -> "ICMP ping"
        EvidenceSource.TCP_CONNECT -> "TCP connect"
        EvidenceSource.MDNS -> "mDNS"
        EvidenceSource.SSDP -> "SSDP/UPnP"
        EvidenceSource.NETBIOS -> "NetBIOS"
        EvidenceSource.SNMP -> "SNMP"
        EvidenceSource.UPNP_HOSTS -> "Router-reported (UPnP Hosts)"
        EvidenceSource.REVERSE_DNS -> "Reverse DNS"
        EvidenceSource.GATEWAY -> "Gateway (known)"
        EvidenceSource.SELF -> "This device (known)"
    }

internal fun Certainty.label(): String =
    when (this) {
        Certainty.CONFIRMED -> "Confirmed"
        Certainty.LIKELY -> "Likely"
        Certainty.POSSIBLE -> "Possible"
    }

/** design §8.2 Stage C - "$port ($serviceGuess) - $banner", degrading gracefully as each part
 * goes missing (a raw connect-scan hit with no name and no banner still reads as `"9999"`). */
internal fun OpenPort.label(): String {
    val portAndService = if (serviceGuess != null) "$port ($serviceGuess)" else port.toString()
    return if (banner != null) "$portAndService - $banner" else portAndService
}

internal fun HygieneRating.label(): String =
    when (this) {
        HygieneRating.EXCELLENT -> "Excellent"
        HygieneRating.GOOD -> "Good"
        HygieneRating.FAIR -> "Fair"
        HygieneRating.POOR -> "Poor"
        HygieneRating.CRITICAL -> "Critical"
    }

/** docs/improvement-ideas.md #1 - the caption under a hygiene score badge, naming what the
 * score's basis actually is rather than leaving the number to speak for itself. */
internal fun HygieneScore.findingsSummary(): String =
    when (findings.size) {
        0 -> "No known port risks found"
        1 -> "1 risky open port found"
        else -> "${findings.size} risky open ports found"
    }
