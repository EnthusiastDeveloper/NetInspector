package dev.enthusiastdev.netinspector.ui.screens.tools.history

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val ABSOLUTE_DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, HH:mm").withZone(ZoneId.systemDefault())

/** Shared by both history lists: recent rows read better as "how long ago" than a timestamp,
 * but past a week the relative form stops being useful at a glance - falls back to a date. */
internal fun Long.asRelativeTime(nowMillis: Long = System.currentTimeMillis()): String {
    val diffSeconds = ((nowMillis - this) / 1000).coerceAtLeast(0)
    val minutes = diffSeconds / 60
    val hours = minutes / 60
    val days = hours / 24
    return when {
        diffSeconds < 60 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        else -> ABSOLUTE_DATE_FORMAT.format(Instant.ofEpochMilli(this))
    }
}
