package dev.enthusiastdev.netinspector.core.model.diagnostics

/**
 * design plan Phase 7's blocking gate, resolved: the Phase 5 fallback set (design §8.2 pass 3)
 * was picked for LAN *discovery* - proving a host with ICMP disabled is still alive - not for a
 * user-driven scan of a chosen target, so it isn't reused here. This is a standard "commonly
 * scanned" service set (the same shape as `nmap --top-ports`) covering the protocols a network
 * diagnostic tool's users actually go looking for: remote access, file sharing, mail, web,
 * databases and common LAN device services - independent of `:data:lan`'s `ExtendedPortProbe`
 * set, since the module graph (design §2.1) forbids one `:data:*` module depending on another
 * and the two lists serve different purposes anyway (enumeration of a known-confirmed host vs.
 * scanning an arbitrary target).
 */
object CommonPorts {
    private const val PORTS_CSV =
        "21,22,23,25,53,80,81,88,110,111,135,139,143,179,389,443,445,465,514," +
            "515,548,554,587,631,636,993,995,1080,1433,1723,2049,3000,3306,3389,5000," +
            "5060,5357,5432,5900,5985,6379,8000,8008,8080,8081,8443,8888,9000,9100," +
            "27017,32400,62078"
    val LIST: List<Int> = PORTS_CSV.split(",").map { it.toInt() }
}

enum class PortScanPresetKind { COMMON, WELL_KNOWN, ALL, CUSTOM }

/** design §9.5 - "configurable range or preset service set." [Custom] carries an explicit
 * inclusive range rather than a free-form list - matches how the UI actually collects it (a
 * start/end pair), and every preset ultimately resolves to a `start..end` sweep or [CommonPorts]
 * before scanning. */
sealed interface PortSelection {
    data object Common : PortSelection

    data object WellKnown : PortSelection

    data object All : PortSelection

    data class Custom(
        val start: Int,
        val end: Int,
    ) : PortSelection

    val kind: PortScanPresetKind
        get() =
            when (this) {
                Common -> PortScanPresetKind.COMMON
                WellKnown -> PortScanPresetKind.WELL_KNOWN
                All -> PortScanPresetKind.ALL
                is Custom -> PortScanPresetKind.CUSTOM
            }

    fun resolve(): List<Int> =
        when (this) {
            Common -> CommonPorts.LIST
            WellKnown -> (1..1024).toList()
            All -> (1..65535).toList()
            is Custom -> if (start <= end) (start..end).toList() else emptyList()
        }
}

data class PortScanProgress(
    val scanned: Int,
    val total: Int,
)

data class PortScanFinding(
    val port: Int,
    val banner: String?,
)
