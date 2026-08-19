package dev.enthusiastdev.netinspector.ui.screens.tools.traceroute

import dev.enthusiastdev.netinspector.core.model.diagnostics.TracerouteHop
import dev.enthusiastdev.netinspector.core.model.diagnostics.TracerouteTier

data class TracerouteUiState(
    val target: String = "",
    val isRunning: Boolean = false,
    val hops: List<TracerouteHop> = emptyList(),
    /** design §11.3 - "degraded modes are named": the fallback binary tier is called out in the
     * header once a probe using it completes, rather than only being visible per-row. */
    val tier: TracerouteTier? = null,
    val errorMessage: String? = null,
)
