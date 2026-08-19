package dev.enthusiastdev.netinspector.core.model.wifi

/**
 * design §6.2 - computed from the channel number, not read from the API. Covers the two most
 * common regulatory-domain DFS ranges; flagged conservatively rather than omitted when the
 * country code isn't available to narrow it further.
 */
fun isDfsChannel(
    band: Band,
    channel: Int,
): Boolean = band == Band.GHZ_5 && (channel in 52..64 || channel in 100..144)

/** design §6.2 - 6 GHz Preferred Scanning Channels: 5, 21, 37, ... (every 16th channel from 5). */
fun is6GhzPsc(
    band: Band,
    channel: Int,
): Boolean = band == Band.GHZ_6 && channel >= 5 && (channel - 5) % 16 == 0
