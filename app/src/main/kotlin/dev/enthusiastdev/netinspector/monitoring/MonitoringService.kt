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
import dev.enthusiastdev.netinspector.data.wifi.ConnectionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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

    private val serviceScope = CoroutineScope(SupervisorJob())
    private var collectJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
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
        startForegroundWithPlaceholder()
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

    private fun observeConnection() {
        collectJob?.cancel()
        collectJob =
            connectionRepository.connectionSnapshot
                .onEach { snapshot -> notificationManager.notify(NOTIFICATION_ID, buildNotification(snapshot)) }
                .launchIn(serviceScope)
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

    private fun createNotificationChannel() {
        val channel =
            NotificationChannel(CHANNEL_ID, "Connection monitoring", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Persistent notification while continuous Wi-Fi monitoring is running"
            }
        notificationManager.createNotificationChannel(channel)
    }

    private val notificationManager: NotificationManager
        get() = requireNotNull(getSystemService(NotificationManager::class.java))

    companion object {
        private const val CHANNEL_ID = "monitoring"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "dev.enthusiastdev.netinspector.action.STOP_MONITORING"

        private val _isRunning = MutableStateFlow(false)

        /** design §8 - "explicit user start/stop": the UI's only source of truth for whether
         * the service is currently running, since a bound-service query would need a live
         * binding just to answer a yes/no question. */
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    }
}
