package dev.enthusiastdev.netinspector.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.enthusiastdev.netinspector.data.persistence.diagnostics.DiagnosticRunRepository
import dev.enthusiastdev.netinspector.data.persistence.lan.KnownLanHostRepository
import dev.enthusiastdev.netinspector.data.persistence.preferences.RetentionSettingsRepository
import dev.enthusiastdev.netinspector.data.persistence.scan.ScanHistoryRepository
import kotlinx.coroutines.flow.first
import timber.log.Timber

/** design §8 acceptance / C-10 - "`WorkManager` handles deferrable periodic work only, and its
 * results are timestamped so gaps are visible rather than interpolated." This worker only ever
 * deletes rows past their retention window; it never writes history itself (that happens
 * synchronously from the screens that produce it), so a missed or delayed run just means old
 * rows linger a bit longer - never a gap in what was recorded. */
@HiltWorker
class RetentionCleanupWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val scanHistoryRepository: ScanHistoryRepository,
        private val diagnosticRunRepository: DiagnosticRunRepository,
        private val retentionSettingsRepository: RetentionSettingsRepository,
        private val knownLanHostRepository: KnownLanHostRepository,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val scanRetentionDays = retentionSettingsRepository.scanHistoryRetentionDays.first()
            val diagnosticRetentionDays = retentionSettingsRepository.diagnosticHistoryRetentionDays.first()
            scanHistoryRepository.deleteSessionsOlderThan(scanRetentionDays)
            diagnosticRunRepository.deleteOlderThan(diagnosticRetentionDays)
            // improvement-ideas.md #24 - same window as scan history: both are periodic-sweep
            // byproducts, not something that needs its own retention knob.
            knownLanHostRepository.deleteOlderThan(scanRetentionDays)
            Timber.d(
                "Retention cleanup ran: scan history older than %d days and diagnostic runs older than %d days purged.",
                scanRetentionDays,
                diagnosticRetentionDays,
            )
            return Result.success()
        }

        companion object {
            const val UNIQUE_WORK_NAME = "retention_cleanup"
        }
    }
