package dev.enthusiastdev.netinspector.data.persistence.wol

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedWolTargetDao {
    @Query("SELECT * FROM saved_wol_target ORDER BY label")
    fun observeAll(): Flow<List<SavedWolTarget>>

    @Insert
    suspend fun insert(target: SavedWolTarget)

    @Delete
    suspend fun delete(target: SavedWolTarget)
}
