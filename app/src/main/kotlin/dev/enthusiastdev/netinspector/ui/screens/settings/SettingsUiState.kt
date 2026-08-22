package dev.enthusiastdev.netinspector.ui.screens.settings

import dev.enthusiastdev.netinspector.core.model.diagnostics.PortSelection
import dev.enthusiastdev.netinspector.core.model.settings.RssiDisplayUnit
import dev.enthusiastdev.netinspector.core.model.settings.ThemeMode
import dev.enthusiastdev.netinspector.data.persistence.preferences.AppSettingsRepository
import dev.enthusiastdev.netinspector.data.persistence.preferences.RetentionSettingsRepository
import dev.enthusiastdev.netinspector.ui.screens.connection.NotificationAccessState

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val rssiDisplayUnit: RssiDisplayUnit = RssiDisplayUnit.DBM,
    val scanHistoryRetentionDays: Int = RetentionSettingsRepository.DEFAULT_SCAN_RETENTION_DAYS,
    val diagnosticHistoryRetentionDays: Int = RetentionSettingsRepository.DEFAULT_DIAGNOSTIC_RETENTION_DAYS,
    val defaultPortSelection: PortSelection = PortSelection.Common,
    val monitoringNotificationAccess: NotificationAccessState = NotificationAccessState.PERMISSION_NEEDED,
    val rssiAlertThresholdDbm: Int = AppSettingsRepository.DEFAULT_RSSI_ALERT_THRESHOLD_DBM,
    val alertOnRssiDrop: Boolean = false,
    val alertOnDisconnect: Boolean = false,
    val alertOnReconnect: Boolean = false,
    val monitoringCardDismissed: Boolean = false,
)
