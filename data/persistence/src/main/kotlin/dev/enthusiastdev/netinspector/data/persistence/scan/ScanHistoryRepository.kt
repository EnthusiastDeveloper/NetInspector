package dev.enthusiastdev.netinspector.data.persistence.scan

import dev.enthusiastdev.netinspector.core.model.wifi.AccessPoint
import dev.enthusiastdev.netinspector.core.model.wifi.ApCapabilitySnapshot
import dev.enthusiastdev.netinspector.core.model.wifi.ScanSnapshot
import dev.enthusiastdev.netinspector.core.model.wifi.diffApCapabilities
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject

/** design §10 - persists one row set per [ScanSnapshot] (design §6.1's "one passively-harvested
 * scan generation"), plus the [KnownApEntity] upsert for indefinite per-BSSID tracking. Takes
 * `:core:model` types directly rather than a parallel DTO hierarchy - `:data:persistence`
 * already depends on `:core:model` (design §2.1), and this repository's own entities are the
 * DB-shaped projection, so no third shape is needed in between. */
interface ScanHistoryRepository {
    suspend fun record(
        snapshot: ScanSnapshot,
        connectedBssid: String?,
    )

    fun recentSessions(limit: Int = 100): Flow<List<ScanSessionEntity>>

    fun observationsForSession(sessionId: Long): Flow<List<ScanObservationEntity>>

    fun rssiHistory(
        bssid: String,
        since: Instant,
    ): Flow<List<RssiHistoryPoint>>

    fun knownAps(): Flow<List<KnownApEntity>>

    suspend fun deleteSessionsOlderThan(retentionDays: Int)
}

class DefaultScanHistoryRepository
    @Inject
    constructor(
        private val sessionDao: ScanSessionDao,
        private val observationDao: ScanObservationDao,
        private val knownApDao: KnownApDao,
    ) : ScanHistoryRepository {
        override suspend fun record(
            snapshot: ScanSnapshot,
            connectedBssid: String?,
        ) {
            val timestampMillis = snapshot.timestamp.toEpochMilli()
            val sessionId =
                sessionDao.insert(
                    ScanSessionEntity(timestampMillis = timestampMillis, connectedBssid = connectedBssid),
                )
            observationDao.insertAll(snapshot.accessPoints.map { it.toObservation(sessionId) })
            for (accessPoint in snapshot.accessPoints) {
                upsertKnownAp(accessPoint, timestampMillis)
            }
        }

        /** ideas.md #11 - a notable capability change is only detected when
         * [existing] already carries a captured baseline ([KnownApEntity.toBaseline] returns
         * non-null); a brand-new BSSID or a pre-migration row with no baseline just records one
         * silently, per [KnownApEntity]'s doc comment. */
        private suspend fun upsertKnownAp(
            accessPoint: AccessPoint,
            timestampMillis: Long,
        ) {
            val existing = knownApDao.get(accessPoint.bssid)
            val current =
                ApCapabilitySnapshot(
                    security = accessPoint.security.joinToString(",") { it.name },
                    standard = accessPoint.standard.name,
                    primaryChannel = accessPoint.span.primaryChannel,
                )
            val baseline = existing?.toBaseline()
            val notable = baseline != null && diffApCapabilities(baseline, current).isNotable

            knownApDao.upsert(
                KnownApEntity(
                    bssid = accessPoint.bssid,
                    ssid = accessPoint.ssid,
                    vendor = accessPoint.vendor,
                    firstSeenMillis = existing?.firstSeenMillis ?: timestampMillis,
                    lastSeenMillis = timestampMillis,
                    bestRssiDbm = maxOf(accessPoint.rssiDbm, existing?.bestRssiDbm ?: Int.MIN_VALUE),
                    security = current.security,
                    standard = current.standard,
                    primaryChannel = current.primaryChannel,
                    previousSecurity = if (notable) baseline?.security else existing?.previousSecurity,
                    previousStandard = if (notable) baseline?.standard else existing?.previousStandard,
                    previousPrimaryChannel =
                        if (notable) baseline?.primaryChannel else existing?.previousPrimaryChannel,
                    lastCapabilityChangeMillis =
                        if (notable) timestampMillis else existing?.lastCapabilityChangeMillis,
                ),
            )
        }

        override fun recentSessions(limit: Int) = sessionDao.observeRecent(limit)

        override fun observationsForSession(sessionId: Long) = observationDao.observeForSession(sessionId)

        override fun rssiHistory(
            bssid: String,
            since: Instant,
        ) = observationDao.observeRssiHistory(bssid, since.toEpochMilli())

        override fun knownAps() = knownApDao.observeAll()

        override suspend fun deleteSessionsOlderThan(retentionDays: Int) {
            val cutoff = Instant.now().minusSeconds(retentionDays * 24L * 3600L)
            sessionDao.deleteOlderThan(cutoff.toEpochMilli())
        }
    }

/** Null when this row has no captured capability baseline yet (a brand-new BSSID, or a row
 * written before ideas.md #11's migration) - see [KnownApEntity]'s doc comment. */
private fun KnownApEntity.toBaseline(): ApCapabilitySnapshot? {
    val security = security ?: return null
    val standard = standard ?: return null
    val primaryChannel = primaryChannel ?: return null
    return ApCapabilitySnapshot(security, standard, primaryChannel)
}

private fun AccessPoint.toObservation(sessionId: Long) =
    ScanObservationEntity(
        sessionId = sessionId,
        bssid = bssid,
        ssid = ssid,
        rssiDbm = rssiDbm,
        centerFrequencyMhz = span.centerMhz,
        channelWidthMhz = span.widthMhz,
        band = span.band.name,
        security = security.joinToString(",") { it.name },
        standard = standard.name,
    )
