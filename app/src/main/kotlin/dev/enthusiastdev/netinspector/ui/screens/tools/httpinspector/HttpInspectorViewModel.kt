package dev.enthusiastdev.netinspector.ui.screens.tools.httpinspector

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.enthusiastdev.netinspector.data.diagnostics.httpinspect.HttpInspectorRepository
import dev.enthusiastdev.netinspector.data.persistence.diagnostics.DiagnosticRunRecord
import dev.enthusiastdev.netinspector.data.persistence.diagnostics.DiagnosticRunRepository
import dev.enthusiastdev.netinspector.history.DiagnosticToolType
import dev.enthusiastdev.netinspector.history.diagnosticHistoryJson
import dev.enthusiastdev.netinspector.history.diagnosticRunParametersJson
import dev.enthusiastdev.netinspector.history.toHistorySummary
import dev.enthusiastdev.netinspector.history.toRunPayload
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import javax.inject.Inject

@HiltViewModel
class HttpInspectorViewModel
    @Inject
    constructor(
        private val httpInspectorRepository: HttpInspectorRepository,
        private val diagnosticRunRepository: DiagnosticRunRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(HttpInspectorUiState())
        val uiState: StateFlow<HttpInspectorUiState> = _uiState.asStateFlow()

        private var inspectJob: Job? = null

        fun updateUrl(value: String) {
            _uiState.update { it.copy(url = value) }
        }

        fun runInspection() {
            val url = _uiState.value.url.trim()
            if (url.isEmpty()) return

            inspectJob?.cancel()
            inspectJob =
                viewModelScope.launch {
                    _uiState.update { it.copy(isRunning = true, outcome = null) }
                    val startedAtMillis = System.currentTimeMillis()
                    val outcome = httpInspectorRepository.inspect(url)
                    _uiState.update { it.copy(isRunning = false, outcome = outcome) }
                    diagnosticRunRepository.record(
                        DiagnosticRunRecord(
                            toolType = DiagnosticToolType.HTTP_INSPECTOR.name,
                            target = url,
                            durationMillis = System.currentTimeMillis() - startedAtMillis,
                            summary = outcome.toHistorySummary(),
                            parametersJson = diagnosticRunParametersJson(emptyMap()),
                            resultJson = diagnosticHistoryJson.encodeToString(outcome.toRunPayload()),
                        ),
                    )
                }
        }

        override fun onCleared() {
            inspectJob?.cancel()
        }
    }
