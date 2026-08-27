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

/** design §10/docs/ideas.md D - version 3 adds one table for manual
 * per-host nicknames, purely additive like 1→2. SQL copied verbatim from the KSP-generated
 * `schemas/.../3.json` `createSql` entry, same convention as [MIGRATION_1_2]. */
val MIGRATION_2_3 =
    object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `saved_host` (`key` TEXT NOT NULL, `nickname` TEXT NOT NULL, " +
                    "PRIMARY KEY(`key`))",
            )
        }
    }

/** design §10/ideas.md #24 - version 4 adds one table for periodic-sweep presence
 * tracking, and one column on the existing `saved_host` table for the "known device" flag.
 * `known_lan_host`'s `CREATE TABLE` is copied verbatim from the KSP-generated
 * `schemas/.../4.json`, same convention as [MIGRATION_2_3]; the `saved_host` change is an
 * `ALTER TABLE ADD COLUMN` instead, since it already has rows in the wild - a `DEFAULT 0`
 * is required here (unlike a fresh `CREATE TABLE`) so those existing rows get a value. */
val MIGRATION_3_4 =
    object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `saved_host` ADD COLUMN `isKnownDevice` INTEGER NOT NULL DEFAULT 0",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `known_lan_host` (`key` TEXT NOT NULL, `displayName` TEXT, " +
                    "`firstSeenMillis` INTEGER NOT NULL, `lastSeenMillis` INTEGER NOT NULL, " +
                    "`consecutiveMissedSweeps` INTEGER NOT NULL, `vanishedAlertSent` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`key`))",
            )
        }
    }

/** design §10/ideas.md #11 - version 5 adds seven nullable capability-tracking
 * columns to the existing `known_ap` table, all `ALTER TABLE ADD COLUMN` like [MIGRATION_3_4]'s
 * `isKnownDevice` case - unlike that one, none of these need a `DEFAULT`, since `NULL` is the
 * correct value for a row with no captured capability baseline yet (see `KnownApEntity`'s doc
 * comment). SQL copied verbatim from the KSP-generated `schemas/.../5.json`, same convention as
 * every migration in this file. */
val MIGRATION_4_5 =
    object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `known_ap` ADD COLUMN `security` TEXT")
            db.execSQL("ALTER TABLE `known_ap` ADD COLUMN `standard` TEXT")
            db.execSQL("ALTER TABLE `known_ap` ADD COLUMN `primaryChannel` INTEGER")
            db.execSQL("ALTER TABLE `known_ap` ADD COLUMN `previousSecurity` TEXT")
            db.execSQL("ALTER TABLE `known_ap` ADD COLUMN `previousStandard` TEXT")
            db.execSQL("ALTER TABLE `known_ap` ADD COLUMN `previousPrimaryChannel` INTEGER")
            db.execSQL("ALTER TABLE `known_ap` ADD COLUMN `lastCapabilityChangeMillis` INTEGER")
        }
    }
