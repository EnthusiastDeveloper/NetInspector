package dev.enthusiastdev.netinspector.data.persistence.scan

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanObservationDao {
    @Insert
    suspend fun insertAll(observations: List<ScanObservationEntity>)

    @Query("SELECT * FROM scan_observation WHERE sessionId = :sessionId ORDER BY rssiDbm DESC")
    fun observeForSession(sessionId: Long): Flow<List<ScanObservationEntity>>

    /** design §11.1 History - "RSSI history per BSSID": joins through the session table for
     * its timestamp, since the observation row itself carries no timestamp of its own. */
    @Query(
        """
        SELECT scan_session.timestampMillis AS timestampMillis, scan_observation.rssiDbm AS rssiDbm
        FROM scan_observation
        INNER JOIN scan_session ON scan_session.id = scan_observation.sessionId
        WHERE scan_observation.bssid = :bssid AND scan_session.timestampMillis >= :sinceMillis
        ORDER BY scan_session.timestampMillis ASC
        """,
    )
    fun observeRssiHistory(
        bssid: String,
        sinceMillis: Long,
    ): Flow<List<RssiHistoryPoint>>
}

data class RssiHistoryPoint(
    val timestampMillis: Long,
    val rssiDbm: Int,
)
