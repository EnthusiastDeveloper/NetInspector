package dev.enthusiastdev.netinspector.ui.screens.tools.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import dev.enthusiastdev.netinspector.core.designsystem.component.InfoCard
import dev.enthusiastdev.netinspector.core.designsystem.component.InfoRow
import dev.enthusiastdev.netinspector.history.ThroughputRunPayload
import dev.enthusiastdev.netinspector.history.WifiCorrelationDto
import dev.enthusiastdev.netinspector.history.diagnosticHistoryJson
import kotlinx.serialization.decodeFromString

/** Split out of `DiagnosticRunDetailContent.kt` (called from its `ToolResultSection`) to keep
 * that file under detekt's per-file function-count threshold - this tool's result happens to
 * need two cards (the throughput numbers, plus the Wi-Fi correlation snapshot) where every
 * other tool's history detail needs only one. */
@Composable
internal fun ThroughputResultCard(resultJson: String) {
    val payload =
        runCatching { diagnosticHistoryJson.decodeFromString<ThroughputRunPayload>(resultJson) }.getOrNull() ?: return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        InfoCard(title = "Result") {
            InfoRow("Average", "%.1f Mbps".format(payload.result.avgMbps))
            InfoRow("Peak", "%.1f Mbps".format(payload.result.peakMbps))
            InfoRow("Sent / received", "${payload.result.packetsSent} / ${payload.result.packetsReceived}")
            InfoRow("Loss", "%.0f%%".format(payload.result.lossPercent))
        }
        payload.correlationAtStart?.let { correlation ->
            InfoCard(title = "Wi-Fi at test start") { WifiCorrelationRows(correlation) }
        }
        payload.correlationAtEnd?.let { correlation ->
            InfoCard(title = "Wi-Fi at test end") { WifiCorrelationRows(correlation) }
        }
    }
}

@Composable
private fun WifiCorrelationRows(correlation: WifiCorrelationDto) {
    if (correlation.ssid == null) {
        Text("Not connected to Wi-Fi")
        return
    }
    InfoRow("Network", correlation.ssid)
    correlation.rssiDbm?.let { InfoRow("RSSI", "$it dBm") }
    correlation.channel?.let { channel ->
        val width = correlation.widthMhz?.let { " ($it MHz)" }.orEmpty()
        InfoRow("Channel", "$channel$width")
    }
    correlation.overlappingApCount?.let { InfoRow("Other networks on this channel", it.toString()) }
}
