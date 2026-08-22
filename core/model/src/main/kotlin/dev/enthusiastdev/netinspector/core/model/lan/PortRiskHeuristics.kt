package dev.enthusiastdev.netinspector.core.model.lan

/**
 * The full entry behind [portRiskNote]/[portRiskSeverity]/[portRiskRemediation] - a single
 * lookup for UI code (the hygiene score's explainer and remediation list, improvement ideas
 * #2/#3) that needs more than one field, rather than three separate map lookups for the same
 * port. `null` means [port] carries no notable risk.
 */
fun portRisk(port: Int): PortRisk? = PORT_RISKS[port]

/**
 * Turns an open port from the extended port probe (design §8.2 Stage C) into a short,
 * evidence-anchored note when that port is a well-known unencrypted or historically
 * vulnerable protocol - the same "no field without a basis" convention [DeviceHintHeuristics]
 * follows. `null` means the port carries no notable risk beyond simply being open. Pure and
 * unit-tested, same shape as [deviceHintFor].
 */
fun portRiskNote(port: Int): String? = portRisk(port)?.let { "${it.protocol} - ${it.reason}" }

/**
 * The severity tier behind [portRiskNote] - the network hygiene score (improvement idea #1)
 * aggregates over this rather than the free-text note, so the score can't drift out of sync
 * with the wording. `null` (no entry) is not a tier: it means [port] carries no notable risk,
 * the same convention [portRiskNote] follows.
 */
fun portRiskSeverity(port: Int): PortRiskSeverity? = portRisk(port)?.severity

/**
 * A concrete suggested fix for a flagged port (improvement idea #3 - "actionable remediation
 * list"), rather than leaving the user to infer one from [portRiskNote]'s explanation of the
 * problem alone. `null` follows the same "no entry, no risk" convention as the other two
 * accessors.
 */
fun portRiskRemediation(port: Int): String? = portRisk(port)?.remediation

/**
 * Every currently-flagged port, sorted for stable display order - backs the hygiene score's
 * methodology explainer (improvement idea #2), which lists exactly what this file checks
 * rather than leaving the score's basis opaque. Exposes [PortRisk] read-only rather than the
 * backing map itself, so [PORT_RISKS] stays the single source of truth callers can't mutate.
 */
fun allFlaggedPorts(): List<Pair<Int, PortRisk>> = PORT_RISKS.entries.sortedBy { it.key }.map { it.key to it.value }

/**
 * Ordered most to least severe. [CRITICAL] means the protocol is commonly reachable with no
 * real authentication barrier at all (an open door); [HIGH] means credentials or traffic are
 * exposed in the clear, or the protocol has a known break, but reaching it still requires
 * knowing/guessing something; [MODERATE] means the exposure is real but conditional on
 * misconfiguration or on an attacker already holding valid credentials.
 */
enum class PortRiskSeverity { CRITICAL, HIGH, MODERATE }

data class PortRisk(
    val protocol: String,
    val reason: String,
    val severity: PortRiskSeverity,
    val remediation: String,
)

private val PORT_RISKS: Map<Int, PortRisk> =
    mapOf(
        21 to
            PortRisk(
                protocol = "FTP",
                reason = "credentials and files sent in plaintext",
                severity = PortRiskSeverity.HIGH,
                remediation =
                    "Disable FTP if it isn't needed, or switch to SFTP/FTPS so credentials aren't sent " +
                        "in the clear.",
            ),
        23 to
            PortRisk(
                protocol = "Telnet",
                reason = "unencrypted remote access, credentials sent in plaintext",
                severity = PortRiskSeverity.CRITICAL,
                remediation = "Disable Telnet and use SSH instead.",
            ),
        25 to
            PortRisk(
                protocol = "SMTP",
                reason = "often unauthenticated relay if misconfigured",
                severity = PortRiskSeverity.MODERATE,
                remediation =
                    "Confirm the mail server requires authentication and isn't operating as an open " +
                        "relay.",
            ),
        110 to
            PortRisk(
                protocol = "POP3",
                reason = "unencrypted mail retrieval",
                severity = PortRiskSeverity.MODERATE,
                remediation = "Use POP3S (port 995) instead, or disable plain POP3 if it isn't required.",
            ),
        143 to
            PortRisk(
                protocol = "IMAP",
                reason = "unencrypted mail access",
                severity = PortRiskSeverity.MODERATE,
                remediation = "Use IMAPS (port 993) instead, or disable plain IMAP if it isn't required.",
            ),
        1723 to
            PortRisk(
                protocol = "PPTP",
                reason = "VPN protocol with known cryptographic weaknesses",
                severity = PortRiskSeverity.HIGH,
                remediation = "Replace PPTP with a modern VPN protocol such as WireGuard or IKEv2/IPsec.",
            ),
        3389 to
            PortRisk(
                protocol = "RDP",
                reason = "a common target for automated login attempts",
                severity = PortRiskSeverity.HIGH,
                remediation =
                    "Restrict RDP to a VPN, enable Network Level Authentication, and use a strong " +
                        "password or certificate-based login.",
            ),
        5900 to
            PortRisk(
                protocol = "VNC",
                reason = "often unauthenticated or weakly authenticated by default",
                severity = PortRiskSeverity.CRITICAL,
                remediation =
                    "Set a strong VNC password, or tunnel VNC over SSH/a VPN rather than exposing it " +
                        "directly.",
            ),
    )
