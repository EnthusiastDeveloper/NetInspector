package dev.enthusiastdev.netinspector.data.persistence

import androidx.room.Database
import androidx.room.RoomDatabase
import dev.enthusiastdev.netinspector.data.persistence.wol.SavedWolTarget
import dev.enthusiastdev.netinspector.data.persistence.wol.SavedWolTargetDao

/** design §10 - schema version 1, `exportSchema = true` (wired in `build.gradle.kts`'s `room {}`
 * block since Phase 0; this is the first entity to actually populate the schema directory). */
@Database(entities = [SavedWolTarget::class], version = 1, exportSchema = true)
abstract class NetInspectorDatabase : RoomDatabase() {
    abstract fun savedWolTargetDao(): SavedWolTargetDao
}
