package dev.enthusiastdev.netinspector.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.enthusiastdev.netinspector.MainActivity
import dev.enthusiastdev.netinspector.R
import dev.enthusiastdev.netinspector.core.model.lan.KnownHostRecord
import dev.enthusiastdev.netinspector.core.model.lan.LanPresenceDiff
import javax.inject.Inject

/** ideas.md #24 - one consolidated notification per periodic sweep rather than one
 * per host, same "distinct event, dismissible, alerts every time" framing
 * `MonitoringService`'s alert notification already uses for connection alerts. */
class LanChangeNotifier
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        /** No-op when [diff] has nothing alert-worthy - known-device-flagged hosts are already
         * excluded from [LanPresenceDiff.vanishedHosts]/[LanPresenceDiff.reappearedHosts] by
         * `diffLanPresence` itself, so this doesn't need to re-check that. */
        fun notify(diff: LanPresenceDiff) {
            val text = messageFor(diff) ?: return
            createChannelIfNeeded()
            val contentIntent =
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE,
                )
            val notification =
                NotificationCompat
                    .Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification_monitoring)
                    .setContentTitle("NetInspector")
                    .setContentText(text)
                    .setAutoCancel(true)
                    .setContentIntent(contentIntent)
                    .build()
            notificationManager.notify(NOTIFICATION_ID, notification)
        }

        private fun createChannelIfNeeded() {
            val channel =
                NotificationChannel(CHANNEL_ID, "Device alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "New, vanished, and reappeared LAN device alerts from background scanning"
                }
            notificationManager.createNotificationChannel(channel)
        }

        private val notificationManager: NotificationManager
            get() = requireNotNull(context.getSystemService(NotificationManager::class.java))

        companion object {
            const val CHANNEL_ID = "lan_host_alerts"
            private const val NOTIFICATION_ID = 1003
        }
    }

/** Pure and package-visible so it's unit-testable without a `Context`. `null` means nothing
 * alert-worthy happened - the caller skips posting entirely rather than notifying "nothing
 * changed." */
internal fun messageFor(diff: LanPresenceDiff): String? {
    val segments =
        listOfNotNull(
            describe(diff.newHosts, "joined the network"),
            describe(diff.vanishedHosts, "vanished"),
            describe(diff.reappearedHosts, "reappeared"),
        )
    return segments.takeIf { it.isNotEmpty() }?.joinToString("; ")
}

private fun describe(
    records: List<KnownHostRecord>,
    verb: String,
): String? =
    when (records.size) {
        0 -> null
        1 -> "${records.single().displayName?.takeIf { it.isNotBlank() } ?: records.single().key} $verb"
        else -> "${records.size} devices $verb"
    }
