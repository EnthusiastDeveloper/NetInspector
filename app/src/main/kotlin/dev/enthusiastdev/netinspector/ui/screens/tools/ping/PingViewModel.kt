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

        // Bounded so an hours-long loop run can't grow the displayed log/chart without limit,
        // oldest probes fall off this window - but see sessionRtts/sessionSent below, which are
        // NOT bounded the same way, since the final summary needs the whole session's numbers,
        // not just whatever's currently on screen.
        private val window = ArrayDeque<PingProbeResult>()

        // A continuous (loop-mode) session's full-run accounting, kept separate from `window`
        // above: a raw RTT double is a few bytes, so even a multi-hour session here is trivial
        // memory, unlike holding every full PingProbeResult (and unlike `window`, this is never
        // trimmed while the session is live). Survives across Stop/Ping cycles on the same
        // target - see the continuingSession check in start() - and only resets when the target
        // actually changes, so a brief pause doesn't throw away an otherwise-continuous session's
        // numbers.
        private var sessionTarget: String? = null
        private var sessionSent: Int = 0
        private val sessionRtts = mutableListOf<Double>()

        fun updateTarget(value: String) {
            _uiState.update { it.copy(target = value) }
        }

        fun setLoopMode(enabled: Boolean) {
            if (_uiState.value.isRunning || enabled == _uiState.value.isLoopMode) return
            // Clears the *display* only (window/results/rttSamples), not sessionTarget/
            // sessionSent/sessionRtts: a mode toggle hasn't run anything, so it must not erase
            // an accumulating loop session's totals, only the stale chart/log left over from
            // whatever last ran under the other mode.
            window.clear()
            _uiState.update { it.copy(isLoopMode = enabled, results = emptyList(), rttSamples = emptyList()) }
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

                    // Fixed-count runs are always independent one-offs, same as before. A loop
                    // run continues the existing session (window included, so the visible log/
                    // chart keeps growing too) when it targets the same host as the session
                    // already in progress; any other case - a new target, or the first loop run
                    // - starts a clean session.
                    val continuingSession = loop && host == sessionTarget
                    if (loop) {
                        if (!continuingSession) {
                            sessionTarget = host
                            sessionSent = 0
                            sessionRtts.clear()
                            window.clear()
                        }
                    } else {
                        // A fixed-count run always invalidates any in-progress loop session,
                        // not just its window: leaving sessionTarget/sessionSent/sessionRtts
                        // untouched here let a later loop run on the same host see
                        // `host == sessionTarget` and wrongly "continue" a session whose window
                        // had actually been overwritten by this intervening fixed-count run's
                        // own results.
                        window.clear()
                        sessionTarget = null
                        sessionSent = 0
                        sessionRtts.clear()
                    }

                    _uiState.update {
                        it.copy(
                            isRunning = true,
                            results = window.toList(),
                            rttSamples = window.rttSamples(),
                            summary = if (continuingSession) it.summary else null,
                            errorMessage = null,
                        )
                    }

                    // Loop mode ("ping -t" style) reuses the same flow-based API with an
                    // unbounded count instead of adding a second streaming entry point to
                    // PingRepository - stop() below cancels runJob to end it, the same
                    // cancellation path a fixed-count run's Stop button always used.
                    val config = if (loop) PingConfig(count = null) else PingConfig()
                    val startedAtMillis = System.currentTimeMillis()
                    // Unbounded, unlike `window` (which is deliberately capped at
                    // MAX_ROLLING_SAMPLES for display): the completion summary/history below
                    // must reflect every probe this run actually sent, not just whatever's
                    // still in the display window, the same reasoning sessionRtts exists for
                    // loop mode's own Stop-time summary.
                    val runResults = mutableListOf<PingProbeResult>()
                    pingRepository.ping(address, config).collect { result ->
                        window.addLast(result)
                        if (window.size > MAX_ROLLING_SAMPLES) window.removeFirst()
                        runResults.add(result)
                        if (loop) {
                            sessionSent += 1
                            (result as? PingProbeResult.Reply)?.let { sessionRtts.add(it.rttMs) }
                        }
                        _uiState.update { it.copy(results = window.toList(), rttSamples = window.rttSamples()) }
                    }

                    // Only reached when the flow completes on its own, i.e. the fixed-count
                    // path - loop mode's unbounded count never gets here in practice, stop()
                    // cancels runJob, which interrupts the collect above instead, and stop()
                    // does its own summary finalization for that case from the session totals.
                    val rtts = runResults.filterIsInstance<PingProbeResult.Reply>().map { it.rttMs }
                    val summary = summarizePing(PingTier.ICMP_SOCKET, runResults.size, rtts)
                    _uiState.update { it.copy(isRunning = false, summary = summary) }

                    recordHistory(host, config, runResults, summary, System.currentTimeMillis() - startedAtMillis)
                }
        }

        fun stop() {
            val state = _uiState.value
            runJob?.cancel()
            if (state.isLoopMode && state.isRunning) {
                // Loop mode is never persisted to diagnostic_run history, unlike a fixed-count
                // run: it has no natural end point to record against, and an in-progress session
                // isn't a "result" the way a completed fixed run is. It's still computed here
                // from the full session totals (sessionSent/sessionRtts, not the bounded display
                // window) and shown live so the user has accurate final numbers to read after
                // hitting Stop; anyone who wants a permanent record can use a fixed-count run.
                val summary = summarizePing(PingTier.ICMP_SOCKET, sessionSent, sessionRtts.toList())
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
                                "count" to (config.count?.toString() ?: "unbounded"),
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
