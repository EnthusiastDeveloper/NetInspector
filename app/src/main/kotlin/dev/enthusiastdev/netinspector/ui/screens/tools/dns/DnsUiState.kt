package dev.enthusiastdev.netinspector.ui.screens.tools.dns

import dev.enthusiastdev.netinspector.core.model.diagnostics.DnsQueryOutcome
import dev.enthusiastdev.netinspector.core.model.diagnostics.DnsRecordType

data class DnsUiState(
    val name: String = "",
    val recordType: DnsRecordType = DnsRecordType.A,
    /** Blank means "system resolver" (design §9.4's default). */
    val customServer: String = "",
    val isRunning: Boolean = false,
    val outcome: DnsQueryOutcome? = null,
)
