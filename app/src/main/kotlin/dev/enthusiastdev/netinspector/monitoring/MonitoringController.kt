package dev.enthusiastdev.netinspector.monitoring

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber
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

        /** Returns false if the OS refused the start (background-start limits, or a
         * foreground-service permission auto-revoked since install) rather than letting the
         * exception reach the caller. [isRunning] stays the source of truth for whether it
         * actually came up. */
        fun start(): Boolean =
            try {
                ContextCompat.startForegroundService(context, Intent(context, MonitoringService::class.java))
                true
            } catch (e: IllegalStateException) {
                Timber.w(e, "Could not start monitoring service")
                false
            } catch (e: SecurityException) {
                Timber.w(e, "Missing permission to start monitoring service")
                false
            }

        fun stop() {
            val intent = Intent(context, MonitoringService::class.java).setAction(MonitoringService.ACTION_STOP)
            context.startService(intent)
        }
    }
