package dev.enthusiastdev.netinspector.data.persistence.scan

import com.google.common.truth.Truth.assertThat
import dev.enthusiastdev.netinspector.core.model.wifi.AccessPoint
import dev.enthusiastdev.netinspector.core.model.wifi.Band
import dev.enthusiastdev.netinspector.core.model.wifi.ChannelSpan
import dev.enthusiastdev.netinspector.core.model.wifi.ScanSnapshot
import dev.enthusiastdev.netinspector.core.model.wifi.SecurityType
import dev.enthusiastdev.netinspector.core.model.wifi.WifiStandard
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant

private const val BSSID = "AA:BB:CC:00:00:01"

class DefaultScanHistoryRepositoryTest {
    @Test
    fun `first sighting of a BSSID records a baseline without flagging a change`() =
        runTest {
            val (repository, knownApDao) = repositoryWithNoExistingKnownAp()

            repository.record(snapshot(accessPoint()), connectedBssid = null)

            val captured = slot<KnownApEntity>()
            coVerify { knownApDao.upsert(capture(captured)) }
            assertThat(captured.captured.security).isEqualTo("WPA2")
            assertThat(captured.captured.standard).isEqualTo("AX")
            assertThat(captured.captured.primaryChannel).isEqualTo(6)
            assertThat(captured.captured.lastCapabilityChangeMillis).isNull()
            assertThat(captured.captured.previousSecurity).isNull()
        }

    @Test
    fun `a second scan with identical capabilities does not flag a change`() =
        runTest {
            val existing = knownAp(security = "WPA2", standard = "AX", primaryChannel = 6)
            val (repository, knownApDao) = repositoryWithExistingKnownAp(existing)

            repository.record(snapshot(accessPoint()), connectedBssid = null)

            val captured = slot<KnownApEntity>()
            coVerify { knownApDao.upsert(capture(captured)) }
            assertThat(captured.captured.lastCapabilityChangeMillis).isNull()
            assertThat(captured.captured.previousSecurity).isNull()
        }

    @Test
    fun `a security change freezes the previous value and stamps the change time`() =
        runTest {
            val existing = knownAp(security = "WPA3", standard = "AX", primaryChannel = 6)
            val (repository, knownApDao) = repositoryWithExistingKnownAp(existing)
            val recordedAt = Instant.parse("2026-01-01T00:00:00Z")

            repository.record(snapshot(accessPoint(), timestamp = recordedAt), connectedBssid = null)

            val captured = slot<KnownApEntity>()
            coVerify { knownApDao.upsert(capture(captured)) }
            assertThat(captured.captured.security).isEqualTo("WPA2")
            assertThat(captured.captured.previousSecurity).isEqualTo("WPA3")
            assertThat(captured.captured.previousStandard).isEqualTo("AX")
            assertThat(captured.captured.previousPrimaryChannel).isEqualTo(6)
            assertThat(captured.captured.lastCapabilityChangeMillis).isEqualTo(recordedAt.toEpochMilli())
        }

    @Test
    fun `a channel change is detected independently of security and standard`() =
        runTest {
            val existing = knownAp(security = "WPA2", standard = "AX", primaryChannel = 11)
            val (repository, knownApDao) = repositoryWithExistingKnownAp(existing)

            repository.record(snapshot(accessPoint()), connectedBssid = null)

            val captured = slot<KnownApEntity>()
            coVerify { knownApDao.upsert(capture(captured)) }
            // A notable change freezes the whole previous snapshot, not just the field that
            // actually differed - otherwise the pre-change security/standard would be lost.
            assertThat(captured.captured.previousPrimaryChannel).isEqualTo(11)
            assertThat(captured.captured.previousSecurity).isEqualTo("WPA2")
            assertThat(captured.captured.previousStandard).isEqualTo("AX")
            assertThat(captured.captured.lastCapabilityChangeMillis).isNotNull()
        }

    @Test
    fun `a pre-migration row with no captured baseline does not false-positive a change`() =
        runTest {
            val legacyRow = knownAp(security = null, standard = null, primaryChannel = null)
            val (repository, knownApDao) = repositoryWithExistingKnownAp(legacyRow)

            repository.record(snapshot(accessPoint()), connectedBssid = null)

            val captured = slot<KnownApEntity>()
            coVerify { knownApDao.upsert(capture(captured)) }
            assertThat(captured.captured.lastCapabilityChangeMillis).isNull()
            assertThat(captured.captured.previousSecurity).isNull()
            assertThat(captured.captured.security).isEqualTo("WPA2")
        }

    private fun repositoryWithNoExistingKnownAp() = buildRepository(existing = null)

    private fun repositoryWithExistingKnownAp(existing: KnownApEntity) = buildRepository(existing)

    private fun buildRepository(existing: KnownApEntity?): Pair<DefaultScanHistoryRepository, KnownApDao> {
        val sessionDao = mockk<ScanSessionDao>()
        coEvery { sessionDao.insert(any()) } returns 1L
        val observationDao = mockk<ScanObservationDao>(relaxUnitFun = true)
        val knownApDao = mockk<KnownApDao>()
        coEvery { knownApDao.get(BSSID) } returns existing
        coEvery { knownApDao.upsert(any()) } returns Unit
        return DefaultScanHistoryRepository(sessionDao, observationDao, knownApDao) to knownApDao
    }

    private fun knownAp(
        security: String?,
        standard: String?,
        primaryChannel: Int?,
    ) = KnownApEntity(
        bssid = BSSID,
        ssid = "Test Network",
        vendor = null,
        firstSeenMillis = 0,
        lastSeenMillis = 0,
        bestRssiDbm = -60,
        security = security,
        standard = standard,
        primaryChannel = primaryChannel,
    )

    private fun snapshot(
        accessPoint: AccessPoint,
        timestamp: Instant = Instant.parse("2026-01-01T00:00:00Z"),
    ) = ScanSnapshot(listOf(accessPoint), timestamp)

    private fun accessPoint(): AccessPoint =
        AccessPoint(
            bssid = BSSID,
            ssid = "Test Network",
            rssiDbm = -60,
            span = ChannelSpan(centerMhz = 2437, widthMhz = 20, primaryChannel = 6, band = Band.GHZ_2_4),
            secondarySpan = null,
            security = setOf(SecurityType.WPA2),
            standard = WifiStandard.AX,
            vendor = null,
            isConnected = false,
            isDfsChannel = false,
            is6GhzPsc = false,
            firstSeen = Instant.parse("2026-01-01T00:00:00Z"),
            lastSeen = Instant.parse("2026-01-01T00:00:00Z"),
        )
}
