package dev.enthusiastdev.netinspector.ui.screens.tools.ping

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.enthusiastdev.netinspector.core.common.icmp.summarizePing
import dev.enthusiastdev.netinspector.core.model.diagnostics.PingProbeResult
import dev.enthusiastdev.netinspector.core.model.diagnostics.PingTier
import dev.enthusiastdev.netinspector.data.diagnostics.icmp.PingConfig
import dev.enthusiastdev.netinspector.data.diagnostics.icmp.PingRepository
import dev.enthusiastdev.netinspector.ui.navigation.PingToolRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetAddress
import javax.inject.Inject

@HiltViewModel
class PingViewModel
    @Inject
    constructor(
        private val pingRepository: PingRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val _uiState =
            MutableStateFlow(PingUiState(target = savedStateHandle.toRoute<PingToolRoute>().target.orEmpty()))
        val uiState: StateFlow<PingUiState> = _uiState.asStateFlow()

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

                    _uiState.update {
                        it.copy(isRunning = true, results = emptyList(), summary = null, errorMessage = null)
                    }

                    val config = PingConfig()
                    val collected = mutableListOf<PingProbeResult>()
                    pingRepository.ping(address, config).collect { result ->
                        collected.add(result)
                        _uiState.update { it.copy(results = collected.toList()) }
                    }

                    val rtts = collected.filterIsInstance<PingProbeResult.Reply>().map { it.rttMs }
                    val summary = summarizePing(PingTier.ICMP_SOCKET, config.count, rtts)
                    _uiState.update { it.copy(isRunning = false, summary = summary) }
                }
        }

        fun stop() {
            runJob?.cancel()
            _uiState.update { it.copy(isRunning = false) }
        }

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
