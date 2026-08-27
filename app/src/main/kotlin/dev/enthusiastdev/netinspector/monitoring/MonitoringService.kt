package dev.enthusiastdev.netinspector.monitoring

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import dev.enthusiastdev.netinspector.MainActivity
import dev.enthusiastdev.netinspector.R
import dev.enthusiastdev.netinspector.core.common.wifi.rssiToQualityPercent
import dev.enthusiastdev.netinspector.core.model.connection.ConnectionSnapshot
import dev.enthusiastdev.netinspector.data.persistence.preferences.AppSettingsRepository
import dev.enthusiastdev.netinspector.data.wifi.ConnectionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** design §8 / C-09 / C-10 - the one optional, explicitly user-started continuous-monitoring
 * feature: a persistent notification tracking the live RSSI stream (design §5.1's
 * [ConnectionRepository], the same flow the dashboard gauge reads) for as long as the user
 * keeps it running. `connectedDevice` type + `FOREGROUND_SERVICE_CONNECTED_DEVICE` is
 * satisfied by `CHANGE_WIFI_STATE`, already held (C-09). This is deliberately *not* a general
 * background-scanning service - it doesn't call `requestScan()` or run any diagnostic; Doze and
 * the scan throttle (C-10) are irrelevant to a service that only reads what
 * `ConnectivityManager.NetworkCallback` already delivers for free. */
@AndroidEntryPoint
class MonitoringService : Service() {
    @Inject
    lateinit var connectionRepository: ConnectionRepository

    @Inject
    lateinit var appSettingsRepository: AppSettingsRepository

    private val serviceScope = CoroutineScope(SupervisorJob())
    private var collectJob: Job? = null

    /** ideas.md #5 - the previous emission, so [connectionAlertsFor] can tell a
     * fresh disconnect/reconnect/threshold-crossing apart from an already-settled state. Reset
     * implicitly every service (re)start, same lifetime as [collectJob]. */
    private var previousSnapshot: ConnectionSnapshot? = null

    /** The very first emission after a service (re)start only establishes [previousSnapshot]
     * as a baseline - it must not be evaluated as a transition, or starting monitoring while
     * already connected would immediately misfire a "Reconnected" alert (there was no real
     * prior disconnect to reconnect from). */
    private var hasBaselineSnapshot = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            startForegroundWithPlaceholder()
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException (an IllegalStateException): an OEM or a
            // newer platform decided this start was not from an allowed state. Nothing to
            // recover here - stop quietly rather than crash. isRunning never flips, so the
            // toggle simply stays off.
            Timber.w(e, "Foreground start refused, monitoring not started")
            stopSelf()
            return START_NOT_STICKY
        } catch (e: SecurityException) {
            // A foreground-service permission was revoked after install (Android can auto-reset
            // unused permissions). Same graceful bail-out.
            Timber.w(e, "Missing foreground-service permission, monitoring not started")
            stopSelf()
            return START_NOT_STICKY
        }
        observeConnection()
        _isRunning.value = true
        return START_STICKY
    }

    override fun onDestroy() {
        collectJob?.cancel()
        serviceScope.cancel()
        _isRunning.value = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundWithPlaceholder() {
        val notification = buildNotification(snapshot = null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    @OptIn(FlowPreview::class)
    private fun observeConnection() {
        collectJob?.cancel()
        previousSnapshot = null
        hasBaselineSnapshot = false
        val alertSettings =
            combine(
                appSettingsRepository.rssiAlertThresholdDbm,
                appSettingsRepository.alertOnRssiDrop,
                appSettingsRepository.alertOnDisconnect,
                appSettingsRepository.alertOnReconnect,
            ) { thresholdDbm, onRssiDrop, onDisconnect, onReconnect ->
                ConnectionAlertSettings(thresholdDbm, onRssiDrop, onDisconnect, onReconnect)
            }
        val snapshots = connectionRepository.connectionSnapshot
        collectJob =
            serviceScope.launch {
                launch {
                    snapshots.collect { snapshot ->
                        notificationManager.notify(NOTIFICATION_ID, buildNotification(snapshot))
                    }
                }
                // ConnectivityManager.NetworkCallback delivers capabilities and link properties
                // separately: a fresh registration while already connected emits a transient
                // null (capabilities arrived, link properties haven't yet) immediately followed
                // by the real snapshot. Debounced here so that startup churn settles into one
                // reading before it's compared for a disconnect/reconnect/threshold transition -
                // undebounced, every service start would misfire a spurious "Reconnected" alert.
                combine(snapshots.debounce(DEBOUNCE_MILLIS), alertSettings) { snapshot, settings ->
                    snapshot to settings
                }.collect { (snapshot, settings) ->
                    if (hasBaselineSnapshot) {
                        postAlerts(connectionAlertsFor(previousSnapshot, snapshot, settings))
                    }
                    previousSnapshot = snapshot
                    hasBaselineSnapshot = true
                }
            }
    }

    private fun postAlerts(alerts: List<ConnectionAlert>) {
        alerts.forEach { alert -> notificationManager.notify(NOTIFICATION_ID_ALERT, buildAlertNotification(alert)) }
    }

    private fun buildNotification(snapshot: ConnectionSnapshot?): Notification {
        val rssiDbm = snapshot?.rssiDbm
        val contentText =
            when {
                snapshot == null -> "Not connected to Wi-Fi"
                rssiDbm == null -> snapshot.ssid ?: "Connected"
                else -> {
                    val quality = rssiToQualityPercent(rssiDbm)
                    "${snapshot.ssid ?: "Connected"} · $rssiDbm dBm ($quality%)"
                }
            }
        val contentIntent =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )
        val stopIntent =
            PendingIntent.getService(
                this,
                0,
                Intent(this, MonitoringService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE,
            )
        return NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_monitoring)
            .setContentTitle("NetInspector monitoring")
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .addAction(0, "Stop", stopIntent)
            .build()
    }

    /** ideas.md #5 - a disconnect/reconnect/weak-signal alert, distinct from
     * [buildNotification]'s ongoing status notification: dismissible rather than
     * [NotificationCompat.Builder.setOngoing], and alerts every time rather than
     * [NotificationCompat.Builder.setOnlyAlertOnce], since each one represents a discrete event
     * rather than a continuously-updating state. */
    private fun buildAlertNotification(alert: ConnectionAlert): Notification {
        val contentIntent =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )
        return NotificationCompat
            .Builder(this, CHANNEL_ID_ALERTS)
            .setSmallIcon(R.drawable.ic_notification_monitoring)
            .setContentTitle("NetInspector")
            .setContentText(alert.message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
    }

    private fun createNotificationChannels() {
        val statusChannel =
            NotificationChannel(CHANNEL_ID, "Connection monitoring", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Persistent notification while continuous Wi-Fi monitoring is running"
            }
        val alertChannel =
            NotificationChannel(
                CHANNEL_ID_ALERTS,
                "Connection alerts",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Disconnect, reconnect, and weak-signal alerts while monitoring is running"
            }
        notificationManager.createNotificationChannels(listOf(statusChannel, alertChannel))
    }

    private val notificationManager: NotificationManager
        get() = requireNotNull(getSystemService(NotificationManager::class.java))

    companion object {
        private const val CHANNEL_ID = "monitoring"
        private const val CHANNEL_ID_ALERTS = "monitoring_alerts"
        private const val NOTIFICATION_ID = 1001
        private const val NOTIFICATION_ID_ALERT = 1002
        private const val DEBOUNCE_MILLIS = 750L
        const val ACTION_STOP = "dev.enthusiastdev.netinspector.action.STOP_MONITORING"

        private val _isRunning = MutableStateFlow(false)

        /** design §8 - "explicit user start/stop": the UI's only source of truth for whether
         * the service is currently running, since a bound-service query would need a live
         * binding just to answer a yes/no question. */
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    }
}
