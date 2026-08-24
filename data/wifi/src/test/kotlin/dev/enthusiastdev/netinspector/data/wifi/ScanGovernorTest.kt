package dev.enthusiastdev.netinspector.data.wifi

import android.content.Context
import android.net.wifi.WifiManager
import com.google.common.truth.Truth.assertThat
import dev.enthusiastdev.netinspector.core.model.wifi.ScanOutcome
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** design §6.1 - the governor is the one piece of scanning logic every user-visible refresh
 * behaviour (`WifiViewModel.onRefresh`, `PeriodicScanWorker`) depends on, yet had no direct
 * coverage: its quota arithmetic and the exact outcome it returns in each state were only ever
 * exercised indirectly through whatever called it. That gap is what let a `WifiViewModel` bug
 * ship: `onRefresh` assumed a throttled `requestScan` call took some time to fail, when in fact
 * it returns the moment it decides to throttle - see `onRefresh`'s own comment and the last test
 * below, which pins that behaviour down directly at the source. */
@OptIn(ExperimentalCoroutinesApi::class)
class ScanGovernorTest {
    private val context = mockk<Context>(relaxed = true)
    private val wifiManager =
        mockk<WifiManager>().apply {
            every { isScanThrottleEnabled() } returns true
            every { startScan() } returns true
        }
    private val clock = MutableClock(Instant.parse("2026-01-01T00:00:00Z"))

    private fun governor() = ScanGovernor(context, wifiManager, clock)

    @Test
    fun `a scan within budget starts and consumes one token`() =
        runTest {
            val governor = governor()

            val outcome = governor.requestScan(isUserInitiated = false)

            assertThat(outcome).isEqualTo(ScanOutcome.Started)
            assertThat(governor.budget().remainingCalls).isEqualTo(3)
        }

    @Test
    fun `a non-user-initiated scan is throttled once the two non-reserved tokens are spent`() =
        runTest {
            val governor = governor()
            governor.requestScan(isUserInitiated = false)
            governor.requestScan(isUserInitiated = false)

            val outcome = governor.requestScan(isUserInitiated = false)

            assertThat(outcome).isInstanceOf(ScanOutcome.Throttled::class.java)
        }

    @Test
    fun `a user-initiated refresh can still dip into the two reserved tokens`() =
        runTest {
            val governor = governor()
            governor.requestScan(isUserInitiated = false)
            governor.requestScan(isUserInitiated = false)

            val outcome = governor.requestScan(isUserInitiated = true)

            assertThat(outcome).isEqualTo(ScanOutcome.Started)
        }

    @Test
    fun `the fifth call in the window is throttled regardless of who asked`() =
        runTest {
            val governor = governor()
            repeat(4) { governor.requestScan(isUserInitiated = true) }

            val outcome = governor.requestScan(isUserInitiated = true)

            assertThat(outcome).isInstanceOf(ScanOutcome.Throttled::class.java)
        }

    @Test
    fun `retryAt reflects when the oldest token in the window expires`() =
        runTest {
            val governor = governor()
            repeat(4) { governor.requestScan(isUserInitiated = true) }

            val outcome = governor.requestScan(isUserInitiated = true) as ScanOutcome.Throttled

            assertThat(outcome.retryAt).isEqualTo(clock.instant().plus(Duration.ofMinutes(2)))
        }

    @Test
    fun `a failed startScan does not consume a token`() =
        runTest {
            every { wifiManager.startScan() } returns false
            val governor = governor()

            val outcome = governor.requestScan(isUserInitiated = true)

            assertThat(outcome).isInstanceOf(ScanOutcome.Failed::class.java)
            assertThat(governor.budget().remainingCalls).isEqualTo(4)
        }

    @Test
    fun `a token older than the rolling window is pruned, freeing budget again`() =
        runTest {
            val governor = governor()
            repeat(4) { governor.requestScan(isUserInitiated = true) }
            assertThat(governor.requestScan(isUserInitiated = true)).isInstanceOf(ScanOutcome.Throttled::class.java)

            clock.advanceBy(Duration.ofMinutes(2).plusSeconds(1))

            assertThat(governor.requestScan(isUserInitiated = true)).isEqualTo(ScanOutcome.Started)
        }

    @Test
    fun `throttling disabled on the device raises cadence to once every five seconds instead of the quota`() =
        runTest {
            every { wifiManager.isScanThrottleEnabled() } returns false
            val governor = governor()
            governor.requestScan(isUserInitiated = true)

            val immediateRetry = governor.requestScan(isUserInitiated = true)
            assertThat(immediateRetry).isInstanceOf(ScanOutcome.Throttled::class.java)

            clock.advanceBy(Duration.ofSeconds(6))

            assertThat(governor.requestScan(isUserInitiated = true)).isEqualTo(ScanOutcome.Started)
        }

    @Test
    fun `throttling never suspends the caller - it returns the moment it decides to throttle`() =
        runTest {
            val governor = governor()
            repeat(4) { governor.requestScan(isUserInitiated = true) }

            val before = currentTime
            val outcome = governor.requestScan(isUserInitiated = true)

            assertThat(currentTime).isEqualTo(before)
            assertThat(outcome).isInstanceOf(ScanOutcome.Throttled::class.java)
        }
}

private class MutableClock(
    private var current: Instant,
) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = current

    fun advanceBy(duration: Duration) {
        current = current.plus(duration)
    }
}
