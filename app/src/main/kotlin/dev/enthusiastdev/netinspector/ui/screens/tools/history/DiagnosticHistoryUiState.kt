package dev.enthusiastdev.netinspector.ui.screens.tools.history

import dev.enthusiastdev.netinspector.data.persistence.diagnostics.DiagnosticRunEntity

data class DiagnosticHistoryUiState(
    val runs: List<DiagnosticRunEntity> = emptyList(),
)
