package dev.enthusiastdev.netinspector.ui.screens.devices

import dev.enthusiastdev.netinspector.core.model.lan.Host
import dev.enthusiastdev.netinspector.core.model.lan.SweepProgress

sealed interface DevicesUiState {
    data object Loading : DevicesUiState

    data class Content(
        val hosts: List<Host>,
        val progress: SweepProgress,
        val isConnected: Boolean,
        val needsAcknowledgement: Boolean,
        /** design §8.2 - non-null while a sweep is refusing to run against a short prefix
         * (a /16 is 65,534 probes) until the user explicitly confirms it. */
        val pendingConfirmationHostCount: Long?,
    ) : DevicesUiState
}
