package dev.enthusiastdev.netinspector.core.common.redact

// improvement-ideas.md #21/#22 - a debug export must not leak the user's home network layout
// or Wi-Fi name. RFC1918 (10/8, 172.16/12, 192.168/16) plus link-local (169.254/16) and
// loopback (127/8) - the ranges an active LAN sweep or connection snapshot can actually emit.
private val LOCAL_IPV4 =
    Regex(
        """\b(?:10\.\d{1,3}\.\d{1,3}\.\d{1,3}|""" +
            """172\.(?:1[6-9]|2\d|3[01])\.\d{1,3}\.\d{1,3}|""" +
            """192\.168\.\d{1,3}\.\d{1,3}|""" +
            """169\.254\.\d{1,3}\.\d{1,3}|""" +
            """127\.\d{1,3}\.\d{1,3}\.\d{1,3})\b""",
    )

fun redactIps(text: String): String = text.replace(LOCAL_IPV4, "<redacted-ip>")

/** SSIDs are arbitrary user-chosen strings, not pattern-matchable - callers pass whichever
 * SSIDs are actually known at redaction time (the connected network, whatever's visible in a
 * scan). Longest-first replacement so a short SSID that's a substring of a longer one (e.g.
 * "Home" inside "Home 5G") doesn't leave a partial match exposed. */
fun redactSsids(
    text: String,
    knownSsids: Set<String>,
): String =
    knownSsids
        .filter { it.isNotBlank() }
        .sortedByDescending { it.length }
        .fold(text) { acc, ssid -> acc.replace(ssid, "<redacted-ssid>") }

fun redact(
    text: String,
    knownSsids: Set<String>,
): String = redactSsids(redactIps(text), knownSsids)
