package dev.enthusiastdev.netinspector.ui.screens.tools.history

import dev.enthusiastdev.netinspector.data.persistence.scan.KnownApEntity

data class ScanHistoryUiState(
    val knownAps: List<KnownApEntity> = emptyList(),
)
