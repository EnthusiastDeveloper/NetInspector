package dev.enthusiastdev.netinspector.ui.screens.tools.dns

import dev.enthusiastdev.netinspector.core.model.connection.NetworkTransport
import dev.enthusiastdev.netinspector.core.model.diagnostics.DnsQueryOutcome
import dev.enthusiastdev.netinspector.core.model.diagnostics.DnsRecordType
import dev.enthusiastdev.netinspector.core.model.diagnostics.QueriedDnsServer
import dev.enthusiastdev.netinspector.core.model.diagnostics.RegisteredDnsNetwork

data class DnsUiState(
    val name: String = "",
    val recordType: DnsRecordType = DnsRecordType.A,
    /** Blank means "system resolver" (design §9.4's default). */
    val customServer: String = "",
    val isRunning: Boolean = false,
    val outcome: DnsQueryOutcome? = null,
    /** What the OS has configured, per active network - refreshed on screen load and again at
     * the start of every query, independent of [outcome]. */
    val registeredNetworks: List<RegisteredDnsNetwork> = emptyList(),
    /** What this specific lookup actually queried. `null` until a query has run, or if the
     * custom-server field couldn't be resolved at all (so nothing was actually queried). */
    val queriedServer: QueriedDnsServer? = null,
    /** Which network was active when [queriedServer] was captured. */
    val activeTransportAtQuery: NetworkTransport? = null,
)
