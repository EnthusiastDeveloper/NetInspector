package dev.enthusiastdev.netinspector.ui.screens.tools.throughput

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.enthusiastdev.netinspector.core.common.throughput.overlappingChannelCount
import dev.enthusiastdev.netinspector.core.model.connection.ConnectionSnapshot
import dev.enthusiastdev.netinspector.core.model.diagnostics.ThroughputResult
import dev.enthusiastdev.netinspector.data.diagnostics.throughput.LanThroughputRepository
import dev.enthusiastdev.netinspector.data.diagnostics.throughput.ThroughputConfig
import dev.enthusiastdev.netinspector.data.diagnostics.throughput.ThroughputEvent
import dev.enthusiastdev.netinspector.data.lan.LanDiscoveryRepository
import dev.enthusiastdev.netinspector.data.persistence.diagnostics.DiagnosticRunRecord
import dev.enthusiastdev.netinspector.data.persistence.diagnostics.DiagnosticRunRepository
import dev.enthusiastdev.netinspector.data.wifi.ConnectionRepository
import dev.enthusiastdev.netinspector.data.wifi.WifiScanRepository
import dev.enthusiastdev.netinspector.data.wifi.WifiScanState
import dev.enthusiastdev.netinspector.history.DiagnosticToolType
import dev.enthusiastdev.netinspector.history.ThroughputRunPayload
import dev.enthusiastdev.netinspector.history.WifiCorrelationDto
import dev.enthusiastdev.netinspector.history.diagnosticHistoryJson
import dev.enthusiastdev.netinspector.history.diagnosticRunParametersJson
import dev.enthusiastdev.netinspector.history.toDto
import dev.enthusiastdev.netinspector.history.toHistorySummary
import dev.enthusiastdev.netinspector.ui.navigation.ThroughputToolRoute
import dev.enthusiastdev.netinspector.ui.screens.devices.addressString
import dev.enthusiastdev.netinspector.ui.screens.devices.displayName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import java.net.Inet4Address
import java.net.InetAddress
import javax.inject.Inject

@HiltViewModel
class ThroughputViewModel
    @Inject
    constructor(
        private val lanThroughputRepository: LanThroughputRepository,
        private val lanDiscoveryRepository: LanDiscoveryRepository,
        private val connectionRepository: ConnectionRepository,
        private val wifiScanRepository: WifiScanRepository,
        private val diagnosticRunRepository: DiagnosticRunRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val initialTarget = savedStateHandle.toRoute<ThroughputToolRoute>().target.orEmpty()
        private val _uiState = MutableStateFlow(ThroughputUiState(target = initialTarget))
        val uiState: StateFlow<ThroughputUiState> = _uiState.asStateFlow()

        // Kept eagerly live for the whole time this screen is open (not WhileSubscribed, unlike
        // the Devices tab's own combine) - a correlation snapshot has to be readable the instant
        // a run starts or completes, not after a fresh subscriber's first emission catches up.
        private val latestConnection: StateFlow<ConnectionSnapshot?> =
            connectionRepository.connectionSnapshot.stateIn(viewModelScope, SharingStarted.Eagerly, null)
        private val latestScanState: StateFlow<WifiScanState> =
            wifiScanRepository.scanState.stateIn(viewModelScope, SharingStarted.Eagerly, WifiScanState(emptyList(), 0))

        private var testJob: Job? = null
        private val config = ThroughputConfig()

        init {
            viewModelScope.launch {
                lanDiscoveryRepository.hosts.collect { hosts ->
                    val options =
                        hosts
                            .filterNot { it.isSelf }
                            .map { host ->
                                HostOption(address = host.address.addressString, label = host.displayName())
                            }
                    _uiState.update { it.copy(hostOptions = options) }
                }
            }
        }

        fun updateTarget(value: String) {
            _uiState.update { it.copy(target = value) }
        }

        fun selectHost(option: HostOption) {
            _uiState.update { it.copy(target = option.address) }
        }

        fun start() {
            val host = _uiState.value.target.trim()
            if (host.isEmpty()) return

            testJob?.cancel()
            testJob =
                viewModelScope.launch {
                    val address = resolveIpv4(host)
                    if (address == null) {
                        _uiState.update { it.copy(errorMessage = "Could not resolve \"$host\" to an IPv4 address") }
                        return@launch
                    }

                    val correlationAtStart = currentCorrelation()
                    _uiState.update {
                        it.copy(
                            isRunning = true,
                            mbpsSamples = emptyList(),
                            packetsSent = 0,
                            packetsReceived = 0,
                            result = null,
                            correlationAtStart = correlationAtStart,
                            correlationAtEnd = null,
                            errorMessage = null,
                        )
                    }

                    val startedAtMillis = System.currentTimeMillis()
                    lanThroughputRepository.measure(address, config).collect { event ->
                        handleEvent(event, host, correlationAtStart, startedAtMillis)
                    }
                }
        }

        private suspend fun handleEvent(
            event: ThroughputEvent,
            host: String,
            correlationAtStart: WifiCorrelationSnapshot?,
            startedAtMillis: Long,
        ) {
            when (event) {
                is ThroughputEvent.Sample ->
                    _uiState.update {
                        it.copy(
                            mbpsSamples = (it.mbpsSamples + event.sample.instantMbps.toFloat()).takeLast(MAX_SAMPLES),
                            packetsSent = event.sample.packetsSent,
                            packetsReceived = event.sample.packetsReceived,
                        )
                    }

                is ThroughputEvent.Complete -> {
                    val correlationAtEnd = currentCorrelation()
                    _uiState.update {
                        it.copy(
                            isRunning = false,
                            result = event.result,
                            correlationAtEnd = correlationAtEnd,
                            packetsSent = event.result.packetsSent,
                            packetsReceived = event.result.packetsReceived,
                        )
                    }
                    recordHistory(
                        host,
                        event.result,
                        correlationAtStart,
                        correlationAtEnd,
                        System.currentTimeMillis() - startedAtMillis,
                    )
                }

                is ThroughputEvent.Unsupported ->
                    _uiState.update { it.copy(isRunning = false, errorMessage = event.reason) }
            }
        }

        fun stop() {
            testJob?.cancel()
            _uiState.update { it.copy(isRunning = false) }
        }

        /** design §5.1 - RSSI comes from the `NetworkCallback` stream, the authoritative source
         * for the *connected* network (scan results are for other networks only); channel
         * congestion comes from `WifiScanRepository`'s scan state instead, since that's the only
         * source with visibility into neighbouring APs at all (improvement-ideas.md #31's
         * correlation differentiator). */
        private fun currentCorrelation(): WifiCorrelationSnapshot? {
            val connection = latestConnection.value ?: return null
            val span = connection.span
            val overlap =
                span?.let {
                    val others =
                        latestScanState.value.accessPoints
                            .filterNot { ap -> ap.bssid == connection.bssid }
                            .map { ap -> ap.span }
                    overlappingChannelCount(it, others)
                }
            return WifiCorrelationSnapshot(
                ssid = connection.ssid,
                bssid = connection.bssid,
                rssiDbm = connection.rssiDbm,
                channel = span?.primaryChannel,
                widthMhz = span?.widthMhz,
                overlappingApCount = overlap,
            )
        }

        private suspend fun recordHistory(
            host: String,
            result: ThroughputResult,
            correlationAtStart: WifiCorrelationSnapshot?,
            correlationAtEnd: WifiCorrelationSnapshot?,
            durationMillis: Long,
        ) {
            val payload = ThroughputRunPayload(result.toDto(), correlationAtStart?.toDto(), correlationAtEnd?.toDto())
            diagnosticRunRepository.record(
                DiagnosticRunRecord(
                    toolType = DiagnosticToolType.LAN_THROUGHPUT.name,
                    target = host,
                    durationMillis = durationMillis,
                    summary = result.toHistorySummary(),
                    parametersJson = diagnosticRunParametersJson(mapOf("durationMs" to config.durationMs.toString())),
                    resultJson = diagnosticHistoryJson.encodeToString(payload),
                ),
            )
        }

        private fun WifiCorrelationSnapshot.toDto(): WifiCorrelationDto =
            WifiCorrelationDto(ssid, bssid, rssiDbm, channel, widthMhz, overlappingApCount)

        private suspend fun resolveIpv4(host: String): Inet4Address? =
            withContext(Dispatchers.IO) {
                runCatching {
                    InetAddress.getAllByName(host).filterIsInstance<Inet4Address>().firstOrNull()
                }.getOrNull()
            }

        override fun onCleared() {
            testJob?.cancel()
        }

        private companion object {
            // Matches PingViewModel's rolling-chart convention: bounded so the in-memory sample
            // list can't grow past a screen's worth even on an unusually long run.
            const val MAX_SAMPLES = 60
        }
    }
