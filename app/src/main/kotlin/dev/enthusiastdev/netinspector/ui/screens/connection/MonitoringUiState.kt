package dev.enthusiastdev.netinspector.ui.screens.connection

/** design §8 - the optional continuous-monitoring foreground service's UI state, independent
 * of [ConnectionUiState] (monitoring keeps running across Wi-Fi connect/disconnect, so it isn't
 * naturally nested inside "the current connection" - and its control stays on screen even when
 * this device isn't connected to anything). */
enum class NotificationAccessState { GRANTED, PERMISSION_NEEDED }

data class MonitoringUiState(
    val isRunning: Boolean,
    val notificationAccess: NotificationAccessState,
)
