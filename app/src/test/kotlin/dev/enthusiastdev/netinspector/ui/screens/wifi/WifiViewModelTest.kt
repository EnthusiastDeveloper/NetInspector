package dev.enthusiastdev.netinspector.ui.screens.wifi

import android.content.Context
import com.google.common.truth.Truth.assertThat
import dev.enthusiastdev.netinspector.core.model.wifi.ScanOutcome
import dev.enthusiastdev.netinspector.data.persistence.preferences.AppSettingsRepository
import dev.enthusiastdev.netinspector.data.persistence.scan.ScanHistoryRepository
import dev.enthusiastdev.netinspector.data.wifi.ConnectionRepository
import dev.enthusiastdev.netinspector.data.wifi.WifiScanRepository
import dev.enthusiastdev.netinspector.usecase.RecordWifiScanUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant

/** `onRefresh`'s `isRefreshing` flag is the one thing driving `PullToRefreshBox`'s spinner
 * (see `WifiScreen.kt`), so its exact timing is a real contract, not an implementation detail -
 * these tests pin down the bug this class's own doc comment describes: a throttled or failed
 * scan must still hold `isRefreshing` up long enough for the indicator to animate in and out,
 * rather than flipping it true then false within the same frame. */
@OptIn(ExperimentalCoroutinesApi::class)
class WifiViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val wifiScanRepository =
        mockk<WifiScanRepository> {
            every { scanState } returns emptyFlow()
            every { scanSnapshots } returns emptyFlow()
        }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(): WifiViewModel =
        WifiViewModel(
            wifiScanRepository = wifiScanRepository,
            connectionRepository =
                mockk<ConnectionRepository>(relaxed = true) {
                    every { connectionSnapshot } returns emptyFlow()
                },
            recordWifiScan = RecordWifiScanUseCase(mockk(relaxed = true)),
            appSettingsRepository =
                mockk<AppSettingsRepository>(relaxed = true) {
                    every { rssiDisplayUnit } returns emptyFlow()
                },
            scanHistoryRepository =
                mockk<ScanHistoryRepository>(relaxed = true) {
                    every { knownAps() } returns emptyFlow()
                },
            context = mockk<Context>(relaxed = true),
        )

    @Test
    fun `a throttled refresh still holds the spinner up long enough to animate`() =
        runTest {
            coEvery { wifiScanRepository.requestScan(isUserInitiated = true) } returns
                ScanOutcome.Throttled(Instant.now())
            val viewModel = viewModel()

            viewModel.onRefresh()
            dispatcher.scheduler.runCurrent()
            assertThat(viewModel.isRefreshing.value).isTrue()

            dispatcher.scheduler.advanceTimeBy(499)
            assertThat(viewModel.isRefreshing.value).isTrue()

            dispatcher.scheduler.advanceTimeBy(2)
            assertThat(viewModel.isRefreshing.value).isFalse()
        }

    @Test
    fun `a failed scan also holds the spinner for the same minimum window as a throttled one`() =
        runTest {
            coEvery { wifiScanRepository.requestScan(isUserInitiated = true) } returns
                ScanOutcome.Failed("startScan() returned false")
            val viewModel = viewModel()

            viewModel.onRefresh()
            dispatcher.scheduler.runCurrent()
            assertThat(viewModel.isRefreshing.value).isTrue()

            dispatcher.scheduler.advanceUntilIdle()
            assertThat(viewModel.isRefreshing.value).isFalse()
        }

    @Test
    fun `a started scan holds the spinner for the full three seconds`() =
        runTest {
            coEvery { wifiScanRepository.requestScan(isUserInitiated = true) } returns ScanOutcome.Started
            val viewModel = viewModel()

            viewModel.onRefresh()
            dispatcher.scheduler.runCurrent()

            dispatcher.scheduler.advanceTimeBy(2_999)
            assertThat(viewModel.isRefreshing.value).isTrue()

            dispatcher.scheduler.advanceTimeBy(2)
            assertThat(viewModel.isRefreshing.value).isFalse()
        }
}
