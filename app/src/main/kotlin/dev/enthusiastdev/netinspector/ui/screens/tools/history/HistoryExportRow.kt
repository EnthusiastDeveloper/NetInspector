package dev.enthusiastdev.netinspector.ui.screens.tools.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/** Shared by both history list panes' headers - same two-button CSV/JSON export affordance,
 * differing only in which `ActivityResultContracts.CreateDocument` launchers the caller wires
 * up (each screen exports a different row shape, see [ScanHistoryExporter]/
 * [DiagnosticHistoryExporter]). */
@Composable
internal fun HistoryExportRow(
    onExportCsv: () -> Unit,
    onExportJson: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onExportCsv) { Text("Export CSV") }
        OutlinedButton(onClick = onExportJson) { Text("Export JSON") }
    }
}
