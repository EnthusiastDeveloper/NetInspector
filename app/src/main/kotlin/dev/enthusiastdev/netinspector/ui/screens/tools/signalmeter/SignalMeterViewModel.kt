package dev.enthusiastdev.netinspector.ui.screens.tools.signalmeter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.enthusiastdev.netinspector.core.model.connection.ConnectionSnapshot
import dev.enthusiastdev.netinspector.data.wifi.ConnectionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RssiSample(
    val elapsedMs: Long,
    val rssiDbm: Int,
)

data class SignalMeterUiState(
    val snapshot: ConnectionSnapshot? = null,
    val history: List<RssiSample> = emptyList(),
)

/** design §9.6 - "Consumes no scan budget": purely passive, riding the same `NetworkCallback`
 * stream the Connection dashboard's gauge uses (design §5.1), never an active Wi-Fi scan. */
@HiltViewModel
class SignalMeterViewModel
    @Inject
    constructor(
        private val connectionRepository: ConnectionRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(SignalMeterUiState())
        val uiState: StateFlow<SignalMeterUiState> = _uiState.asStateFlow()

        private val startElapsedMs = System.currentTimeMillis()

        init {
            viewModelScope.launch {
                connectionRepository.connectionSnapshot.collect { snapshot ->
                    _uiState.update { current ->
                        val rssiDbm = snapshot?.rssiDbm
                        val history =
                            if (rssiDbm != null) {
                                val now = System.currentTimeMillis() - startElapsedMs
                                (current.history + RssiSample(now, rssiDbm))
                                    .filter { it.elapsedMs >= now - WINDOW_MS }
                            } else {
                                current.history
                            }
                        current.copy(snapshot = snapshot, history = history)
                    }
                }
            }
        }

        private companion object {
            const val WINDOW_MS = 60_000L
        }
    }
