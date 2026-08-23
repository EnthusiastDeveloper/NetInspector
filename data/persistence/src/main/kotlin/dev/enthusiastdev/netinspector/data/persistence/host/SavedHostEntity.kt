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
)
