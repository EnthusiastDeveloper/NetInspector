package dev.enthusiastdev.netinspector.core.model.lan

/** design §8.4/§3 - Phase 6 (device identification) populates this; the field exists now so
 * the Phase 5 model doesn't need to change shape when that phase lands. Nothing may assert a
 * [label] without a [basis] to show for it. */
data class DeviceHint(
    val label: String,
    val basis: String,
    val certainty: Certainty,
)

enum class Certainty { LIKELY, POSSIBLE }
