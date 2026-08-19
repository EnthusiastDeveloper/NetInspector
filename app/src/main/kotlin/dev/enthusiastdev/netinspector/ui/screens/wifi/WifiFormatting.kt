package dev.enthusiastdev.netinspector.ui.screens.wifi

import dev.enthusiastdev.netinspector.core.model.wifi.SecurityType
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val CLOCK_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

internal fun Instant.asClockTime(): String = CLOCK_FORMAT.format(this)

/** mm:ss for anything a minute or over, otherwise just seconds - the countdown never runs
 * long enough (design §6.1: 2-minute window) to need an hours component. */
internal fun Duration.asCountdown(): String {
    val totalSeconds = seconds.coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) "%d:%02d".format(minutes, seconds) else "${seconds}s"
}

/** design §6.2: a WPA2/WPA3 transition network and an OWE network both need explicit
 * labels - neither may render as plain "Open". */
internal fun Set<SecurityType>.label(): String =
    when {
        isEmpty() -> "Unknown"
        SecurityType.WPA2 in this && SecurityType.WPA3 in this -> "WPA2/WPA3 transition"
        size == 1 -> single().label()
        else -> joinToString("/") { it.label() }
    }

private fun SecurityType.label(): String =
    when (this) {
        SecurityType.OPEN -> "Open"
        SecurityType.OWE -> "Open (encrypted)"
        SecurityType.WEP -> "WEP"
        SecurityType.WPA2 -> "WPA2"
        SecurityType.WPA3 -> "WPA3"
        SecurityType.EAP -> "Enterprise (EAP)"
        SecurityType.UNKNOWN -> "Unknown"
    }
