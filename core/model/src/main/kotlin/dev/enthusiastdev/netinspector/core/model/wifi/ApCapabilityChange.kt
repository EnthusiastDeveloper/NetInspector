package dev.enthusiastdev.netinspector.core.model.wifi

/** ideas.md #11 - the capability fields a persisted `known_ap` row snapshots, before
 * or after a change. [primaryChannel] rather than center-frequency/width - it's the human-
 * recognizable number shown on the AP detail screen. */
data class ApCapabilitySnapshot(
    val security: String,
    val standard: String,
    val primaryChannel: Int,
)

/** ideas.md #11 - the `KnownApEntity`-persisted counterpart to
 * [ScanSessionDiff.kt][dev.enthusiastdev.netinspector.core.model.wifi.ApChange]'s per-session
 * diff: same equality-only comparison on security/standard/channel, applied to one BSSID's
 * frozen before/after capability snapshot across scans rather than two whole sessions. The
 * RSSI/center-frequency fields [ApChange] carries don't apply to a single persisted "known AP"
 * row, so this doesn't force-reuse that shape. */
data class ApCapabilityChange(
    val securityChanged: Boolean,
    val standardChanged: Boolean,
    val channelChanged: Boolean,
) {
    val isNotable: Boolean get() = securityChanged || standardChanged || channelChanged
}

fun diffApCapabilities(
    previous: ApCapabilitySnapshot,
    current: ApCapabilitySnapshot,
): ApCapabilityChange =
    ApCapabilityChange(
        securityChanged = previous.security != current.security,
        standardChanged = previous.standard != current.standard,
        channelChanged = previous.primaryChannel != current.primaryChannel,
    )
