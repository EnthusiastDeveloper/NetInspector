package dev.enthusiastdev.netinspector.ui.screens.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.enthusiastdev.netinspector.core.designsystem.component.InfoCard
import dev.enthusiastdev.netinspector.core.model.diagnostics.PortScanPresetKind
import dev.enthusiastdev.netinspector.core.model.diagnostics.PortSelection
import dev.enthusiastdev.netinspector.core.model.settings.RssiDisplayUnit
import dev.enthusiastdev.netinspector.core.model.settings.ThemeMode

@Composable
fun SettingsRoute(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    SettingsScreen(
        uiState = uiState,
        onThemeModeChange = viewModel::setThemeMode,
        onRssiDisplayUnitChange = viewModel::setRssiDisplayUnit,
        onScanRetentionChange = viewModel::setScanHistoryRetentionDays,
        onDiagnosticRetentionChange = viewModel::setDiagnosticHistoryRetentionDays,
        onDefaultPortSelectionChange = viewModel::setDefaultPortSelection,
        modifier = modifier,
    )
}

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onThemeModeChange: (ThemeMode) -> Unit,
    onRssiDisplayUnitChange: (RssiDisplayUnit) -> Unit,
    onScanRetentionChange: (Int) -> Unit,
    onDiagnosticRetentionChange: (Int) -> Unit,
    onDefaultPortSelectionChange: (PortSelection) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().widthIn(max = 600.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text(text = "Settings", style = MaterialTheme.typography.titleLarge) }
        item { AppearanceSection(uiState, onThemeModeChange, onRssiDisplayUnitChange) }
        item {
            RetentionSection(uiState, onScanRetentionChange, onDiagnosticRetentionChange)
        }
        item { PortScannerSection(uiState.defaultPortSelection, onDefaultPortSelectionChange) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceSection(
    uiState: SettingsUiState,
    onThemeModeChange: (ThemeMode) -> Unit,
    onRssiDisplayUnitChange: (RssiDisplayUnit) -> Unit,
) {
    InfoCard(title = "Appearance") {
        Text("Theme", style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ThemeMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = mode == uiState.themeMode,
                    onClick = { onThemeModeChange(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = ThemeMode.entries.size),
                ) {
                    Text(mode.label())
                }
            }
        }
        Text("RSSI display", style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            RssiDisplayUnit.entries.forEachIndexed { index, unit ->
                SegmentedButton(
                    selected = unit == uiState.rssiDisplayUnit,
                    onClick = { onRssiDisplayUnitChange(unit) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = RssiDisplayUnit.entries.size),
                ) {
                    Text(if (unit == RssiDisplayUnit.DBM) "dBm" else "Percent")
                }
            }
        }
    }
}

private fun ThemeMode.label(): String =
    when (this) {
        ThemeMode.SYSTEM -> "System"
        ThemeMode.LIGHT -> "Light"
        ThemeMode.DARK -> "Dark"
    }

@Composable
private fun RetentionSection(
    uiState: SettingsUiState,
    onScanRetentionChange: (Int) -> Unit,
    onDiagnosticRetentionChange: (Int) -> Unit,
) {
    InfoCard(title = "History retention") {
        Text(
            "How long scan and diagnostic history is kept before the daily cleanup removes it.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RetentionField(
                label = "Wi-Fi history (days)",
                days = uiState.scanHistoryRetentionDays,
                onChange = onScanRetentionChange,
                modifier = Modifier.weight(1f),
            )
            RetentionField(
                label = "Diagnostic history (days)",
                days = uiState.diagnosticHistoryRetentionDays,
                onChange = onDiagnosticRetentionChange,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun RetentionField(
    label: String,
    days: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = days.toString(),
        onValueChange = { value -> value.toIntOrNull()?.let(onChange) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
private fun PortScannerSection(
    selection: PortSelection,
    onSelectionChange: (PortSelection) -> Unit,
) {
    InfoCard(title = "Port scanner") {
        Text("Default preset for new scans", style = MaterialTheme.typography.labelLarge)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            FilterChip(
                selected = selection.kind == PortScanPresetKind.COMMON,
                onClick = { onSelectionChange(PortSelection.Common) },
                label = { Text("Common") },
            )
            FilterChip(
                selected = selection.kind == PortScanPresetKind.WELL_KNOWN,
                onClick = { onSelectionChange(PortSelection.WellKnown) },
                label = { Text("1-1024") },
            )
            FilterChip(
                selected = selection.kind == PortScanPresetKind.ALL,
                onClick = { onSelectionChange(PortSelection.All) },
                label = { Text("All") },
            )
            FilterChip(
                selected = selection.kind == PortScanPresetKind.CUSTOM,
                onClick = {
                    val current = selection as? PortSelection.Custom
                    onSelectionChange(PortSelection.Custom(current?.start ?: 1, current?.end ?: 1024))
                },
                label = { Text("Custom") },
            )
        }
        if (selection is PortSelection.Custom) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = selection.start.toString(),
                    onValueChange = { value ->
                        value.toIntOrNull()?.let { onSelectionChange(selection.copy(start = it)) }
                    },
                    label = { Text("Start") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = selection.end.toString(),
                    onValueChange = { value ->
                        value.toIntOrNull()?.let { onSelectionChange(selection.copy(end = it)) }
                    },
                    label = { Text("End") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
