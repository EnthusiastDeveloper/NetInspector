package dev.enthusiastdev.netinspector.ui.screens.tools.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.enthusiastdev.netinspector.core.designsystem.component.InfoCard
import dev.enthusiastdev.netinspector.core.designsystem.component.InfoRow
import dev.enthusiastdev.netinspector.data.persistence.diagnostics.DiagnosticRunEntity
import dev.enthusiastdev.netinspector.history.DiagnosticToolType
import dev.enthusiastdev.netinspector.history.DnsRunPayload
import dev.enthusiastdev.netinspector.history.HttpInspectionRunPayload
import dev.enthusiastdev.netinspector.history.PingRunPayload
import dev.enthusiastdev.netinspector.history.PortScanRunPayload
import dev.enthusiastdev.netinspector.history.TracerouteRunPayload
import dev.enthusiastdev.netinspector.history.WhoisRunPayload
import dev.enthusiastdev.netinspector.history.diagnosticHistoryJson
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.decodeFromString

@Composable
internal fun DiagnosticRunDetailPane(run: DiagnosticRunEntity) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(text = run.target, style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Monospace)
        }
        item {
            InfoCard(title = "Run") {
                InfoRow("Tool", run.toolType.toolLabel())
                InfoRow("When", run.timestampMillis.asRelativeTime())
                InfoRow("Duration", "${run.durationMillis} ms")
                InfoRow("Summary", run.summary)
            }
        }
        val parameters = runCatching { run.parametersJson.decodeParameters() }.getOrDefault(emptyMap())
        if (parameters.isNotEmpty()) {
            item {
                InfoCard(title = "Parameters") {
                    parameters.forEach { (key, value) -> InfoRow(key, value) }
                }
            }
        }
        item { ToolResultSection(run) }
    }
}

private fun String.decodeParameters(): Map<String, String> =
    diagnosticHistoryJson.decodeFromString(MapSerializer(String.serializer(), String.serializer()), this)

private fun String.toolLabel(): String =
    when (runCatching { DiagnosticToolType.valueOf(this) }.getOrNull()) {
        DiagnosticToolType.PING -> "Ping"
        DiagnosticToolType.TRACEROUTE -> "Traceroute"
        DiagnosticToolType.PORT_SCAN -> "Port scanner"
        DiagnosticToolType.DNS_LOOKUP -> "DNS lookup"
        DiagnosticToolType.HTTP_INSPECTOR -> "HTTP headers"
        DiagnosticToolType.WHOIS -> "WHOIS"
        DiagnosticToolType.LAN_THROUGHPUT -> "LAN throughput test"
        null -> this
    }

@Composable
private fun ToolResultSection(run: DiagnosticRunEntity) {
    when (runCatching { DiagnosticToolType.valueOf(run.toolType) }.getOrNull()) {
        DiagnosticToolType.PING -> PingResultCard(run.resultJson)
        DiagnosticToolType.TRACEROUTE -> TracerouteResultCard(run.resultJson)
        DiagnosticToolType.PORT_SCAN -> PortScanResultCard(run.resultJson)
        DiagnosticToolType.DNS_LOOKUP -> DnsResultCard(run.resultJson)
        DiagnosticToolType.HTTP_INSPECTOR -> HttpInspectorResultCard(run.resultJson)
        DiagnosticToolType.WHOIS -> WhoisResultCard(run.resultJson)
        DiagnosticToolType.LAN_THROUGHPUT -> ThroughputResultCard(run.resultJson)
        null -> {}
    }
}

@Composable
private fun PingResultCard(resultJson: String) {
    val payload =
        runCatching { diagnosticHistoryJson.decodeFromString<PingRunPayload>(resultJson) }.getOrNull() ?: return
    val summary = payload.summary
    InfoCard(title = "Result") {
        InfoRow("Sent / received", "${summary.sent} / ${summary.received}")
        InfoRow("Loss", "%.0f%%".format(summary.lossPercent))
        summary.avgMs?.let { InfoRow("Avg RTT", "%.1f ms".format(it)) }
        summary.minMs?.let { InfoRow("Min RTT", "%.1f ms".format(it)) }
        summary.maxMs?.let { InfoRow("Max RTT", "%.1f ms".format(it)) }
        summary.jitterMs?.let { InfoRow("Jitter", "%.1f ms".format(it)) }
    }
}

@Composable
private fun TracerouteResultCard(resultJson: String) {
    val payload =
        runCatching { diagnosticHistoryJson.decodeFromString<TracerouteRunPayload>(resultJson) }.getOrNull() ?: return
    InfoCard(title = "Hops") {
        payload.hops.forEach { hop ->
            val bestReply = hop.probes.firstOrNull { it.kind == "REPLY" }
            val label = hop.hostname ?: bestReply?.fromAddress ?: "* * *"
            val rtt = bestReply?.rttMs?.let { "%.1f ms".format(it) } ?: "no reply"
            InfoRow("Hop ${hop.ttl}", "$label - $rtt")
        }
    }
}

@Composable
private fun PortScanResultCard(resultJson: String) {
    val payload =
        runCatching { diagnosticHistoryJson.decodeFromString<PortScanRunPayload>(resultJson) }.getOrNull() ?: return
    InfoCard(title = "Findings (${payload.findings.size} open of ${payload.portsScanned} scanned)") {
        if (payload.findings.isEmpty()) {
            Text("No open ports found")
        } else {
            payload.findings.forEach { finding -> InfoRow("Port ${finding.port}", finding.banner ?: "open") }
        }
    }
}

@Composable
private fun DnsResultCard(resultJson: String) {
    val payload =
        runCatching { diagnosticHistoryJson.decodeFromString<DnsRunPayload>(resultJson) }.getOrNull() ?: return
    InfoCard(title = "Answers") {
        if (payload.errorMessage != null) {
            Text("Error: ${payload.errorMessage}")
        } else if (payload.answers.isEmpty()) {
            Text("No records returned")
        } else {
            payload.answers.forEach { record -> InfoRow(record.type ?: "TYPE ${record.rawTypeCode}", record.data) }
        }
    }
}

@Composable
private fun HttpInspectorResultCard(resultJson: String) {
    val payload =
        runCatching { diagnosticHistoryJson.decodeFromString<HttpInspectionRunPayload>(resultJson) }.getOrNull()
            ?: return
    InfoCard(title = "Response chain") {
        if (payload.errorMessage != null) {
            Text("Error: ${payload.errorMessage}")
        } else {
            payload.chain.forEach { hop -> InfoRow(hop.url, hop.statusLine) }
            payload.tls?.let { tls -> InfoRow("TLS subject", tls.subject) }
        }
    }
}

@Composable
private fun WhoisResultCard(resultJson: String) {
    val payload =
        runCatching { diagnosticHistoryJson.decodeFromString<WhoisRunPayload>(resultJson) }.getOrNull() ?: return
    InfoCard(title = "Referral chain") {
        if (payload.errorMessage != null) {
            Text("Error: ${payload.errorMessage}")
        } else {
            payload.hops.forEach { hop -> InfoRow(hop.server, hop.responseText.take(80)) }
        }
    }
}
