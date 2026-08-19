package dev.enthusiastdev.netinspector

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import dev.enthusiastdev.netinspector.work.RetentionCleanupWorker
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class NetInspectorApplication :
    Application(),
    Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        Timber.plant(if (BuildConfig.DEBUG) Timber.DebugTree() else ReleaseTree())
        // The default `androidx.startup`-driven WorkManagerInitializer (disabled in the
        // manifest) runs as a ContentProvider *before* `Application.onCreate()` - before Hilt
        // has field-injected `workerFactory` below, so reading `workManagerConfiguration` at
        // that point would hit an uninitialized `lateinit`. Initializing manually here, after
        // `super.onCreate()` has run Hilt's injection, is WorkManager's documented fix for
        // exactly this ordering problem with `HiltWorkerFactory`.
        WorkManager.initialize(this, workManagerConfiguration)
        scheduleRetentionCleanup()
    }

    /** design §8 - "retention policies with a periodic WorkManager cleanup." Daily cadence is
     * arbitrary but deliberately far looser than the 15-minute WorkManager minimum: this is
     * deferrable janitorial work (C-10), not anything time-sensitive. `KEEP` so re-scheduling on
     * every process start doesn't reset an already-pending job's next-run time. */
    private fun scheduleRetentionCleanup() {
        val request =
            PeriodicWorkRequestBuilder<RetentionCleanupWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            RetentionCleanupWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
