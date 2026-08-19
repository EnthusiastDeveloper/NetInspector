package dev.enthusiastdev.netinspector.ui.screens.tools.portscanner

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.enthusiastdev.netinspector.core.model.diagnostics.PortScanFinding
import dev.enthusiastdev.netinspector.core.model.diagnostics.PortSelection
import dev.enthusiastdev.netinspector.data.diagnostics.portscan.PortScanEvent
import dev.enthusiastdev.netinspector.data.diagnostics.portscan.PortScannerRepository
import dev.enthusiastdev.netinspector.data.persistence.diagnostics.DiagnosticRunRecord
import dev.enthusiastdev.netinspector.data.persistence.diagnostics.DiagnosticRunRepository
import dev.enthusiastdev.netinspector.data.persistence.preferences.AppSettingsRepository
import dev.enthusiastdev.netinspector.history.DiagnosticToolType
import dev.enthusiastdev.netinspector.history.PortScanRunPayload
import dev.enthusiastdev.netinspector.history.diagnosticHistoryJson
import dev.enthusiastdev.netinspector.history.diagnosticRunParametersJson
import dev.enthusiastdev.netinspector.history.toDto
import dev.enthusiastdev.netinspector.ui.navigation.PortScannerToolRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import java.net.Inet4Address
import java.net.InetAddress
import javax.inject.Inject

@HiltViewModel
class PortScannerViewModel
    @Inject
    constructor(
        private val portScannerRepository: PortScannerRepository,
        private val diagnosticRunRepository: DiagnosticRunRepository,
        private val appSettingsRepository: AppSettingsRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val _uiState =
            MutableStateFlow(
                PortScannerUiState(target = savedStateHandle.toRoute<PortScannerToolRoute>().target.orEmpty()),
            )
        val uiState: StateFlow<PortScannerUiState> = _uiState.asStateFlow()

        // design §8 settings - the port scanner's default preset (see
        // AppSettingsRepository.defaultPortSelection). Read once on entry rather than kept live:
        // this only seeds the form's starting selection, so a setting changed mid-session
        // shouldn't yank the user's in-progress choice out from under them.
        init {
            viewModelScope.launch {
                val default = appSettingsRepository.defaultPortSelection.first()
                _uiState.update {
                    it.copy(
                        selection = default,
                        customStart = (default as? PortSelection.Custom)?.start?.toString() ?: it.customStart,
                        customEnd = (default as? PortSelection.Custom)?.end?.toString() ?: it.customEnd,
                    )
                }
            }
        }

        private var scanJob: Job? = null

        fun updateTarget(value: String) {
            _uiState.update { it.copy(target = value) }
        }

        fun updateSelection(selection: PortSelection) {
            _uiState.update { it.copy(selection = selection) }
        }

        fun updateCustomStart(value: String) {
            _uiState.update { it.copy(customStart = value, selection = it.customSelectionOrKeep(value, it.customEnd)) }
        }

        fun updateCustomEnd(value: String) {
            _uiState.update { it.copy(customEnd = value, selection = it.customSelectionOrKeep(it.customStart, value)) }
        }

        private fun PortScannerUiState.customSelectionOrKeep(
            start: String,
            end: String,
        ): PortSelection {
            val startPort = start.toIntOrNull()
            val endPort = end.toIntOrNull()
            return if (startPort != null && endPort != null) PortSelection.Custom(startPort, endPort) else selection
        }

        fun start() {
            val host = _uiState.value.target.trim()
            if (host.isEmpty()) return

            scanJob?.cancel()
            scanJob =
                viewModelScope.launch {
                    val address = resolveIpv4(host)
                    if (address == null) {
                        _uiState.update { it.copy(errorMessage = "Could not resolve \"$host\" to an IPv4 address") }
                        return@launch
                    }
                    val ports = _uiState.value.selection.resolve()

                    _uiState.update {
                        it.copy(isRunning = true, findings = emptyList(), progress = null, errorMessage = null)
                    }

                    val startedAtMillis = System.currentTimeMillis()
                    portScannerRepository.scan(address, ports).collect { event ->
                        when (event) {
                            is PortScanEvent.Progress -> _uiState.update { it.copy(progress = event.progress) }
                            is PortScanEvent.Found ->
                                _uiState.update { it.copy(findings = it.findings + event.finding) }
                        }
                    }

                    _uiState.update { it.copy(isRunning = false) }
                    recordHistory(
                        host,
                        _uiState.value.selection,
                        ports.size,
                        _uiState.value.findings,
                        System.currentTimeMillis() - startedAtMillis,
                    )
                }
        }

        fun stop() {
            scanJob?.cancel()
            _uiState.update { it.copy(isRunning = false) }
        }

        private suspend fun recordHistory(
            host: String,
            selection: PortSelection,
            portsScanned: Int,
            findings: List<PortScanFinding>,
            durationMillis: Long,
        ) {
            diagnosticRunRepository.record(
                DiagnosticRunRecord(
                    toolType = DiagnosticToolType.PORT_SCAN.name,
                    target = host,
                    durationMillis = durationMillis,
                    summary = "${findings.size} open of $portsScanned scanned",
                    parametersJson = diagnosticRunParametersJson(mapOf("preset" to selection.kind.name)),
                    resultJson =
                        diagnosticHistoryJson.encodeToString(
                            PortScanRunPayload(portsScanned, findings.map { it.toDto() }),
                        ),
                ),
            )
        }

        private suspend fun resolveIpv4(host: String): Inet4Address? =
            withContext(Dispatchers.IO) {
                runCatching {
                    InetAddress.getAllByName(host).filterIsInstance<Inet4Address>().firstOrNull()
                }.getOrNull()
            }

        override fun onCleared() {
            scanJob?.cancel()
        }
    }
