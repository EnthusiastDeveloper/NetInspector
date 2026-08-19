package dev.enthusiastdev.netinspector.core.model.lan

/** design §8.3 - where one piece of evidence about a host came from. `GATEWAY` and `SELF`
 * are the two "known hosts" design §8.2 calls guaranteed-correct, not really probes. */
enum class EvidenceSource { ICMP, TCP_CONNECT, MDNS, SSDP, NETBIOS, REVERSE_DNS, GATEWAY, SELF }

/** design §8.3 - sources whose mere presence proves a host is actually there, as opposed to
 * merely having advertised itself. Backs [dev.enthusiastdev.netinspector.core.model.lan.confidenceOf]. */
val DIRECT_EVIDENCE_SOURCES: Set<EvidenceSource> =
    setOf(EvidenceSource.ICMP, EvidenceSource.TCP_CONNECT, EvidenceSource.GATEWAY, EvidenceSource.SELF)
