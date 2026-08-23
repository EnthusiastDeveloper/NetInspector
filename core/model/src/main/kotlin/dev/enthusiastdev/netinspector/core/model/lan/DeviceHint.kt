package dev.enthusiastdev.netinspector.core.model.lan

/** design §8.4/§3 - Phase 6 (device identification) populates this; the field exists now so
 * the Phase 5 model doesn't need to change shape when that phase lands. Nothing may assert a
 * [label] without a [basis] to show for it. */
data class DeviceHint(
    val label: String,
    val basis: String,
    val certainty: Certainty,
)

/** design §8.2/docs/device-identification-ideas.md A1 - a three-tier ladder, most confident
 * first. [CONFIRMED] is a device's own self-reported identity (UPnP `manufacturer`/`modelName`,
 * an mDNS TXT model string) rather than something inferred from indirect signals; it ranks
 * above [LIKELY]'s protocol/port inference and [POSSIBLE]'s coarse TTL guess. The enum's
 * declaration order doubles as rank order via its natural [Comparable] - see
 * `HostMerge.preferredHint`. */
enum class Certainty { CONFIRMED, LIKELY, POSSIBLE }
