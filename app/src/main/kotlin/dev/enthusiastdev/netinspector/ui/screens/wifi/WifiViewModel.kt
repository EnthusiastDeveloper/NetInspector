package dev.enthusiastdev.netinspector.ui.screens.wifi

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.enthusiastdev.netinspector.core.model.wifi.ScanOutcome
import dev.enthusiastdev.netinspector.data.persistence.preferences.AppSettingsRepository
import dev.enthusiastdev.netinspector.data.persistence.scan.ScanHistoryRepository
import dev.enthusiastdev.netinspector.data.wifi.ConnectionRepository
import dev.enthusiastdev.netinspector.data.wifi.WifiScanRepository
import dev.enthusiastdev.netinspector.usecase.RecordWifiScanUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WifiViewModel
    @Inject
    constructor(
        private val wifiScanRepository: WifiScanRepository,
        connectionRepository: ConnectionRepository,
        private val recordWifiScan: RecordWifiScanUseCase,
        private val appSettingsRepository: AppSettingsRepository,
        scanHistoryRepository: ScanHistoryRepository,
        @ApplicationContext private val context: Context,
    ) : ViewModel() {
        // Drives the throttle countdown and the permission-state check - neither has its own
        // change signal to collect, so this just re-samples both once a second.
        private val ticker =
            flow {
                while (true) {
                    emit(Unit)
                    delay(1_000)
                }
            }

        // design §10/Phase 8 - persists every passively-harvested scan generation as it arrives,
        // independent of `uiState`'s accumulation (a `WhileSubscribed` collector further down
        // would otherwise stop recording history the moment the screen isn't visible, which the
        // history feature explicitly shouldn't depend on while the screen *is* open).
        init {
            wifiScanRepository.scanSnapshots
                .onEach { recordWifiScan(it) }
                .launchIn(viewModelScope)
        }

        val uiState: StateFlow<WifiUiState> =
            combine(
                wifiScanRepository.scanState,
                ticker,
                appSettingsRepository.rssiDisplayUnit,
                // Read only to tell the channel recommendation which AP is *this* device's, so it
                // can be excluded from the interference it is being compared against.
                connectionRepository.connectionSnapshot,
                // ideas.md #11 - source of the AP detail screen's capability-change
                // card; filtered down to only the entries that actually have one, so the common
                // case is an empty map.
                scanHistoryRepository.knownAps(),
            ) { scanState, _, rssiDisplayUnit, connection, knownAps ->
                WifiUiState.Content(
                    accessPoints = scanState.accessPoints,
                    sampleCount = scanState.sampleCount,
                    wifiAccess = currentWifiAccessState(),
                    budget = wifiScanRepository.budget(),
                    lastUpdated = scanState.accessPoints.maxOfOrNull { it.lastSeen },
                    rssiDisplayUnit = rssiDisplayUnit,
                    connectedBssid = connection?.bssid,
                    connectedSpan = connection?.span,
                    apCapabilityChanges =
                        knownAps.filter { it.lastCapabilityChangeMillis != null }.associateBy { it.bssid },
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = WifiUiState.Loading,
            )

        /** design §6.1 - "one [active scan] on screen entry": called from `ON_RESUME`, which
         * covers first entry and also re-arming after a permission grant (nothing else would
         * otherwise trigger a fresh broadcast once access is newly granted). A denied or
         * still-missing permission is a silent no-op here - the card below drives that flow. */
        fun onResumed() {
            if (currentWifiAccessState() != WifiAccessState.GRANTED) return
            viewModelScope.launch { wifiScanRepository.requestScan(isUserInitiated = false) }
        }

        private val _isRefreshing = MutableStateFlow(false)
        val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

        /** design §6.1 - the explicit pull-to-refresh scan; dips into the two reserved
         * tokens if the non-reserved ones are already spent. `isRefreshing` is purely a spinner
         * affordance sized to a typical scan's completion time - the header's "results as of"
         * timestamp remains the actual freshness signal regardless of this flag.
         *
         * The delay always runs, even when throttled: `requestScan` returns near-instantly in
         * that case, and flipping `isRefreshing` true then false within the same frame leaves
         * PullToRefreshBox's indicator stuck visible without ever starting its spin animation. */
        fun onRefresh() {
            viewModelScope.launch {
                _isRefreshing.value = true
                val outcome = wifiScanRepository.requestScan(isUserInitiated = true)
                delay(if (outcome is ScanOutcome.Started) 3_000 else 500)
                _isRefreshing.value = false
            }
        }

        fun informationElements(bssid: String) = wifiScanRepository.informationElements(bssid)

        private fun currentWifiAccessState(): WifiAccessState {
            val hasPermission =
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
            if (!hasPermission) return WifiAccessState.PERMISSION_NEEDED

            val locationEnabled = context.getSystemService(LocationManager::class.java)?.isLocationEnabled == true
            return if (locationEnabled) WifiAccessState.GRANTED else WifiAccessState.SERVICES_DISABLED
        }
    }
