package dev.enthusiastdev.netinspector.ui.screens.wifi

import dev.enthusiastdev.netinspector.core.model.wifi.ApCapabilityChange
import dev.enthusiastdev.netinspector.core.model.wifi.ApCapabilitySnapshot
import dev.enthusiastdev.netinspector.core.model.wifi.SecurityType
import dev.enthusiastdev.netinspector.core.model.wifi.WifiStandard
import dev.enthusiastdev.netinspector.core.model.wifi.diffApCapabilities
import dev.enthusiastdev.netinspector.data.persistence.scan.KnownApEntity
import dev.enthusiastdev.netinspector.ui.screens.connection.label
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val CLOCK_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
private val CHANGE_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("MMM d, HH:mm").withZone(ZoneId.systemDefault())

internal fun Instant.asClockTime(): String = CLOCK_FORMAT.format(this)

/** ideas.md #11 - a capability change can be days old by the time it's viewed, so
 * (unlike [asClockTime]) this carries a date, matching `HistoryFormatting.kt`'s
 * `ABSOLUTE_DATE_FORMAT` pattern. */
internal fun Instant.asChangeTimestamp(): String = CHANGE_TIMESTAMP_FORMAT.format(this)

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

/** Parses a `known_ap`/`scan_observation` stored raw security string (comma-joined
 * [SecurityType] names, e.g. `ScanHistoryRepository`'s `upsertKnownAp`) back to its display
 * label - shared by `WifiChangesScreen`'s `ObservedAp` rendering and ideas.md #11's
 * capability-change card. */
internal fun String.parsedSecurityLabel(): String =
    split(",")
        .filter { it.isNotBlank() }
        .mapNotNull { runCatching { SecurityType.valueOf(it) }.getOrNull() }
        .toSet()
        .label()

/** Parses a stored raw [WifiStandard] name back to its display label - same sharing rationale
 * as [parsedSecurityLabel]. */
internal fun String.parsedStandardLabel(): String =
    runCatching { WifiStandard.valueOf(this) }.getOrNull()?.label() ?: this

/** ideas.md #11 - the before/after snapshot plus which fields actually changed,
 * ready for [WifiDetailContent]'s capability-change card to render. */
internal data class ApCapabilityChangeDisplay(
    val previous: ApCapabilitySnapshot,
    val current: ApCapabilitySnapshot,
    val change: ApCapabilityChange,
    val changedAt: Instant,
)

/** Null when [KnownApEntity] has no complete before/after snapshot (a legacy pre-migration row
 * could in theory have a null subset - defensive, since the repository only ever stamps
 * `lastCapabilityChangeMillis` alongside a full snapshot) or the recomputed diff turns out not
 * notable after all. */
internal fun KnownApEntity.toCapabilityChangeDisplay(): ApCapabilityChangeDisplay? {
    val previous = previousCapabilitySnapshot() ?: return null
    val current = currentCapabilitySnapshot() ?: return null
    val changedAt = lastCapabilityChangeMillis?.let { Instant.ofEpochMilli(it) } ?: return null

    val change = diffApCapabilities(previous, current)
    return change.takeIf { it.isNotable }?.let { ApCapabilityChangeDisplay(previous, current, it, changedAt) }
}

private fun KnownApEntity.previousCapabilitySnapshot(): ApCapabilitySnapshot? {
    val security = previousSecurity ?: return null
    val standard = previousStandard ?: return null
    val channel = previousPrimaryChannel ?: return null
    return ApCapabilitySnapshot(security, standard, channel)
}

private fun KnownApEntity.currentCapabilitySnapshot(): ApCapabilitySnapshot? {
    val security = security ?: return null
    val standard = standard ?: return null
    val channel = primaryChannel ?: return null
    return ApCapabilitySnapshot(security, standard, channel)
}
