package dev.enthusiastdev.netinspector.data.persistence.scan

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface KnownApDao {
    @Query("SELECT * FROM known_ap WHERE bssid = :bssid")
    suspend fun get(bssid: String): KnownApEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(ap: KnownApEntity)

    @Query("SELECT * FROM known_ap ORDER BY lastSeenMillis DESC")
    fun observeAll(): Flow<List<KnownApEntity>>
}
