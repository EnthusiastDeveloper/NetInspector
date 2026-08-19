package dev.enthusiastdev.netinspector.ui.screens.devices

import dev.enthusiastdev.netinspector.core.model.lan.Certainty
import dev.enthusiastdev.netinspector.core.model.lan.EvidenceSource
import dev.enthusiastdev.netinspector.core.model.lan.Host
import dev.enthusiastdev.netinspector.core.model.lan.HostConfidence
import dev.enthusiastdev.netinspector.core.model.lan.OpenPort
import dev.enthusiastdev.netinspector.core.model.lan.primaryHostname
import java.net.Inet4Address
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val CLOCK_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

internal fun Instant.asClockTime(): String = CLOCK_FORMAT.format(this)

/** `InetAddress.getHostAddress()` is a Java platform type (`String!`) rather than a
 * Kotlin-checked nullable - every navigation key and deep-link argument built from a [Host]'s
 * address goes through this so a genuinely-null result degrades to an empty string instead of
 * an unchecked NPE risk at the call site. */
internal val Inet4Address.addressString: String get() = hostAddress.orEmpty()

internal fun Duration.asElapsedSeconds(): String = "${seconds.coerceAtLeast(0)}s"

internal fun Host.displayName(): String =
    when {
        isSelf -> "This device"
        isGateway -> primaryHostname?.let { "$it (gateway)" } ?: "Gateway"
        else -> primaryHostname ?: "Unknown device"
    }

internal fun HostConfidence.label(): String =
    when (this) {
        HostConfidence.CONFIRMED -> "Confirmed"
        HostConfidence.ANNOUNCED -> "Announced only"
        HostConfidence.STALE -> "Not seen this sweep"
    }

internal fun EvidenceSource.label(): String =
    when (this) {
        EvidenceSource.ICMP -> "ICMP ping"
        EvidenceSource.TCP_CONNECT -> "TCP connect"
        EvidenceSource.MDNS -> "mDNS"
        EvidenceSource.SSDP -> "SSDP/UPnP"
        EvidenceSource.NETBIOS -> "NetBIOS"
        EvidenceSource.REVERSE_DNS -> "Reverse DNS"
        EvidenceSource.GATEWAY -> "Gateway (known)"
        EvidenceSource.SELF -> "This device (known)"
    }

internal fun Certainty.label(): String =
    when (this) {
        Certainty.LIKELY -> "Likely"
        Certainty.POSSIBLE -> "Possible"
    }

/** design §8.2 Stage C - "$port ($serviceGuess) - $banner", degrading gracefully as each part
 * goes missing (a raw connect-scan hit with no name and no banner still reads as `"9999"`). */
internal fun OpenPort.label(): String {
    val portAndService = if (serviceGuess != null) "$port ($serviceGuess)" else port.toString()
    return if (banner != null) "$portAndService - $banner" else portAndService
}
