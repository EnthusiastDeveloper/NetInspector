package dev.enthusiastdev.netinspector.ui.screens.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.enthusiastdev.netinspector.R
import dev.enthusiastdev.netinspector.core.designsystem.component.InfoCard
import dev.enthusiastdev.netinspector.core.model.diagnostics.PortScanPresetKind
import dev.enthusiastdev.netinspector.core.model.diagnostics.PortSelection
import dev.enthusiastdev.netinspector.core.model.settings.RssiDisplayUnit
import dev.enthusiastdev.netinspector.core.model.settings.ThemeMode
import dev.enthusiastdev.netinspector.data.persistence.preferences.AppSettingsRepository
import kotlin.math.roundToInt

/** The Settings tab's entry point: binds [SettingsViewModel] to [SettingsScreen]. Named
 * `*Destination` to match the other top-level destinations in `NetInspectorApp`, and to leave the
 * `SettingsRoute` name to the nav route object itself. */
@Composable
fun SettingsDestination(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    // A crash written since this screen was last visited is filesystem state, not a Flow this
    // ViewModel already observes - re-check on resume.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshCrashReportAvailability()
        onPauseOrDispose {}
    }
    SettingsScreen(
        uiState = uiState,
        onThemeModeChange = viewModel::setThemeMode,
        onRssiDisplayUnitChange = viewModel::setRssiDisplayUnit,
        onScanRetentionChange = viewModel::setScanHistoryRetentionDays,
        onDiagnosticRetentionChange = viewModel::setDiagnosticHistoryRetentionDays,
        onDefaultPortSelectionChange = viewModel::setDefaultPortSelection,
        onRssiAlertThresholdChange = viewModel::setRssiAlertThresholdDbm,
        onAlertOnRssiDropChange = viewModel::setAlertOnRssiDrop,
        onAlertOnDisconnectChange = viewModel::setAlertOnDisconnect,
        onAlertOnReconnectChange = viewModel::setAlertOnReconnect,
        onAutoScanEnabledChange = viewModel::setAutoScanEnabled,
        onAutoScanIntervalChange = viewModel::setAutoScanIntervalMinutes,
        onAlertOnLanHostChangesChange = viewModel::setAlertOnLanHostChanges,
        onCrashReportingToggle = viewModel::setCrashReportingEnabled,
        onExportCrashReport = viewModel::exportCrashReport,
        onExportDebugBundle = viewModel::exportDebugBundle,
        onUiFontScaleChange = viewModel::setUiFontScale,
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
    onRssiAlertThresholdChange: (Int) -> Unit,
    onAlertOnRssiDropChange: (Boolean) -> Unit,
    onAlertOnDisconnectChange: (Boolean) -> Unit,
    onAlertOnReconnectChange: (Boolean) -> Unit,
    onAutoScanEnabledChange: (Boolean) -> Unit,
    onAutoScanIntervalChange: (Int) -> Unit,
    onAlertOnLanHostChangesChange: (Boolean) -> Unit,
    onCrashReportingToggle: (Boolean) -> Unit,
    onExportCrashReport: () -> Unit,
    onExportDebugBundle: () -> Unit,
    onUiFontScaleChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().widthIn(max = 600.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.destination_settings),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item { AppearanceSection(uiState, onThemeModeChange, onRssiDisplayUnitChange) }
        item { DisplayScaleSection(uiState.uiFontScale, onUiFontScaleChange) }
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
        item {
            AutomaticScanningSection(
                uiState = uiState,
                onAutoScanEnabledChange = onAutoScanEnabledChange,
                onIntervalChange = onAutoScanIntervalChange,
                onAlertOnLanHostChangesChange = onAlertOnLanHostChangesChange,
            )
        }
        item {
            DebugSection(
                crashReportingEnabled = uiState.crashReportingEnabled,
                hasCrashReports = uiState.hasCrashReports,
                onCrashReportingToggle = onCrashReportingToggle,
                onExportCrashReport = onExportCrashReport,
                onExportDebugBundle = onExportDebugBundle,
            )
        }
        item { AboutSection() }
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

/** improvement-ideas.md #36. The slider drags a local [previewScale] rather than writing to
 * [AppSettingsRepository] on every drag tick - that would mean a DataStore write per pixel of
 * drag, and would only reflect back into this composable after a full write/read/recompose
 * round trip through [SettingsViewModel]. `onValueChangeFinished` is where the drag's final
 * value actually gets persisted (and, via MainActivity's app-root `CompositionLocalProvider`,
 * takes over the whole app's `fontScale`). The preview card below is re-densitied to
 * [previewScale] directly so it tracks the drag in real time regardless of that round trip. */
@Composable
private fun DisplayScaleSection(
    uiFontScale: Float,
    onUiFontScaleChange: (Float) -> Unit,
) {
    var previewScale by remember(uiFontScale) { mutableFloatStateOf(uiFontScale) }
    InfoCard(title = "Text & UI scale") {
        Text(
            "Scales text and UI sizing across the whole app, independent of the system's " +
                "accessibility font size setting.",
            style = MaterialTheme.typography.bodySmall,
        )
        Slider(
            value = previewScale,
            onValueChange = { previewScale = it },
            onValueChangeFinished = { onUiFontScaleChange(previewScale) },
            valueRange = AppSettingsRepository.MIN_UI_FONT_SCALE..AppSettingsRepository.MAX_UI_FONT_SCALE,
            steps = 8,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "${(previewScale * 100).roundToInt()}%",
            style = MaterialTheme.typography.labelLarge,
        )
        val baseDensity = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(baseDensity.density, previewScale)) {
            Surface(
                tonalElevation = 2.dp,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Kitchen AP-42", style = MaterialTheme.typography.titleMedium)
                    Text("192.168.1.42  ·  -52 dBm", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
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

/** ideas.md #5 - the continuous-monitoring notification (`MonitoringService`) only
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

/** `internal`, not `private`: also used by [SettingsDebugSection] (a separate file - keeping
 * this section there instead of growing this file further past detekt's per-file function
 * threshold). */
@Composable
internal fun AlertToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // labelLarge, not bodyMedium - matches the "Theme"/"Default preset for new scans"
        // style other sections use for a control's own name, so it reads as a label for the
        // switch next to it rather than a continuation of whatever description text precedes it.
        Text(label, style = MaterialTheme.typography.labelLarge)
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
