package dev.enthusiastdev.netinspector.ui.screens.dashboard

import dev.enthusiastdev.netinspector.core.model.connection.ConnectionSnapshot
import dev.enthusiastdev.netinspector.core.model.lan.SweepProgress
import dev.enthusiastdev.netinspector.core.model.settings.RssiDisplayUnit

sealed interface DashboardUiState {
    data object Loading : DashboardUiState

    /** [connection] is null while disconnected - the dashboard shows that state inline rather
     * than the Connection tab's dedicated "not connected" screen, since a glance here should
     * never look broken just because Wi-Fi happens to be off right now. */
    data class Content(
        val connection: ConnectionSnapshot?,
        val rssiDisplayUnit: RssiDisplayUnit,
        val hostCount: Int,
        val sweepProgress: SweepProgress,
        val isMonitoringActive: Boolean,
        // improvement-ideas.md #21 - non-null when a crash report exists that the user hasn't
        // yet exported or dismissed (see DashboardViewModel). Null hides the prompt entirely.
        val pendingCrashReportFilename: String? = null,
    ) : DashboardUiState
}
