package dev.enthusiastdev.netinspector.monitoring

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Thin wrapper the UI layer calls into rather than building [MonitoringService] intents
 * itself - keeps `ContextCompat.startForegroundService` vs. plain `startService` (stop doesn't
 * need the foreground variant) as an implementation detail here, not duplicated at every call
 * site. */
class MonitoringController
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        val isRunning: StateFlow<Boolean> = MonitoringService.isRunning

        fun start() {
            ContextCompat.startForegroundService(context, Intent(context, MonitoringService::class.java))
        }

        fun stop() {
            val intent = Intent(context, MonitoringService::class.java).setAction(MonitoringService.ACTION_STOP)
            context.startService(intent)
        }
    }
