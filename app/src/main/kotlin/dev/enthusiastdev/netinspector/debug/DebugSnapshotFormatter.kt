package dev.enthusiastdev.netinspector.debug

import dev.enthusiastdev.netinspector.core.model.connection.ConnectionSnapshot
import dev.enthusiastdev.netinspector.core.model.lan.Host
import dev.enthusiastdev.netinspector.core.model.lan.SweepProgress
import dev.enthusiastdev.netinspector.core.model.lan.primaryHostname
import dev.enthusiastdev.netinspector.core.model.wifi.AccessPoint
import java.time.Instant

/** ideas.md #22 - "bundle recent logs plus current scan/diagnostic state,"
 * deliberately scoped to *live* state (this sweep's hosts, the current connection, the latest
 * Wi-Fi scan) rather than persisted session history - pulling in full history would blur this
 * with idea #20 (PDF/shareable report generation), a separate backlog item. Output is plain
 * text; the caller redacts it before it leaves the device. */
fun formatDebugSnapshot(
    connection: ConnectionSnapshot?,
    hosts: List<Host>,
    sweepProgress: SweepProgress,
    accessPoints: List<AccessPoint>,
    recentDiagnosticRuns: List<DiagnosticRunSummary>,
): String =
    buildString {
        appendConnectionSection(connection)
        appendHostsSection(hosts, sweepProgress)
        appendScanSection(accessPoints)
        appendDiagnosticsSection(recentDiagnosticRuns)
    }

private fun StringBuilder.appendConnectionSection(connection: ConnectionSnapshot?) {
    appendLine("=== Connection ===")
    if (connection == null) {
        appendLine("Not connected to Wi-Fi.")
    } else {
        appendLine("SSID: ${connection.ssid ?: "(unknown)"}")
        appendLine("BSSID: ${connection.bssid ?: "(unknown)"}")
        appendLine("RSSI: ${connection.rssiDbm?.let { "$it dBm" } ?: "(unknown)"}")
        appendLine("Standard: ${connection.standard}")
        appendLine("Gateway: ${connection.gateway ?: "(unknown)"}")
        appendLine("Has internet: ${connection.hasInternet}")
    }
    appendLine()
}

private fun StringBuilder.appendHostsSection(
    hosts: List<Host>,
    sweepProgress: SweepProgress,
) {
    appendLine(
        "=== LAN hosts (${hosts.size}, sweep running=${sweepProgress.isRunning}, " +
            "${sweepProgress.addressesProbed}/${sweepProgress.addressesTotal} probed) ===",
    )
    if (hosts.isEmpty()) {
        appendLine("No hosts discovered yet.")
    } else {
        hosts.forEach { appendLine(it.toDebugLine()) }
    }
    appendLine()
}

private fun Host.toDebugLine(): String {
    val roleTags = listOfNotNull("gateway".takeIf { isGateway }, "self".takeIf { isSelf })
    val role = if (roleTags.isNotEmpty()) " [${roleTags.joinToString(",")}]" else ""
    return "${address.hostAddress} ${primaryHostname ?: "(no hostname)"} confidence=$confidence$role"
}

private fun StringBuilder.appendScanSection(accessPoints: List<AccessPoint>) {
    appendLine("=== Wi-Fi scan (${accessPoints.size} APs) ===")
    if (accessPoints.isEmpty()) {
        appendLine("No scan results.")
    } else {
        accessPoints.forEach {
            appendLine(
                "${it.ssid} (${it.bssid}) rssi=${it.rssiDbm}dBm standard=${it.standard} connected=${it.isConnected}",
            )
        }
    }
    appendLine()
}

private fun StringBuilder.appendDiagnosticsSection(recentDiagnosticRuns: List<DiagnosticRunSummary>) {
    appendLine("=== Recent diagnostics (${recentDiagnosticRuns.size}) ===")
    if (recentDiagnosticRuns.isEmpty()) {
        appendLine("No diagnostic runs recorded.")
    } else {
        recentDiagnosticRuns.forEach {
            appendLine("${Instant.ofEpochMilli(it.timestampMillis)} ${it.toolType} ${it.target}: ${it.summary}")
        }
    }
}
