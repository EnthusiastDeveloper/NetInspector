package dev.enthusiastdev.netinspector.data.persistence

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** design §10/§8 acceptance ("Room migration from a seeded v1 database succeeds") - purely
 * additive: version 1 shipped with only `saved_wol_target` (Phase 7), version 2 (Phase 8) adds
 * four new tables and touches nothing existing, so there's no column-shape change to reconcile.
 * SQL copied verbatim from the KSP-generated `schemas/.../2.json` `createSql`/index entries
 * (`${TABLE_NAME}` substituted with the real name) rather than hand-written, so it can't drift
 * from what Room actually validates the opened database against at runtime. */
val MIGRATION_1_2 =
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `scan_session` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`timestampMillis` INTEGER NOT NULL, `connectedBssid` TEXT)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `scan_observation` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`sessionId` INTEGER NOT NULL, `bssid` TEXT NOT NULL, `ssid` TEXT NOT NULL, " +
                    "`rssiDbm` INTEGER NOT NULL, `centerFrequencyMhz` INTEGER NOT NULL, " +
                    "`channelWidthMhz` INTEGER NOT NULL, `band` TEXT NOT NULL, `security` TEXT NOT NULL, " +
                    "`standard` TEXT NOT NULL, FOREIGN KEY(`sessionId`) REFERENCES `scan_session`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_scan_observation_sessionId` ON `scan_observation` (`sessionId`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_scan_observation_bssid` ON `scan_observation` (`bssid`)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `known_ap` (`bssid` TEXT NOT NULL, `ssid` TEXT NOT NULL, " +
                    "`vendor` TEXT, `firstSeenMillis` INTEGER NOT NULL, `lastSeenMillis` INTEGER NOT NULL, " +
                    "`bestRssiDbm` INTEGER NOT NULL, PRIMARY KEY(`bssid`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `diagnostic_run` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`toolType` TEXT NOT NULL, `target` TEXT NOT NULL, `timestampMillis` INTEGER NOT NULL, " +
                    "`durationMillis` INTEGER NOT NULL, `summary` TEXT NOT NULL, `parametersJson` TEXT NOT NULL, " +
                    "`resultJson` TEXT NOT NULL)",
            )
        }
    }
