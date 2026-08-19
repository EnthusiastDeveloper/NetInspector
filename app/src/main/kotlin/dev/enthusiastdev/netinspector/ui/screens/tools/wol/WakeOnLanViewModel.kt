package dev.enthusiastdev.netinspector.ui.screens.tools.wol

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.enthusiastdev.netinspector.data.diagnostics.wol.WakeOnLanRepository
import dev.enthusiastdev.netinspector.data.persistence.wol.SavedWolTarget
import dev.enthusiastdev.netinspector.data.persistence.wol.SavedWolTargetRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WakeOnLanViewModel
    @Inject
    constructor(
        private val wakeOnLanRepository: WakeOnLanRepository,
        private val savedWolTargetRepository: SavedWolTargetRepository,
    ) : ViewModel() {
        private val formState = MutableStateFlow(WakeOnLanUiState())

        val uiState: StateFlow<WakeOnLanUiState> =
            combine(formState, savedWolTargetRepository.observeAll()) { form, saved ->
                form.copy(savedTargets = saved)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WakeOnLanUiState())

        fun updateLabel(value: String) {
            formState.update { it.copy(label = value) }
        }

        fun updateMac(value: String) {
            formState.update { it.copy(mac = value) }
        }

        fun updateBroadcastAddress(value: String) {
            formState.update { it.copy(broadcastAddress = value) }
        }

        fun wake(
            mac: String,
            broadcastAddress: String,
        ) {
            viewModelScope.launch {
                val sent = wakeOnLanRepository.wake(mac, broadcastAddress)
                val message = if (sent) "Magic packet sent to $mac" else "Could not send: invalid MAC address"
                formState.update { it.copy(lastResultMessage = message) }
            }
        }

        fun saveTarget() {
            val state = formState.value
            if (state.label.isBlank() || state.mac.isBlank()) return
            viewModelScope.launch {
                savedWolTargetRepository.save(state.label.trim(), state.mac.trim(), state.broadcastAddress.trim())
                formState.update { it.copy(label = "", mac = "") }
            }
        }

        fun deleteTarget(target: SavedWolTarget) {
            viewModelScope.launch { savedWolTargetRepository.delete(target) }
        }
    }
