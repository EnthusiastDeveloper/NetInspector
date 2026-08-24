package dev.enthusiastdev.netinspector.ui.screens.tools.wifichanges

import dev.enthusiastdev.netinspector.core.model.wifi.ScanSessionDiff
import dev.enthusiastdev.netinspector.data.persistence.scan.ScanSessionEntity

data class WifiChangesUiState(
    val recentSessions: List<ScanSessionEntity> = emptyList(),
    val before: ScanSessionEntity? = null,
    val after: ScanSessionEntity? = null,
    /** `null` until both [before] and [after] are picked. */
    val diff: ScanSessionDiff? = null,
)
