package dev.enthusiastdev.netinspector.data.persistence.scan

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** design §10 - one AP observation joined to a [ScanSessionEntity], cascade-deleted with it.
 * [security]/[band]/[standard] are enum `.name` values rather than a `TypeConverter`-mapped
 * column, matching how the rest of the app keeps enums as plain strings at persistence
 * boundaries (design §2.1 - repositories stay in plain data, no framework/library types leak
 * across a module boundary; a `TypeConverter` would be one more thing the DB schema JSON has
 * to pin down). */
@Entity(
    tableName = "scan_observation",
    foreignKeys = [
        ForeignKey(
            entity = ScanSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId"), Index("bssid")],
)
data class ScanObservationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val bssid: String,
    val ssid: String,
    val rssiDbm: Int,
    val centerFrequencyMhz: Int,
    val channelWidthMhz: Int,
    val band: String,
    val security: String,
    val standard: String,
)
