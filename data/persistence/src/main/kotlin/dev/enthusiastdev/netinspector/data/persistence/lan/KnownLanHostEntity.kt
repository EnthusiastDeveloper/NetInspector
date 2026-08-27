package dev.enthusiastdev.netinspector.data.persistence.lan

import androidx.room.Entity
import androidx.room.PrimaryKey

/** ideas.md #24 - one row per LAN host identity (`Host.nicknameKey()`), tracked
 * across periodic background sweeps so `diffLanPresence` (`:core:model`) can tell a new host
 * from a previously-seen one, and a transient miss from a real vanish. Swept by the retention
 * worker on the same schedule as `scan_session` - see `RetentionCleanupWorker`. */
@Entity(tableName = "known_lan_host")
data class KnownLanHostEntity(
    @PrimaryKey val key: String,
    val displayName: String?,
    val firstSeenMillis: Long,
    val lastSeenMillis: Long,
    val consecutiveMissedSweeps: Int,
    val vanishedAlertSent: Boolean,
)
