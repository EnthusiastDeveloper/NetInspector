package dev.enthusiastdev.netinspector.data.persistence.preferences

import androidx.datastore.core.DataStore
import dev.enthusiastdev.netinspector.core.model.diagnostics.PortScanPresetKind
import dev.enthusiastdev.netinspector.core.model.diagnostics.PortSelection
import dev.enthusiastdev.netinspector.core.model.settings.RssiDisplayUnit
import dev.enthusiastdev.netinspector.core.model.settings.ThemeMode
import dev.enthusiastdev.netinspector.data.persistence.proto.AppPreferences
import dev.enthusiastdev.netinspector.data.persistence.proto.copy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** design §10 - the settings screen's fields other than retention (see
 * [RetentionSettingsRepository]): theme, RSSI display unit, and the port scanner's default
 * preset. "Scan cadence" and "sweep concurrency/timeouts" from that same design line are
 * deliberately not here - neither maps to a real knob in the current implementation
 * ([dev.enthusiastdev.netinspector.data.wifi.ScanGovernor] has no periodic-scan concept to set
 * a cadence on, and the LAN sweep's three-pass timeout/concurrency values are tuned per-pass,
 * not a single adjustable number) - see the Phase 8 milestone 5 commit for the full rationale. */
interface AppSettingsRepository {
    val themeMode: Flow<ThemeMode>
    val rssiDisplayUnit: Flow<RssiDisplayUnit>
    val defaultPortSelection: Flow<PortSelection>
    val monitoringCardDismissed: Flow<Boolean>

    // improvement-ideas.md #5 - opt-in, default off, matching this codebase's convention for
    // other notification-adjacent settings (see docs/open-items.md #5): a user who just started
    // continuous monitoring shouldn't be surprised by alerts they never asked for.
    val rssiAlertThresholdDbm: Flow<Int>
    val alertOnRssiDrop: Flow<Boolean>
    val alertOnDisconnect: Flow<Boolean>
    val alertOnReconnect: Flow<Boolean>

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setRssiDisplayUnit(unit: RssiDisplayUnit)

    suspend fun setDefaultPortSelection(selection: PortSelection)

    suspend fun setMonitoringCardDismissed(dismissed: Boolean)

    suspend fun setRssiAlertThresholdDbm(thresholdDbm: Int)

    suspend fun setAlertOnRssiDrop(enabled: Boolean)

    suspend fun setAlertOnDisconnect(enabled: Boolean)

    suspend fun setAlertOnReconnect(enabled: Boolean)

    companion object {
        /** A widely used "weak signal" cutoff - offered as the threshold field's starting
         * value once a user opts into RSSI-drop alerts, not a value enforced on them. */
        const val DEFAULT_RSSI_ALERT_THRESHOLD_DBM = -75
    }
}

class DefaultAppSettingsRepository
    @Inject
    constructor(
        private val dataStore: DataStore<AppPreferences>,
    ) : AppSettingsRepository {
        override val themeMode: Flow<ThemeMode> =
            dataStore.data.map { it.themeMode.toEnumOrDefault(ThemeMode.SYSTEM) }

        override val rssiDisplayUnit: Flow<RssiDisplayUnit> =
            dataStore.data.map { it.rssiDisplayUnit.toEnumOrDefault(RssiDisplayUnit.DBM) }

        override val defaultPortSelection: Flow<PortSelection> =
            dataStore.data.map { it.toPortSelection() }

        override val monitoringCardDismissed: Flow<Boolean> =
            dataStore.data.map { it.monitoringCardDismissed }

        override val rssiAlertThresholdDbm: Flow<Int> =
            dataStore.data.map {
                it.rssiAlertThresholdDbm.takeIf { dbm -> dbm != 0 }
                    ?: AppSettingsRepository.DEFAULT_RSSI_ALERT_THRESHOLD_DBM
            }

        override val alertOnRssiDrop: Flow<Boolean> = dataStore.data.map { it.alertOnRssiDrop }

        override val alertOnDisconnect: Flow<Boolean> = dataStore.data.map { it.alertOnDisconnect }

        override val alertOnReconnect: Flow<Boolean> = dataStore.data.map { it.alertOnReconnect }

        override suspend fun setThemeMode(mode: ThemeMode) {
            dataStore.updateData { it.copy { themeMode = mode.name } }
        }

        override suspend fun setRssiDisplayUnit(unit: RssiDisplayUnit) {
            dataStore.updateData { it.copy { rssiDisplayUnit = unit.name } }
        }

        override suspend fun setDefaultPortSelection(selection: PortSelection) {
            dataStore.updateData {
                it.copy {
                    defaultPortPreset = selection.kind.name
                    if (selection is PortSelection.Custom) {
                        defaultPortCustomStart = selection.start
                        defaultPortCustomEnd = selection.end
                    }
                }
            }
        }

        override suspend fun setMonitoringCardDismissed(dismissed: Boolean) {
            dataStore.updateData { it.copy { monitoringCardDismissed = dismissed } }
        }

        override suspend fun setRssiAlertThresholdDbm(thresholdDbm: Int) {
            dataStore.updateData { it.copy { rssiAlertThresholdDbm = thresholdDbm } }
        }

        override suspend fun setAlertOnRssiDrop(enabled: Boolean) {
            dataStore.updateData { it.copy { alertOnRssiDrop = enabled } }
        }

        override suspend fun setAlertOnDisconnect(enabled: Boolean) {
            dataStore.updateData { it.copy { alertOnDisconnect = enabled } }
        }

        override suspend fun setAlertOnReconnect(enabled: Boolean) {
            dataStore.updateData { it.copy { alertOnReconnect = enabled } }
        }
    }

private inline fun <reified T : Enum<T>> String.toEnumOrDefault(default: T): T =
    runCatching { enumValueOf<T>(this) }.getOrDefault(default)

private fun AppPreferences.toPortSelection(): PortSelection =
    when (defaultPortPreset.toEnumOrDefault(PortScanPresetKind.COMMON)) {
        PortScanPresetKind.COMMON -> PortSelection.Common
        PortScanPresetKind.WELL_KNOWN -> PortSelection.WellKnown
        PortScanPresetKind.ALL -> PortSelection.All
        PortScanPresetKind.CUSTOM ->
            PortSelection.Custom(
                start = defaultPortCustomStart.takeIf { it > 0 } ?: 1,
                end = defaultPortCustomEnd.takeIf { it > 0 } ?: 1024,
            )
    }
