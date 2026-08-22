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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.enthusiastdev.netinspector.core.designsystem.component.InfoCard
import dev.enthusiastdev.netinspector.core.model.diagnostics.PortScanPresetKind
import dev.enthusiastdev.netinspector.core.model.diagnostics.PortSelection
import dev.enthusiastdev.netinspector.core.model.settings.RssiDisplayUnit
import dev.enthusiastdev.netinspector.core.model.settings.ThemeMode
import dev.enthusiastdev.netinspector.ui.screens.connection.NotificationAccessButton
import dev.enthusiastdev.netinspector.ui.screens.connection.NotificationAccessState

@Composable
fun SettingsRoute(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    // Granting POST_NOTIFICATIONS via the system Settings app fires no callback this screen
    // would otherwise observe - re-check on resume, same as the Connection tab's own card.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshNotificationAccess()
        onPauseOrDispose {}
    }
    SettingsScreen(
        uiState = uiState,
        onThemeModeChange = viewModel::setThemeMode,
        onRssiDisplayUnitChange = viewModel::setRssiDisplayUnit,
        onScanRetentionChange = viewModel::setScanHistoryRetentionDays,
        onDiagnosticRetentionChange = viewModel::setDiagnosticHistoryRetentionDays,
        onDefaultPortSelectionChange = viewModel::setDefaultPortSelection,
        onNotificationAccessChanged = viewModel::refreshNotificationAccess,
        onRssiAlertThresholdChange = viewModel::setRssiAlertThresholdDbm,
        onAlertOnRssiDropChange = viewModel::setAlertOnRssiDrop,
        onAlertOnDisconnectChange = viewModel::setAlertOnDisconnect,
        onAlertOnReconnectChange = viewModel::setAlertOnReconnect,
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
    onNotificationAccessChanged: () -> Unit,
    onRssiAlertThresholdChange: (Int) -> Unit,
    onAlertOnRssiDropChange: (Boolean) -> Unit,
    onAlertOnDisconnectChange: (Boolean) -> Unit,
    onAlertOnReconnectChange: (Boolean) -> Unit,
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
        item {
            ConnectionAlertsSection(
                uiState = uiState,
                onThresholdChange = onRssiAlertThresholdChange,
                onRssiDropChange = onAlertOnRssiDropChange,
                onDisconnectChange = onAlertOnDisconnectChange,
                onReconnectChange = onAlertOnReconnectChange,
            )
        }
        // Independent of the Connection tab's card and its dismiss state: this section is the
        // only place granting notification access is guaranteed to be reachable, so it stays
        // visible for as long as the permission itself is missing.
        if (uiState.monitoringNotificationAccess != NotificationAccessState.GRANTED) {
            item { MonitoringSection(onNotificationAccessChanged) }
        }
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
        ThemeMode.AMOLED -> "AMOLED"
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

/** improvement-ideas.md #5 - the continuous-monitoring notification (`MonitoringService`) only
 * ever displayed live state until now; these three toggles are what actually turn a
 * disconnect/reconnect/weak-signal reading into a distinct alert notification, each read
 * straight from `AppSettingsRepository` the same way [RetentionSection]'s fields are. */
@Composable
private fun ConnectionAlertsSection(
    uiState: SettingsUiState,
    onThresholdChange: (Int) -> Unit,
    onRssiDropChange: (Boolean) -> Unit,
    onDisconnectChange: (Boolean) -> Unit,
    onReconnectChange: (Boolean) -> Unit,
) {
    InfoCard(title = "Connection alerts") {
        Text(
            "Alert while continuous monitoring is running, in addition to the ongoing status " +
                "notification.",
            style = MaterialTheme.typography.bodySmall,
        )
        AlertToggleRow(
            label = "Alert on disconnect",
            checked = uiState.alertOnDisconnect,
            onCheckedChange = onDisconnectChange,
        )
        AlertToggleRow(
            label = "Alert on reconnect",
            checked = uiState.alertOnReconnect,
            onCheckedChange = onReconnectChange,
        )
        AlertToggleRow(
            label = "Alert on weak signal",
            checked = uiState.alertOnRssiDrop,
            onCheckedChange = onRssiDropChange,
        )
        if (uiState.alertOnRssiDrop) {
            OutlinedTextField(
                value = uiState.rssiAlertThresholdDbm.toString(),
                onValueChange = { value -> value.toIntOrNull()?.let(onThresholdChange) },
                label = { Text("Weak signal threshold (dBm)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AlertToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
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

@Composable
private fun MonitoringSection(onNotificationAccessChanged: () -> Unit) {
    InfoCard(title = "Continuous monitoring") {
        Text(
            "Notification access is required before continuous monitoring can run.",
            style = MaterialTheme.typography.bodyMedium,
        )
        NotificationAccessButton(
            onGranted = {},
            onNotificationAccessChanged = onNotificationAccessChanged,
        )
    }
}
