package dev.enthusiastdev.netinspector.ui.screens.tools.portscanner

import dev.enthusiastdev.netinspector.core.model.diagnostics.PortScanFinding
import dev.enthusiastdev.netinspector.core.model.diagnostics.PortScanProgress
import dev.enthusiastdev.netinspector.core.model.diagnostics.PortSelection

data class PortScannerUiState(
    val target: String = "",
    val selection: PortSelection = PortSelection.Common,
    val customStart: String = "1",
    val customEnd: String = "1024",
    val isRunning: Boolean = false,
    val progress: PortScanProgress? = null,
    val findings: List<PortScanFinding> = emptyList(),
    val errorMessage: String? = null,
)
