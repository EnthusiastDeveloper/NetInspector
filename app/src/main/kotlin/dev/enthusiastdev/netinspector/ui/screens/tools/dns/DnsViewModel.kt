package dev.enthusiastdev.netinspector.ui.screens.tools.dns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.enthusiastdev.netinspector.core.model.connection.NetworkTransport
import dev.enthusiastdev.netinspector.core.model.diagnostics.DnsQueryOutcome
import dev.enthusiastdev.netinspector.core.model.diagnostics.DnsRecordType
import dev.enthusiastdev.netinspector.core.model.diagnostics.QueriedDnsServer
import dev.enthusiastdev.netinspector.core.model.diagnostics.RegisteredDnsNetwork
import dev.enthusiastdev.netinspector.data.diagnostics.dns.DnsRepository
import dev.enthusiastdev.netinspector.data.diagnostics.dns.RegisteredDnsServersRepository
import dev.enthusiastdev.netinspector.data.diagnostics.dns.firstRegisteredDnsServer
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
            val networks = registeredDnsServersDataSource.snapshot()
            val activeTransport = registeredDnsServersDataSource.activeTransport()
            val activeNetwork = networks.firstOrNull { it.transport == activeTransport }
            _uiState.update {
                it.copy(
                    registeredNetworks = networks,
                    activeTransport = activeTransport,
                    activePrivateDns = activeNetwork?.isPrivateDnsActive == true,
                    defaultServerHint = firstRegisteredDnsServer(activeTransport, networks)?.hostAddress,
                )
            }
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
                    val activeTransport = registeredDnsServersDataSource.activeTransport()
                    val dispatch =
                        dispatchQuery(
                            queryName = queryName,
                            type = state.recordType,
                            serverField = state.customServer.trim(),
                            networks = _uiState.value.registeredNetworks,
                            activeTransport = activeTransport,
                        )
                    _uiState.update {
                        it.copy(
                            isRunning = false,
                            outcome = dispatch.outcome,
                            queriedServer = dispatch.queriedServer,
                            activeTransportAtQuery = activeTransport,
                        )
                    }
                    recordRun(queryName, state.recordType, dispatch, System.currentTimeMillis() - startedAtMillis)
                }
        }

        /** Picks the destination and runs the query. A typed server is resolved and used
         * as-is; a blank field aims at the active network's first registered server (so the
         * "used for this lookup" panel can name a concrete destination) and only falls back to
         * the opaque system resolver when there is no such server or Private DNS is active. */
        private suspend fun dispatchQuery(
            queryName: String,
            type: DnsRecordType,
            serverField: String,
            networks: List<RegisteredDnsNetwork>,
            activeTransport: NetworkTransport?,
        ): QueryDispatch {
            val typedAddress =
                if (serverField.isEmpty()) null else runCatching { InetAddress.getByName(serverField) }.getOrNull()
            if (serverField.isNotEmpty() && typedAddress == null) {
                val error = DnsQueryOutcome.Error("Could not resolve server \"$serverField\"")
                return QueryDispatch(error, queriedServer = null, serverParamLabel = serverField)
            }
            val target = typedAddress ?: firstRegisteredDnsServer(activeTransport, networks)
            if (target == null) {
                return QueryDispatch(
                    dnsRepository.querySystemResolver(queryName, type),
                    QueriedDnsServer.SystemResolver,
                    "system",
                )
            }
            val autoSelected = typedAddress == null
            return QueryDispatch(
                outcome = dnsRepository.queryServer(target, queryName, type),
                queriedServer = queriedDnsServerOf(target, networks, autoSelected = autoSelected),
                serverParamLabel = if (autoSelected) "${target.hostAddress} (auto)" else serverField,
            )
        }

        private suspend fun recordRun(
            queryName: String,
            type: DnsRecordType,
            dispatch: QueryDispatch,
            durationMillis: Long,
        ) {
            diagnosticRunRepository.record(
                DiagnosticRunRecord(
                    toolType = DiagnosticToolType.DNS_LOOKUP.name,
                    target = queryName,
                    durationMillis = durationMillis,
                    summary = dispatch.outcome.toHistorySummary(),
                    parametersJson =
                        diagnosticRunParametersJson(
                            mapOf("recordType" to type.name, "server" to dispatch.serverParamLabel),
                        ),
                    resultJson = diagnosticHistoryJson.encodeToString(dispatch.outcome.toRunPayload()),
                ),
            )
        }

        /** What [dispatchQuery] decided and got back: the [outcome] to show, the [queriedServer]
         * indicator (null when nothing was actually sent), and the label to store in history. */
        private data class QueryDispatch(
            val outcome: DnsQueryOutcome,
            val queriedServer: QueriedDnsServer?,
            val serverParamLabel: String,
        )

        override fun onCleared() {
            queryJob?.cancel()
        }
    }
