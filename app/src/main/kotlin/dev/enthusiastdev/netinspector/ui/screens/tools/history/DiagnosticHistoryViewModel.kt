package dev.enthusiastdev.netinspector.ui.screens.tools.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.enthusiastdev.netinspector.data.persistence.diagnostics.DiagnosticRunRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** design §11.1 History - "past diagnostic runs." [DiagnosticRunRepository.recent] already
 * returns full rows (including `resultJson`/`parametersJson`), so unlike a typical list/detail
 * pair there is no separate per-id fetch: the list this exposes doubles as the detail source,
 * the same way [ScanHistoryViewModel]'s known-AP list backs its own detail pane. */
@HiltViewModel
class DiagnosticHistoryViewModel
    @Inject
    constructor(
        diagnosticRunRepository: DiagnosticRunRepository,
    ) : ViewModel() {
        val uiState: StateFlow<DiagnosticHistoryUiState> =
            diagnosticRunRepository
                .recent()
                .map { DiagnosticHistoryUiState(runs = it) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiagnosticHistoryUiState())
    }
