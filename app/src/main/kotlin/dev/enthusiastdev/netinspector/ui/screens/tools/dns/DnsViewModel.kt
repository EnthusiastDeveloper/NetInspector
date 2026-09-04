package dev.enthusiastdev.netinspector.ui.screens.tools.dns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.enthusiastdev.netinspector.core.model.diagnostics.DnsQueryOutcome
import dev.enthusiastdev.netinspector.core.model.diagnostics.DnsRecordType
import dev.enthusiastdev.netinspector.data.diagnostics.dns.DnsRepository
import dev.enthusiastdev.netinspector.data.diagnostics.dns.RegisteredDnsServersRepository
import dev.enthusiastdev.netinspector.data.diagnostics.dns.queriedDnsServerOf
import dev.enthusiastdev.netinspector.data.diagnostics.dns.reverseDnsName
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
import java.net.InetAddress
import javax.inject.Inject

@HiltViewModel
class DnsViewModel
    @Inject
    constructor(
        private val dnsRepository: DnsRepository,
        private val registeredDnsServersDataSource: RegisteredDnsServersRepository,
        private val diagnosticRunRepository: DiagnosticRunRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(DnsUiState())
        val uiState: StateFlow<DnsUiState> = _uiState.asStateFlow()

        private var queryJob: Job? = null

        init {
            refreshRegisteredNetworks()
        }

        private fun refreshRegisteredNetworks() {
            _uiState.update { it.copy(registeredNetworks = registeredDnsServersDataSource.snapshot()) }
        }

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
                    refreshRegisteredNetworks()
                    _uiState.update {
                        it.copy(isRunning = true, outcome = null, queriedServer = null, activeTransportAtQuery = null)
                    }
                    val startedAtMillis = System.currentTimeMillis()
                    val server = state.customServer.trim()
                    val serverAddress =
                        if (server.isEmpty()) null else runCatching { InetAddress.getByName(server) }.getOrNull()
                    val outcome =
                        when {
                            server.isEmpty() -> dnsRepository.querySystemResolver(queryName, state.recordType)
                            serverAddress == null -> DnsQueryOutcome.Error("Could not resolve server \"$server\"")
                            else -> dnsRepository.queryServer(serverAddress, queryName, state.recordType)
                        }
                    // Only record what was actually queried - a server that failed to resolve
                    // was never sent anything, so there's nothing to show as "used."
                    val queriedServer =
                        if (server.isEmpty() || serverAddress != null) {
                            queriedDnsServerOf(serverAddress, _uiState.value.registeredNetworks)
                        } else {
                            null
                        }
                    _uiState.update {
                        it.copy(
                            isRunning = false,
                            outcome = outcome,
                            queriedServer = queriedServer,
                            activeTransportAtQuery = registeredDnsServersDataSource.activeTransport(),
                        )
                    }
                    diagnosticRunRepository.record(
                        DiagnosticRunRecord(
                            toolType = DiagnosticToolType.DNS_LOOKUP.name,
                            target = queryName,
                            durationMillis = System.currentTimeMillis() - startedAtMillis,
                            summary = outcome.toHistorySummary(),
                            parametersJson =
                                diagnosticRunParametersJson(
                                    mapOf(
                                        "recordType" to state.recordType.name,
                                        "server" to server.ifEmpty { "system" },
                                    ),
                                ),
                            resultJson = diagnosticHistoryJson.encodeToString(outcome.toRunPayload()),
                        ),
                    )
                }
        }

        override fun onCleared() {
            queryJob?.cancel()
        }
    }
