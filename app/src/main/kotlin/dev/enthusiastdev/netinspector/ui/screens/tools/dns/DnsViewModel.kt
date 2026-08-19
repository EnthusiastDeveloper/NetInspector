package dev.enthusiastdev.netinspector.ui.screens.tools.dns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.enthusiastdev.netinspector.core.model.diagnostics.DnsQueryOutcome
import dev.enthusiastdev.netinspector.core.model.diagnostics.DnsRecordType
import dev.enthusiastdev.netinspector.data.diagnostics.dns.DnsRepository
import dev.enthusiastdev.netinspector.data.diagnostics.dns.reverseDnsName
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.InetAddress
import javax.inject.Inject

@HiltViewModel
class DnsViewModel
    @Inject
    constructor(
        private val dnsRepository: DnsRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(DnsUiState())
        val uiState: StateFlow<DnsUiState> = _uiState.asStateFlow()

        private var queryJob: Job? = null

        fun updateName(value: String) {
            _uiState.update { it.copy(name = value) }
        }

        fun updateRecordType(type: DnsRecordType) {
            _uiState.update { it.copy(recordType = type) }
        }

        fun updateCustomServer(value: String) {
            _uiState.update { it.copy(customServer = value) }
        }

        fun runQuery() {
            val state = _uiState.value
            val name = state.name.trim()
            if (name.isEmpty()) return
            // design §9.4 - "Reverse lookups build the in-addr.arpa name and issue a PTR query,"
            // so a PTR lookup takes the user's entry as an IPv4 address, not a hostname.
            val queryName = if (state.recordType == DnsRecordType.PTR) reverseDnsName(name) else name

            queryJob?.cancel()
            queryJob =
                viewModelScope.launch {
                    _uiState.update { it.copy(isRunning = true, outcome = null) }
                    val server = state.customServer.trim()
                    val outcome =
                        if (server.isEmpty()) {
                            dnsRepository.querySystemResolver(queryName, state.recordType)
                        } else {
                            val serverAddress = runCatching { InetAddress.getByName(server) }.getOrNull()
                            if (serverAddress == null) {
                                DnsQueryOutcome.Error("Could not resolve server \"$server\"")
                            } else {
                                dnsRepository.queryServer(serverAddress, queryName, state.recordType)
                            }
                        }
                    _uiState.update { it.copy(isRunning = false, outcome = outcome) }
                }
        }

        override fun onCleared() {
            queryJob?.cancel()
        }
    }
