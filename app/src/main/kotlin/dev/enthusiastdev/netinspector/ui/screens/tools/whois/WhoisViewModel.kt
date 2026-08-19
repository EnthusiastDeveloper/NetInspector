package dev.enthusiastdev.netinspector.ui.screens.tools.whois

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.enthusiastdev.netinspector.data.diagnostics.whois.WhoisRepository
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
class WhoisViewModel
    @Inject
    constructor(
        private val whoisRepository: WhoisRepository,
        private val diagnosticRunRepository: DiagnosticRunRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(WhoisUiState())
        val uiState: StateFlow<WhoisUiState> = _uiState.asStateFlow()

        private var queryJob: Job? = null

        fun updateTarget(value: String) {
            _uiState.update { it.copy(target = value) }
        }

        fun runQuery() {
            val target = _uiState.value.target.trim()
            if (target.isEmpty()) return

            queryJob?.cancel()
            queryJob =
                viewModelScope.launch {
                    _uiState.update { it.copy(isRunning = true, outcome = null) }
                    val startedAtMillis = System.currentTimeMillis()
                    val outcome = whoisRepository.query(target)
                    _uiState.update { it.copy(isRunning = false, outcome = outcome) }
                    diagnosticRunRepository.record(
                        DiagnosticRunRecord(
                            toolType = DiagnosticToolType.WHOIS.name,
                            target = target,
                            durationMillis = System.currentTimeMillis() - startedAtMillis,
                            summary = outcome.toHistorySummary(),
                            parametersJson = diagnosticRunParametersJson(emptyMap()),
                            resultJson = diagnosticHistoryJson.encodeToString(outcome.toRunPayload()),
                        ),
                    )
                }
        }

        override fun onCleared() {
            queryJob?.cancel()
        }
    }
