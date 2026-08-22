package dev.enthusiastdev.netinspector.monitoring

import dev.enthusiastdev.netinspector.core.model.connection.ConnectionSnapshot

/** improvement-ideas.md #5 - the alert toggles + threshold read from `AppSettingsRepository`,
 * bundled together since [connectionAlertsFor] needs all four to decide whether a
 * previous -> current transition warrants an alert. */
data class ConnectionAlertSettings(
    val rssiAlertThresholdDbm: Int,
    val alertOnRssiDrop: Boolean,
    val alertOnDisconnect: Boolean,
    val alertOnReconnect: Boolean,
)

/** A user-facing alert distinct from the ongoing status notification `MonitoringService`
 * otherwise keeps silently re-posting on every reading (`setOnlyAlertOnce(true)`). */
sealed interface ConnectionAlert {
    val message: String

    data class Disconnected(
        val previousSsid: String?,
    ) : ConnectionAlert {
        override val message: String get() = "Disconnected from ${previousSsid ?: "Wi-Fi"}"
    }

    data class Reconnected(
        val ssid: String?,
    ) : ConnectionAlert {
        override val message: String get() = "Reconnected to ${ssid ?: "Wi-Fi"}"
    }

    data class WeakSignal(
        val ssid: String?,
        val rssiDbm: Int,
    ) : ConnectionAlert {
        override val message: String get() = "Weak signal on ${ssid ?: "Wi-Fi"}: $rssiDbm dBm"
    }
}

/** improvement-ideas.md #5's pure decision logic, kept separate from `MonitoringService` so
 * it's unit-testable without Robolectric/an Android runtime. Only fires on the transition into
 * a state (disconnect, reconnect, or crossing below the threshold) rather than on every
 * subsequent reading while already in that state, since a service that re-alerted on every
 * emission while a weak signal persisted would be spammy rather than useful. */
fun connectionAlertsFor(
    previous: ConnectionSnapshot?,
    current: ConnectionSnapshot?,
    settings: ConnectionAlertSettings,
): List<ConnectionAlert> {
    val alerts = mutableListOf<ConnectionAlert>()
    if (settings.alertOnDisconnect && previous != null && current == null) {
        alerts += ConnectionAlert.Disconnected(previous.ssid)
    }
    if (settings.alertOnReconnect && previous == null && current != null) {
        alerts += ConnectionAlert.Reconnected(current.ssid)
    }
    val currentRssi = current?.rssiDbm
    if (settings.alertOnRssiDrop && currentRssi != null) {
        val wasAboveThreshold = (previous?.rssiDbm ?: Int.MAX_VALUE) >= settings.rssiAlertThresholdDbm
        val isBelowThreshold = currentRssi < settings.rssiAlertThresholdDbm
        if (wasAboveThreshold && isBelowThreshold) {
            alerts += ConnectionAlert.WeakSignal(current.ssid, currentRssi)
        }
    }
    return alerts
}
