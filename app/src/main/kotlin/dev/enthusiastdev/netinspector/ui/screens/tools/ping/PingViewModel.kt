package dev.enthusiastdev.netinspector.ui.screens.tools.ping

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.enthusiastdev.netinspector.core.common.icmp.PingSummary
import dev.enthusiastdev.netinspector.core.common.icmp.summarizePing
import dev.enthusiastdev.netinspector.core.model.diagnostics.PingProbeResult
import dev.enthusiastdev.netinspector.core.model.diagnostics.PingTier
import dev.enthusiastdev.netinspector.data.diagnostics.icmp.PingConfig
import dev.enthusiastdev.netinspector.data.diagnostics.icmp.PingRepository
import dev.enthusiastdev.netinspector.data.persistence.diagnostics.DiagnosticRunRecord
import dev.enthusiastdev.netinspector.data.persistence.diagnostics.DiagnosticRunRepository
import dev.enthusiastdev.netinspector.history.DiagnosticToolType
import dev.enthusiastdev.netinspector.history.PingRunPayload
import dev.enthusiastdev.netinspector.history.diagnosticHistoryJson
import dev.enthusiastdev.netinspector.history.diagnosticRunParametersJson
import dev.enthusiastdev.netinspector.history.toDto
import dev.enthusiastdev.netinspector.history.toHistorySummary
import dev.enthusiastdev.netinspector.ui.navigation.PingToolRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import java.net.Inet4Address
import java.net.InetAddress
import javax.inject.Inject

@HiltViewModel
class PingViewModel
    @Inject
    constructor(
        private val pingRepository: PingRepository,
        private val diagnosticRunRepository: DiagnosticRunRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val _uiState =
            MutableStateFlow(PingUiState(target = savedStateHandle.toRoute<PingToolRoute>().target.orEmpty()))
        val uiState: StateFlow<PingUiState> = _uiState.asStateFlow()

        private var runJob: Job? = null

        fun updateTarget(value: String) {
            _uiState.update { it.copy(target = value) }
        }

        fun setLoopMode(enabled: Boolean) {
            if (_uiState.value.isRunning) return
            _uiState.update { it.copy(isLoopMode = enabled) }
        }

        fun start() {
            val host = _uiState.value.target.trim()
            if (host.isEmpty()) return
            val loop = _uiState.value.isLoopMode

            runJob?.cancel()
            runJob =
                viewModelScope.launch {
                    val address = resolveIpv4(host)
                    if (address == null) {
                        _uiState.update { it.copy(errorMessage = "Could not resolve \"$host\" to an IPv4 address") }
                        return@launch
                    }

                    _uiState.update {
                        it.copy(
                            isRunning = true,
                            results = emptyList(),
                            rttSamples = emptyList(),
                            summary = null,
                            errorMessage = null,
                        )
                    }

                    // Loop mode ("ping -t" style) reuses the same flow-based API with an
                    // effectively unlimited count instead of adding a second streaming entry
                    // point to PingRepository - stop() below cancels runJob to end it, the same
                    // cancellation path a fixed-count run's Stop button always used.
                    val config = if (loop) PingConfig(count = Int.MAX_VALUE) else PingConfig()
                    val startedAtMillis = System.currentTimeMillis()
                    // Bounded so an hours-long loop run can't grow this (and the results/chart
                    // state derived from it) without limit - oldest probes fall off the window.
                    val window = ArrayDeque<PingProbeResult>()
                    pingRepository.ping(address, config).collect { result ->
                        window.addLast(result)
                        if (window.size > MAX_ROLLING_SAMPLES) window.removeFirst()
                        _uiState.update { it.copy(results = window.toList(), rttSamples = window.rttSamples()) }
                    }

                    // Only reached when the flow completes on its own, i.e. the fixed-count
                    // path. Loop mode's Int.MAX_VALUE count never gets here in practice - stop()
                    // cancels runJob, which interrupts the collect above instead, and stop()
                    // does its own (unrecorded) summary finalization for that case.
                    val collected = window.toList()
                    val rtts = collected.filterIsInstance<PingProbeResult.Reply>().map { it.rttMs }
                    val sent = if (loop) collected.size else config.count
                    val summary = summarizePing(PingTier.ICMP_SOCKET, sent, rtts)
                    _uiState.update { it.copy(isRunning = false, summary = summary) }

                    recordHistory(host, config, collected, summary, System.currentTimeMillis() - startedAtMillis)
                }
        }

        fun stop() {
            val state = _uiState.value
            runJob?.cancel()
            if (state.isLoopMode && state.isRunning) {
                // Loop mode is never persisted to diagnostic_run history: an indefinite run
                // only ever keeps the last MAX_ROLLING_SAMPLES probes in memory (see start()),
                // so a summary built from that window reflects a recent slice, not the whole
                // run. Recording it as a diagnostic run would misrepresent it as a complete
                // result the way a fixed-count run's summary is - e.g. loss over the run would
                // silently mean "loss over the last minute or two". It's still computed and
                // shown live so the user has final numbers to read after hitting Stop; anyone
                // who wants a permanent record can use the fixed-count run instead.
                val rtts = state.results.filterIsInstance<PingProbeResult.Reply>().map { it.rttMs }
                val summary = summarizePing(PingTier.ICMP_SOCKET, state.results.size, rtts)
                _uiState.update { it.copy(isRunning = false, summary = summary) }
            } else {
                _uiState.update { it.copy(isRunning = false) }
            }
        }

        private suspend fun recordHistory(
            host: String,
            config: PingConfig,
            results: List<PingProbeResult>,
            summary: PingSummary,
            durationMillis: Long,
        ) {
            val payload = PingRunPayload(results.map { it.toDto() }, summary.toDto())
            diagnosticRunRepository.record(
                DiagnosticRunRecord(
                    toolType = DiagnosticToolType.PING.name,
                    target = host,
                    durationMillis = durationMillis,
                    summary = summary.toHistorySummary(),
                    parametersJson =
                        diagnosticRunParametersJson(
                            mapOf(
                                "count" to config.count.toString(),
                                "intervalMs" to config.intervalMs.toString(),
                                "timeoutMs" to config.timeoutMs.toString(),
                                "ttl" to config.ttl.toString(),
                                "payloadSize" to config.payloadSize.toString(),
                            ),
                        ),
                    resultJson = diagnosticHistoryJson.encodeToString(payload),
                ),
            )
        }

        private suspend fun resolveIpv4(host: String): Inet4Address? =
            withContext(Dispatchers.IO) {
                runCatching {
                    InetAddress.getAllByName(host).filterIsInstance<Inet4Address>().firstOrNull()
                }.getOrNull()
            }

        private fun ArrayDeque<PingProbeResult>.rttSamples(): List<Float> =
            filterIsInstance<PingProbeResult.Reply>().map { it.rttMs.toFloat() }

        override fun onCleared() {
            runJob?.cancel()
        }

        private companion object {
            // At the default 1-second probe interval this is a 60-second rolling window,
            // matching the signal meter's rolling-60-seconds convention (RollingLineChart's own
            // doc comment) - short enough that a multi-hour loop run's results/chart state stay
            // bounded, long enough to be a useful live trend.
            const val MAX_ROLLING_SAMPLES = 60
        }
    }
