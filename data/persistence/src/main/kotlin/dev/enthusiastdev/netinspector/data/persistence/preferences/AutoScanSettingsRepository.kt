package dev.enthusiastdev.netinspector.data.persistence.preferences

import androidx.datastore.core.DataStore
import dev.enthusiastdev.netinspector.data.persistence.proto.AppPreferences
import dev.enthusiastdev.netinspector.data.persistence.proto.copy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** improvement-ideas.md #23/#24 - read by `PeriodicScanWorker` (`:app`) as well as the
 * Settings screen, same reason [RetentionSettingsRepository] is its own focused repository
 * rather than folded into [AppSettingsRepository]. */
interface AutoScanSettingsRepository {
    val autoScanEnabled: Flow<Boolean>
    val autoScanIntervalMinutes: Flow<Int>
    val alertOnLanHostChanges: Flow<Boolean>

    suspend fun setAutoScanEnabled(enabled: Boolean)

    suspend fun setAutoScanIntervalMinutes(minutes: Int)

    suspend fun setAlertOnLanHostChanges(enabled: Boolean)

    companion object {
        const val DEFAULT_INTERVAL_MINUTES = 60

        /** 15 minutes is WorkManager's own periodic-work floor (`PeriodicWorkRequest` silently
         * clamps anything shorter to it) - offered as the fastest option rather than a smaller
         * number that would be honest-looking but not actually honest about the real cadence.
         * The Settings screen's battery/Doze disclaimer is what speaks to the cost of choosing
         * it, not withholding the option. [snapToAllowedInterval] picks the closest of these to
         * whatever the UI passes in. */
        val ALLOWED_INTERVAL_MINUTES = listOf(15, 30, 60, 180, 360, 720, 1440)

        fun snapToAllowedInterval(minutes: Int): Int = ALLOWED_INTERVAL_MINUTES.minBy { kotlin.math.abs(it - minutes) }
    }
}

class DefaultAutoScanSettingsRepository
    @Inject
    constructor(
        private val dataStore: DataStore<AppPreferences>,
    ) : AutoScanSettingsRepository {
        override val autoScanEnabled: Flow<Boolean> = dataStore.data.map { it.autoScanEnabled }

        override val autoScanIntervalMinutes: Flow<Int> =
            dataStore.data.map {
                it.autoScanIntervalMinutes.takeIf { minutes -> minutes > 0 }
                    ?: AutoScanSettingsRepository.DEFAULT_INTERVAL_MINUTES
            }

        override val alertOnLanHostChanges: Flow<Boolean> = dataStore.data.map { it.alertOnLanHostChanges }

        override suspend fun setAutoScanEnabled(enabled: Boolean) {
            dataStore.updateData { it.copy { autoScanEnabled = enabled } }
        }

        override suspend fun setAutoScanIntervalMinutes(minutes: Int) {
            dataStore.updateData {
                it.copy { autoScanIntervalMinutes = AutoScanSettingsRepository.snapToAllowedInterval(minutes) }
            }
        }

        override suspend fun setAlertOnLanHostChanges(enabled: Boolean) {
            dataStore.updateData { it.copy { alertOnLanHostChanges = enabled } }
        }
    }
