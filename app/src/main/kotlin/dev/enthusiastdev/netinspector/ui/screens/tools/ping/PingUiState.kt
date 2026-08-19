package dev.enthusiastdev.netinspector.ui.screens.tools.ping

import dev.enthusiastdev.netinspector.core.common.icmp.PingSummary
import dev.enthusiastdev.netinspector.core.model.diagnostics.PingProbeResult

data class PingUiState(
    val target: String = "",
    val isRunning: Boolean = false,
    val results: List<PingProbeResult> = emptyList(),
    val summary: PingSummary? = null,
    val errorMessage: String? = null,
)
