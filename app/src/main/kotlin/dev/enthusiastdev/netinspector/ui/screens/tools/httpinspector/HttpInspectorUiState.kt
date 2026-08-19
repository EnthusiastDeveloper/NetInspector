package dev.enthusiastdev.netinspector.ui.screens.tools.httpinspector

import dev.enthusiastdev.netinspector.core.model.diagnostics.HttpInspectionOutcome

data class HttpInspectorUiState(
    val url: String = "",
    val isRunning: Boolean = false,
    val outcome: HttpInspectionOutcome? = null,
)
