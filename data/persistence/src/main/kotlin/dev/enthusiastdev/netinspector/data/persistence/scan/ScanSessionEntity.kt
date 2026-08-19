package dev.enthusiastdev.netinspector.data.persistence.scan

import androidx.room.Entity
import androidx.room.PrimaryKey

/** design §10 - one row per passively-harvested scan generation (design §6.1's
 * `ScanSnapshot`). [connectedBssid] is whichever AP was the connected network at the moment
 * of this scan, nullable since the device may not be connected to any Wi-Fi at scan time. */
@Entity(tableName = "scan_session")
data class ScanSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMillis: Long,
    val connectedBssid: String?,
)
