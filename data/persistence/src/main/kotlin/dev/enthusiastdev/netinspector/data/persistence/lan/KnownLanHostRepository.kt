package dev.enthusiastdev.netinspector.data.persistence.lan

import dev.enthusiastdev.netinspector.core.model.lan.Host
import dev.enthusiastdev.netinspector.core.model.lan.KnownHostRecord
import dev.enthusiastdev.netinspector.core.model.lan.LanPresenceDiff
import dev.enthusiastdev.netinspector.core.model.lan.diffLanPresence
import dev.enthusiastdev.netinspector.data.persistence.host.SavedHostDao
import java.time.Clock
import javax.inject.Inject

/** ideas.md #24 - all the I/O around `diffLanPresence` (`:core:model`, the pure
 * decision logic): read prior state, read the user's "known device" flags
 * (`SavedHostRepository`/`SavedHostDao` - same module, so this is an intra-module dependency,
 * not the cross-`:data:*`-module kind design §2.1 forbids), diff, persist the new state, hand
 * back what's alert-worthy for the caller (`PeriodicScanWorker`, `:app`) to notify on. */
interface KnownLanHostRepository {
    suspend fun applySweep(confirmedHosts: List<Host>): LanPresenceDiff

    suspend fun deleteOlderThan(retentionDays: Int)
}

class DefaultKnownLanHostRepository
    @Inject
    constructor(
        private val knownLanHostDao: KnownLanHostDao,
        private val savedHostDao: SavedHostDao,
        private val clock: Clock,
    ) : KnownLanHostRepository {
        override suspend fun applySweep(confirmedHosts: List<Host>): LanPresenceDiff {
            val previousRecords = knownLanHostDao.getAll().associate { it.key to it.toRecord() }
            val knownDeviceKeys =
                savedHostDao
                    .getAll()
                    .filter { it.isKnownDevice }
                    .mapTo(mutableSetOf()) { it.key }

            val diff =
                diffLanPresence(
                    previousRecords = previousRecords,
                    currentConfirmedHosts = confirmedHosts,
                    nowMillis = clock.instant().toEpochMilli(),
                    knownDeviceKeys = knownDeviceKeys,
                )

            knownLanHostDao.upsertAll(diff.updatedRecords.map { it.toEntity() })
            return diff
        }

        override suspend fun deleteOlderThan(retentionDays: Int) {
            val cutoffMillis = clock.instant().minusSeconds(retentionDays * 24L * 3600L).toEpochMilli()
            knownLanHostDao.deleteOlderThan(cutoffMillis)
        }
    }

private fun KnownLanHostEntity.toRecord() =
    KnownHostRecord(
        key = key,
        displayName = displayName,
        firstSeenMillis = firstSeenMillis,
        lastSeenMillis = lastSeenMillis,
        consecutiveMissedSweeps = consecutiveMissedSweeps,
        vanishedAlertSent = vanishedAlertSent,
    )

private fun KnownHostRecord.toEntity() =
    KnownLanHostEntity(
        key = key,
        displayName = displayName,
        firstSeenMillis = firstSeenMillis,
        lastSeenMillis = lastSeenMillis,
        consecutiveMissedSweeps = consecutiveMissedSweeps,
        vanishedAlertSent = vanishedAlertSent,
    )
