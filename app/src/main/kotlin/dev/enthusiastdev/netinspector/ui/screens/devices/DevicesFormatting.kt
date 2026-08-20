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

/** design §11.3 - a confirmed hostname always wins; absent that, a [DeviceHint] is a real but
 * *inferred* signal, so it's only used as a stand-in for the name (never silently equated with
 * one) and callers must style it distinctly - see [Host.hasInferredDisplayName]. */
internal fun Host.displayName(): String =
    when {
        isSelf -> "This device"
        isGateway -> primaryHostname?.let { "$it (gateway)" } ?: "Gateway"
        else -> primaryHostname ?: deviceHint?.label ?: "Unknown device"
    }

/** Whether [displayName] fell back to the [DeviceHint] guess rather than a confirmed hostname
 * - callers use this to render the name as visibly inferred (e.g. italic) and to skip a
 * separate hint line that would otherwise just repeat the title verbatim. */
internal val Host.hasInferredDisplayName: Boolean
    get() = !isSelf && !isGateway && primaryHostname == null && deviceHint != null

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
