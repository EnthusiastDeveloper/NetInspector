package dev.enthusiastdev.netinspector.data.persistence.host

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedHostDao {
    @Query("SELECT * FROM saved_host")
    fun observeAll(): Flow<List<SavedHostEntity>>

    @Query("SELECT * FROM saved_host")
    suspend fun getAll(): List<SavedHostEntity>

    @Query("SELECT * FROM saved_host WHERE key = :key")
    suspend fun get(key: String): SavedHostEntity?

    @Upsert
    suspend fun upsert(entity: SavedHostEntity)

    @Query("DELETE FROM saved_host WHERE key = :key")
    suspend fun delete(key: String)
}
