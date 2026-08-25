package dev.enthusiastdev.netinspector.ui.screens.settings

import android.content.Context
import dev.enthusiastdev.netinspector.data.persistence.preferences.AppSettingsRepository
import dev.enthusiastdev.netinspector.data.persistence.preferences.AutoScanSettingsRepository
import dev.enthusiastdev.netinspector.data.persistence.preferences.RetentionSettingsRepository
import dev.enthusiastdev.netinspector.debug.CrashReportStore
import dev.enthusiastdev.netinspector.debug.DebugBundleBuilder
import dev.enthusiastdev.netinspector.work.AutoScanScheduler
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/** improvement-ideas.md #36 - `setUiFontScale`'s clamp is the boundary that matters here: a
 * value outside `[MIN_UI_FONT_SCALE, MAX_UI_FONT_SCALE]` reaching the repository would flow
 * straight into MainActivity's app-root `CompositionLocalProvider` and rescale (or illegibly
 * shrink) every screen at once, with no per-screen safeguard to catch it. */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val appSettingsRepository = mockk<AppSettingsRepository>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(): SettingsViewModel =
        SettingsViewModel(
            appSettingsRepository = appSettingsRepository,
            retentionSettingsRepository = mockk<RetentionSettingsRepository>(relaxed = true),
            autoScanSettingsRepository = mockk<AutoScanSettingsRepository>(relaxed = true),
            autoScanScheduler = mockk<AutoScanScheduler>(relaxed = true),
            crashReportStore = mockk<CrashReportStore>(relaxed = true),
            debugBundleBuilder = mockk<DebugBundleBuilder>(relaxed = true),
            context = mockk<Context>(relaxed = true),
        )

    @Test
    fun `a scale within range is persisted unchanged`() =
        runTest {
            viewModel().setUiFontScale(1.1f)
            dispatcher.scheduler.runCurrent()

            coVerify { appSettingsRepository.setUiFontScale(1.1f) }
        }

    @Test
    fun `a scale below the minimum clamps up to the minimum`() =
        runTest {
            viewModel().setUiFontScale(0.5f)
            dispatcher.scheduler.runCurrent()

            coVerify { appSettingsRepository.setUiFontScale(AppSettingsRepository.MIN_UI_FONT_SCALE) }
        }

    @Test
    fun `a scale above the maximum clamps down to the maximum`() =
        runTest {
            viewModel().setUiFontScale(2.5f)
            dispatcher.scheduler.runCurrent()

            coVerify { appSettingsRepository.setUiFontScale(AppSettingsRepository.MAX_UI_FONT_SCALE) }
        }
}
