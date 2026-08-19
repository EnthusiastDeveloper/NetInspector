package dev.enthusiastdev.netinspector.core.model.lan

/** design §8.3. */
enum class HostConfidence {
    /** Responded directly to at least one probe (or is the gateway/self). */
    CONFIRMED,

    /** Advertised itself via mDNS/SSDP/NetBIOS but never answered a probe. */
    ANNOUNCED,

    /** Seen in a previous sweep, absent from the current one. Kept visible for one sweep,
     * greyed, then dropped. */
    STALE,
}
