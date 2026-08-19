package dev.enthusiastdev.netinspector.ui.screens.tools.traceroute

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.enthusiastdev.netinspector.core.model.diagnostics.TracerouteHop
import dev.enthusiastdev.netinspector.data.diagnostics.traceroute.TracerouteConfig
import dev.enthusiastdev.netinspector.data.diagnostics.traceroute.TracerouteRepository
import dev.enthusiastdev.netinspector.data.persistence.diagnostics.DiagnosticRunRecord
import dev.enthusiastdev.netinspector.data.persistence.diagnostics.DiagnosticRunRepository
import dev.enthusiastdev.netinspector.history.DiagnosticToolType
import dev.enthusiastdev.netinspector.history.diagnosticHistoryJson
import dev.enthusiastdev.netinspector.history.diagnosticRunParametersJson
import dev.enthusiastdev.netinspector.history.toHistorySummary
import dev.enthusiastdev.netinspector.history.toRunPayload
import dev.enthusiastdev.netinspector.ui.navigation.TracerouteToolRoute
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
class TracerouteViewModel
    @Inject
    constructor(
        private val tracerouteRepository: TracerouteRepository,
        private val diagnosticRunRepository: DiagnosticRunRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val _uiState =
            MutableStateFlow(
                TracerouteUiState(target = savedStateHandle.toRoute<TracerouteToolRoute>().target.orEmpty()),
            )
        val uiState: StateFlow<TracerouteUiState> = _uiState.asStateFlow()

        private var runJob: Job? = null

        fun updateTarget(value: String) {
            _uiState.update { it.copy(target = value) }
        }

        fun start() {
            val host = _uiState.value.target.trim()
            if (host.isEmpty()) return

            runJob?.cancel()
            runJob =
                viewModelScope.launch {
                    val address = resolveIpv4(host)
                    if (address == null) {
                        _uiState.update { it.copy(errorMessage = "Could not resolve \"$host\" to an IPv4 address") }
                        return@launch
                    }

                    _uiState.update { it.copy(isRunning = true, hops = emptyList(), tier = null, errorMessage = null) }

                    val startedAtMillis = System.currentTimeMillis()
                    tracerouteRepository.traceroute(address, TracerouteConfig()).collect { hop ->
                        _uiState.update { current ->
                            current.copy(hops = current.hops.replacingByTtl(hop), tier = hop.probes.firstOrNull()?.tier)
                        }
                    }

                    _uiState.update { it.copy(isRunning = false) }
                    recordHistory(host, _uiState.value.hops, System.currentTimeMillis() - startedAtMillis)
                }
        }

        fun stop() {
            runJob?.cancel()
            _uiState.update { it.copy(isRunning = false) }
        }

        private suspend fun recordHistory(
            host: String,
            hops: List<TracerouteHop>,
            durationMillis: Long,
        ) {
            if (hops.isEmpty()) return
            diagnosticRunRepository.record(
                DiagnosticRunRecord(
                    toolType = DiagnosticToolType.TRACEROUTE.name,
                    target = host,
                    durationMillis = durationMillis,
                    summary = hops.toHistorySummary(),
                    parametersJson =
                        diagnosticRunParametersJson(mapOf("maxHops" to TracerouteConfig().maxHops.toString())),
                    resultJson = diagnosticHistoryJson.encodeToString(hops.toRunPayload()),
                ),
            )
        }

        private fun List<TracerouteHop>.replacingByTtl(hop: TracerouteHop): List<TracerouteHop> =
            if (any { it.ttl == hop.ttl }) map { if (it.ttl == hop.ttl) hop else it } else this + hop

        private suspend fun resolveIpv4(host: String): Inet4Address? =
            withContext(Dispatchers.IO) {
                runCatching {
                    InetAddress.getAllByName(host).filterIsInstance<Inet4Address>().firstOrNull()
                }.getOrNull()
            }

        override fun onCleared() {
            runJob?.cancel()
        }
    }
