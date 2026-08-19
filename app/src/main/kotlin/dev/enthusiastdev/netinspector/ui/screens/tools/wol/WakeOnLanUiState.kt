package dev.enthusiastdev.netinspector.ui.screens.tools.wol

import dev.enthusiastdev.netinspector.data.persistence.wol.SavedWolTarget

data class WakeOnLanUiState(
    val label: String = "",
    val mac: String = "",
    val broadcastAddress: String = "255.255.255.255",
    val savedTargets: List<SavedWolTarget> = emptyList(),
    val lastResultMessage: String? = null,
)
