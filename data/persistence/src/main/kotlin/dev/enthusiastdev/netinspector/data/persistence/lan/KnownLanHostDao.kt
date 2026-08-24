package dev.enthusiastdev.netinspector.data.persistence.lan

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface KnownLanHostDao {
    @Query("SELECT * FROM known_lan_host")
    suspend fun getAll(): List<KnownLanHostEntity>

    @Upsert
    suspend fun upsertAll(entities: List<KnownLanHostEntity>)

    @Query("DELETE FROM known_lan_host WHERE lastSeenMillis < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long)
}
