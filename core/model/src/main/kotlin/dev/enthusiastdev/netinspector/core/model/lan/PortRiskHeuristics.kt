package dev.enthusiastdev.netinspector.core.model.lan

/**
 * Turns an open port from the extended port probe (design §8.2 Stage C) into a short,
 * evidence-anchored note when that port is a well-known unencrypted or historically
 * vulnerable protocol - the same "no field without a basis" convention [DeviceHintHeuristics]
 * follows. `null` means the port carries no notable risk beyond simply being open. Pure and
 * unit-tested, same shape as [deviceHintFor].
 */
fun portRiskNote(port: Int): String? = PORT_RISKS[port]?.note

/**
 * The severity tier behind [portRiskNote] - the network hygiene score (improvement idea #1)
 * aggregates over this rather than the free-text note, so the score can't drift out of sync
 * with the wording. `null` (no entry) is not a tier: it means [port] carries no notable risk,
 * the same convention [portRiskNote] follows.
 */
fun portRiskSeverity(port: Int): PortRiskSeverity? = PORT_RISKS[port]?.severity

/**
 * Ordered most to least severe. [CRITICAL] means the protocol is commonly reachable with no
 * real authentication barrier at all (an open door); [HIGH] means credentials or traffic are
 * exposed in the clear, or the protocol has a known break, but reaching it still requires
 * knowing/guessing something; [MODERATE] means the exposure is real but conditional on
 * misconfiguration or on an attacker already holding valid credentials.
 */
enum class PortRiskSeverity { CRITICAL, HIGH, MODERATE }

private data class PortRisk(
    val note: String,
    val severity: PortRiskSeverity,
)

private val PORT_RISKS: Map<Int, PortRisk> =
    mapOf(
        21 to PortRisk("FTP - credentials and files sent in plaintext", PortRiskSeverity.HIGH),
        23 to
            PortRisk(
                "Telnet - unencrypted remote access, credentials sent in plaintext",
                PortRiskSeverity.CRITICAL,
            ),
        25 to PortRisk("SMTP - often unauthenticated relay if misconfigured", PortRiskSeverity.MODERATE),
        110 to PortRisk("POP3 - unencrypted mail retrieval", PortRiskSeverity.MODERATE),
        143 to PortRisk("IMAP - unencrypted mail access", PortRiskSeverity.MODERATE),
        1723 to PortRisk("PPTP - VPN protocol with known cryptographic weaknesses", PortRiskSeverity.HIGH),
        3389 to PortRisk("RDP - a common target for automated login attempts", PortRiskSeverity.HIGH),
        5900 to
            PortRisk(
                "VNC - often unauthenticated or weakly authenticated by default",
                PortRiskSeverity.CRITICAL,
            ),
    )
