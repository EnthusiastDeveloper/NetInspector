package dev.enthusiastdev.netinspector.ui.screens.dashboard

import dev.enthusiastdev.netinspector.core.model.lan.SweepProgress

/** Whatever the app is actively doing right now, collapsed to one glanceable state - the
 * dashboard's "active diagnostics" summary (design idea #14) rather than a raw progress bar and
 * a raw boolean side by side. */
internal enum class DiagnosticsStatus { IDLE, SCANNING, MONITORING, SCANNING_AND_MONITORING }

internal fun diagnosticsStatus(
    sweepProgress: SweepProgress,
    isMonitoringActive: Boolean,
): DiagnosticsStatus =
    when {
        sweepProgress.isRunning && isMonitoringActive -> DiagnosticsStatus.SCANNING_AND_MONITORING
        sweepProgress.isRunning -> DiagnosticsStatus.SCANNING
        isMonitoringActive -> DiagnosticsStatus.MONITORING
        else -> DiagnosticsStatus.IDLE
    }

internal fun DiagnosticsStatus.label(): String =
    when (this) {
        DiagnosticsStatus.IDLE -> "No active diagnostics"
        DiagnosticsStatus.SCANNING -> "Scanning the network…"
        DiagnosticsStatus.MONITORING -> "Connection monitoring active"
        DiagnosticsStatus.SCANNING_AND_MONITORING -> "Scanning the network - monitoring active"
    }

internal fun hostCountLabel(hostCount: Int): String =
    when (hostCount) {
        0 -> "No devices discovered yet"
        1 -> "1 device on this network"
        else -> "$hostCount devices on this network"
    }
