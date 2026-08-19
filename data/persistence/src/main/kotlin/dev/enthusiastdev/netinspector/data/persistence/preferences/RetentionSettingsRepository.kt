package dev.enthusiastdev.netinspector.data.persistence.preferences

import androidx.datastore.core.DataStore
import dev.enthusiastdev.netinspector.data.persistence.proto.AppPreferences
import dev.enthusiastdev.netinspector.data.persistence.proto.copy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** design §10 table / decision #4 - 30-day default for scan history, 90-day for diagnostic
 * runs, both user-adjustable. Read by the periodic retention-cleanup worker in `:app`; written
 * by the settings screen once it exists. */
interface RetentionSettingsRepository {
    val scanHistoryRetentionDays: Flow<Int>
    val diagnosticHistoryRetentionDays: Flow<Int>

    suspend fun setScanHistoryRetentionDays(days: Int)

    suspend fun setDiagnosticHistoryRetentionDays(days: Int)

    companion object {
        const val DEFAULT_SCAN_RETENTION_DAYS = 30
        const val DEFAULT_DIAGNOSTIC_RETENTION_DAYS = 90
    }
}

class DefaultRetentionSettingsRepository
    @Inject
    constructor(
        private val dataStore: DataStore<AppPreferences>,
    ) : RetentionSettingsRepository {
        override val scanHistoryRetentionDays: Flow<Int> =
            dataStore.data.map {
                it.scanHistoryRetentionDays.takeIf { days -> days > 0 }
                    ?: RetentionSettingsRepository.DEFAULT_SCAN_RETENTION_DAYS
            }

        override val diagnosticHistoryRetentionDays: Flow<Int> =
            dataStore.data.map {
                it.diagnosticHistoryRetentionDays.takeIf { days -> days > 0 }
                    ?: RetentionSettingsRepository.DEFAULT_DIAGNOSTIC_RETENTION_DAYS
            }

        override suspend fun setScanHistoryRetentionDays(days: Int) {
            dataStore.updateData { it.copy { scanHistoryRetentionDays = days } }
        }

        override suspend fun setDiagnosticHistoryRetentionDays(days: Int) {
            dataStore.updateData { it.copy { diagnosticHistoryRetentionDays = days } }
        }
    }
