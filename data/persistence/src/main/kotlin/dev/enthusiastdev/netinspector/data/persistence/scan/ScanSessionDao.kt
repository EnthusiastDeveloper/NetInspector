package dev.enthusiastdev.netinspector.data.persistence.scan

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanSessionDao {
    @Insert
    suspend fun insert(session: ScanSessionEntity): Long

    @Query("SELECT * FROM scan_session ORDER BY timestampMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ScanSessionEntity>>

    @Query("SELECT * FROM scan_session WHERE id = :id")
    suspend fun get(id: Long): ScanSessionEntity?

    /** design §8 acceptance / decision #4 - 30-day default, user-adjustable. Cascades to
     * [ScanObservationEntity] rows via the FK's `onDelete = CASCADE`. */
    @Query("DELETE FROM scan_session WHERE timestampMillis < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long): Int
}
