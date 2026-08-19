package dev.enthusiastdev.netinspector.data.persistence.scan

import androidx.room.Entity
import androidx.room.PrimaryKey

/** design §10 - stable per-BSSID record, indefinite retention (never swept by the periodic
 * cleanup worker, unlike [ScanSessionEntity]/[ScanObservationEntity]). */
@Entity(tableName = "known_ap")
data class KnownApEntity(
    @PrimaryKey val bssid: String,
    val ssid: String,
    val vendor: String?,
    val firstSeenMillis: Long,
    val lastSeenMillis: Long,
    val bestRssiDbm: Int,
)
