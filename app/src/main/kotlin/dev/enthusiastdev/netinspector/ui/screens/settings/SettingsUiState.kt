package dev.enthusiastdev.netinspector.ui.screens.settings

import dev.enthusiastdev.netinspector.core.model.diagnostics.PortSelection
import dev.enthusiastdev.netinspector.core.model.settings.RssiDisplayUnit
import dev.enthusiastdev.netinspector.core.model.settings.ThemeMode
import dev.enthusiastdev.netinspector.data.persistence.preferences.RetentionSettingsRepository

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val rssiDisplayUnit: RssiDisplayUnit = RssiDisplayUnit.DBM,
    val scanHistoryRetentionDays: Int = RetentionSettingsRepository.DEFAULT_SCAN_RETENTION_DAYS,
    val diagnosticHistoryRetentionDays: Int = RetentionSettingsRepository.DEFAULT_DIAGNOSTIC_RETENTION_DAYS,
    val defaultPortSelection: PortSelection = PortSelection.Common,
)
