package dev.enthusiastdev.netinspector.core.model.diagnostics

/** design §9.4 - the record types the DNS tool supports. */
enum class DnsRecordType(
    val code: Int,
) {
    A(1),
    NS(2),
    CNAME(5),
    SOA(6),
    PTR(12),
    MX(15),
    TXT(16),
    AAAA(28),
    SRV(33),
    ;

    companion object {
        fun fromCode(code: Int): DnsRecordType? = entries.firstOrNull { it.code == code }
    }
}

/** [data] is a human-readable rendering of the type-specific RDATA - an address for A/AAAA, a
 * hostname for CNAME/NS/PTR, `"priority host"` for MX, `"priority weight port target"` for SRV,
 * quoted strings for TXT, the seven SOA fields space-separated. Kept as one formatted string
 * rather than a per-type sealed hierarchy since the UI only ever displays it, never branches on
 * its structure. */
data class DnsRecord(
    val name: String,
    val type: DnsRecordType?,
    val rawTypeCode: Int,
    val ttlSeconds: Long,
    val data: String,
)

sealed interface DnsQueryOutcome {
    data class Success(
        val answers: List<DnsRecord>,
        val queryTimeMs: Double,
    ) : DnsQueryOutcome

    data class Error(
        val message: String,
    ) : DnsQueryOutcome
}
