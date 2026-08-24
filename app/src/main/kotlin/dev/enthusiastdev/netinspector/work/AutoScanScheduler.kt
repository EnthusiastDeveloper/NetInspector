package dev.enthusiastdev.netinspector.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** improvement-ideas.md #23 - thin `WorkManager` wrapper for [PeriodicScanWorker], called from
 * `SettingsViewModel` whenever the auto-scan toggle or interval changes (unlike
 * `RetentionCleanupWorker`'s unconditional every-app-start schedule in
 * `NetInspectorApplication` - this is an opt-in, off-by-default feature, and `WorkManager`'s
 * own database persists an enqueued periodic job across process restarts, so nothing needs to
 * re-enqueue it on every launch). */
@Singleton
class AutoScanScheduler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        /** [ExistingPeriodicWorkPolicy.UPDATE] so an interval change reschedules the existing
         * job (with its next run time recalculated from now) rather than being ignored the way
         * `KEEP` would. */
        fun enqueue(intervalMinutes: Int) {
            val request =
                PeriodicWorkRequestBuilder<PeriodicScanWorker>(intervalMinutes.toLong(), TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun cancel() {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }

        companion object {
            const val UNIQUE_WORK_NAME = "periodic_scan"
        }
    }
