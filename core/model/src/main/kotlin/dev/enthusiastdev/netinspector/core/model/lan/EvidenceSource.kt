package dev.enthusiastdev.netinspector.core.model.lan

/** design §8.3 - where one piece of evidence about a host came from. `GATEWAY` and `SELF`
 * are the two "known hosts" design §8.2 calls guaranteed-correct, not really probes.
 * `UPNP_HOSTS` (docs/device-identification-ideas.md C1) is the router reporting *another*
 * host's entry from its own table, not that host's own direct response - grouped with the
 * other announcement-only sources below, same as `NETBIOS` despite also carrying a real MAC.
 * `SNMP` (B1) and `TLS` (B3) are direct per-host responses, but Stage C only ever queries
 * already-`CONFIRMED` hosts, so neither needs to affect [confidenceOf] either. */
enum class EvidenceSource { ICMP, TCP_CONNECT, MDNS, SSDP, NETBIOS, SNMP, TLS, UPNP_HOSTS, REVERSE_DNS, GATEWAY, SELF }

/** design §8.3 - sources whose mere presence proves a host is actually there, as opposed to
 * merely having advertised itself. Backs [dev.enthusiastdev.netinspector.core.model.lan.confidenceOf]. */
val DIRECT_EVIDENCE_SOURCES: Set<EvidenceSource> =
    setOf(EvidenceSource.ICMP, EvidenceSource.TCP_CONNECT, EvidenceSource.GATEWAY, EvidenceSource.SELF)
