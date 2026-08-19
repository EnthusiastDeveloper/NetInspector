package dev.enthusiastdev.netinspector.ui.screens.devices

import dev.enthusiastdev.netinspector.core.model.lan.EvidenceSource
import dev.enthusiastdev.netinspector.core.model.lan.Host
import dev.enthusiastdev.netinspector.core.model.lan.HostConfidence
import dev.enthusiastdev.netinspector.core.model.lan.primaryHostname
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val CLOCK_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

internal fun Instant.asClockTime(): String = CLOCK_FORMAT.format(this)

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
