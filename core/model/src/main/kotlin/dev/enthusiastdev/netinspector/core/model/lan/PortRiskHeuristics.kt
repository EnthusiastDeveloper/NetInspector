package dev.enthusiastdev.netinspector.core.model.lan

/**
 * Turns an open port from the extended port probe (design §8.2 Stage C) into a short,
 * evidence-anchored note when that port is a well-known unencrypted or historically
 * vulnerable protocol - the same "no field without a basis" convention [DeviceHintHeuristics]
 * follows. `null` means the port carries no notable risk beyond simply being open. Pure and
 * unit-tested, same shape as [deviceHintFor].
 */
fun portRiskNote(port: Int): String? = PORT_RISK_NOTES[port]

private val PORT_RISK_NOTES: Map<Int, String> =
    mapOf(
        21 to "FTP - credentials and files sent in plaintext",
        23 to "Telnet - unencrypted remote access, credentials sent in plaintext",
        25 to "SMTP - often unauthenticated relay if misconfigured",
        110 to "POP3 - unencrypted mail retrieval",
        143 to "IMAP - unencrypted mail access",
        1723 to "PPTP - VPN protocol with known cryptographic weaknesses",
        3389 to "RDP - a common target for automated login attempts",
        5900 to "VNC - often unauthenticated or weakly authenticated by default",
    )
