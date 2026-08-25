package dev.enthusiastdev.netinspector.history

/** design §10 - mirrors the tool names diagnostic runs are recorded under; kept as plain
 * `.name` strings on the [dev.enthusiastdev.netinspector.data.persistence.diagnostics.DiagnosticRunEntity]
 * row rather than a Room-mapped enum, since that module can't see this one (design §2.1). */
enum class DiagnosticToolType {
    PING,
    TRACEROUTE,
    PORT_SCAN,
    DNS_LOOKUP,
    HTTP_INSPECTOR,
    WHOIS,
    LAN_THROUGHPUT,
}
