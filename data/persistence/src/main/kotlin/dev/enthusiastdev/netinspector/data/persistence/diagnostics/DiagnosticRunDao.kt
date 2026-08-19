package dev.enthusiastdev.netinspector.data.persistence.diagnostics

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DiagnosticRunDao {
    @Insert
    suspend fun insert(run: DiagnosticRunEntity): Long

    @Query("SELECT * FROM diagnostic_run ORDER BY timestampMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<DiagnosticRunEntity>>

    @Query("SELECT * FROM diagnostic_run WHERE id = :id")
    suspend fun get(id: Long): DiagnosticRunEntity?

    /** design §8 acceptance / decision #4 - 90-day default, user-adjustable. */
    @Query("DELETE FROM diagnostic_run WHERE timestampMillis < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long): Int
}
