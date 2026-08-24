package dev.enthusiastdev.netinspector.data.persistence.host

import androidx.room.Entity
import androidx.room.PrimaryKey

/** design §10's aspirational `saved_host` table, first implemented for
 * docs/device-identification-ideas.md D (manual nickname per device). [key] is a `Host`'s
 * `nicknameKey()` (`core/model`) - MAC-based when available, address+hostname otherwise -
 * never the raw IP alone, since a DHCP lease change would silently orphan the row. */
@Entity(tableName = "saved_host")
data class SavedHostEntity(
    @PrimaryKey val key: String,
    val nickname: String,
    /** improvement-ideas.md #24 - "this host is expected to come and go" (a laptop that
     * sleeps, a phone that leaves and returns home); suppresses the periodic background
     * sweep's vanish/reappear alerts for this key. Independent of [nickname] - a row can carry
     * either, both, so the delete-when-empty logic in `SavedHostRepository` checks both. */
    val isKnownDevice: Boolean = false,
)
