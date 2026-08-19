package dev.enthusiastdev.netinspector.ui.screens.tools.whois

import dev.enthusiastdev.netinspector.core.model.diagnostics.WhoisOutcome

data class WhoisUiState(
    val target: String = "",
    val isRunning: Boolean = false,
    val outcome: WhoisOutcome? = null,
)
