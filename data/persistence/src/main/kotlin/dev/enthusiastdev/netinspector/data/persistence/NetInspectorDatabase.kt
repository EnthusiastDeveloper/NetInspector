package dev.enthusiastdev.netinspector.data.persistence

import androidx.room.Database
import androidx.room.RoomDatabase
import dev.enthusiastdev.netinspector.data.persistence.diagnostics.DiagnosticRunDao
import dev.enthusiastdev.netinspector.data.persistence.diagnostics.DiagnosticRunEntity
import dev.enthusiastdev.netinspector.data.persistence.scan.KnownApDao
import dev.enthusiastdev.netinspector.data.persistence.scan.KnownApEntity
import dev.enthusiastdev.netinspector.data.persistence.scan.ScanObservationDao
import dev.enthusiastdev.netinspector.data.persistence.scan.ScanObservationEntity
import dev.enthusiastdev.netinspector.data.persistence.scan.ScanSessionDao
import dev.enthusiastdev.netinspector.data.persistence.scan.ScanSessionEntity
import dev.enthusiastdev.netinspector.data.persistence.wol.SavedWolTarget
import dev.enthusiastdev.netinspector.data.persistence.wol.SavedWolTargetDao

/** design §10 - schema version 2, `exportSchema = true` (wired in `build.gradle.kts`'s `room {}`
 * block since Phase 0). Version 1 shipped with only [SavedWolTarget] (Phase 7); version 2
 * (Phase 8) adds scan history, known APs and diagnostic run history - see
 * `NetInspectorDatabaseMigrations.kt` for the 1→2 migration, purely additive (four new
 * tables, nothing about `saved_wol_target` changes). */
@Database(
    entities = [
        SavedWolTarget::class,
        ScanSessionEntity::class,
        ScanObservationEntity::class,
        KnownApEntity::class,
        DiagnosticRunEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class NetInspectorDatabase : RoomDatabase() {
    abstract fun savedWolTargetDao(): SavedWolTargetDao

    abstract fun scanSessionDao(): ScanSessionDao

    abstract fun scanObservationDao(): ScanObservationDao

    abstract fun knownApDao(): KnownApDao

    abstract fun diagnosticRunDao(): DiagnosticRunDao
}
