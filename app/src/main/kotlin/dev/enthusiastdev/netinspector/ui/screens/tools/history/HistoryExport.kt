package dev.enthusiastdev.netinspector.ui.screens.tools.history

import dev.enthusiastdev.netinspector.data.persistence.diagnostics.DiagnosticRunEntity
import dev.enthusiastdev.netinspector.data.persistence.scan.KnownApEntity
import dev.enthusiastdev.netinspector.history.diagnosticHistoryJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.time.Instant

@Serializable
private data class KnownApExportDto(
    val bssid: String,
    val ssid: String,
    val vendor: String?,
    val firstSeen: String,
    val lastSeen: String,
    val bestRssiDbm: Int,
)

@Serializable
private data class DiagnosticRunExportDto(
    val target: String,
    val toolType: String,
    val timestamp: String,
    val durationMillis: Long,
    val summary: String,
    val parametersJson: String,
    val resultJson: String,
)

/** design §8 - "Export to CSV and JSON." Exports exactly what each history screen already
 * lists (known APs / diagnostic runs), not a deeper per-session/per-observation dump - keeps
 * the exported rows matching what's on screen when the user taps Export. Timestamps go out as
 * ISO-8601 (`Instant.toString()`), not epoch millis, so the CSV is legible in a spreadsheet
 * without a manual conversion step. */
internal object ScanHistoryExporter {
    fun toCsv(knownAps: List<KnownApEntity>): String =
        csvDocument(
            header = listOf("bssid", "ssid", "vendor", "firstSeen", "lastSeen", "bestRssiDbm"),
            rows = knownAps.map { it.toExportDto().toCsvRow() },
        )

    fun toJson(knownAps: List<KnownApEntity>): String =
        diagnosticHistoryJson.encodeToString(knownAps.map { it.toExportDto() })
}

internal object DiagnosticHistoryExporter {
    fun toCsv(runs: List<DiagnosticRunEntity>): String =
        csvDocument(
            header =
                listOf(
                    "target",
                    "toolType",
                    "timestamp",
                    "durationMillis",
                    "summary",
                    "parametersJson",
                    "resultJson",
                ),
            rows = runs.map { it.toExportDto().toCsvRow() },
        )

    fun toJson(runs: List<DiagnosticRunEntity>): String =
        diagnosticHistoryJson.encodeToString(runs.map { it.toExportDto() })
}

private fun KnownApEntity.toExportDto() =
    KnownApExportDto(
        bssid = bssid,
        ssid = ssid,
        vendor = vendor,
        firstSeen = Instant.ofEpochMilli(firstSeenMillis).toString(),
        lastSeen = Instant.ofEpochMilli(lastSeenMillis).toString(),
        bestRssiDbm = bestRssiDbm,
    )

private fun KnownApExportDto.toCsvRow(): List<String> =
    listOf(bssid, ssid, vendor.orEmpty(), firstSeen, lastSeen, bestRssiDbm.toString())

private fun DiagnosticRunEntity.toExportDto() =
    DiagnosticRunExportDto(
        target = target,
        toolType = toolType,
        timestamp = Instant.ofEpochMilli(timestampMillis).toString(),
        durationMillis = durationMillis,
        summary = summary,
        parametersJson = parametersJson,
        resultJson = resultJson,
    )

private fun DiagnosticRunExportDto.toCsvRow(): List<String> =
    listOf(target, toolType, timestamp, durationMillis.toString(), summary, parametersJson, resultJson)

private fun csvDocument(
    header: List<String>,
    rows: List<List<String>>,
): String {
    val lines = listOf(header) + rows
    return lines.joinToString("\n") { row -> row.joinToString(",") { it.csvField() } }
}

private val CSV_SPECIAL_CHARS = charArrayOf(',', '"', '\n', '\r')

private fun String.csvField(): String =
    if (any { it in CSV_SPECIAL_CHARS }) {
        "\"${replace("\"", "\"\"")}\""
    } else {
        this
    }
