package dev.enthusiastdev.netinspector.ui.screens.tools.whois

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.enthusiastdev.netinspector.data.diagnostics.whois.WhoisRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WhoisViewModel
    @Inject
    constructor(
        private val whoisRepository: WhoisRepository,
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
                    val outcome = whoisRepository.query(target)
                    _uiState.update { it.copy(isRunning = false, outcome = outcome) }
                }
        }

        override fun onCleared() {
            queryJob?.cancel()
        }
    }
