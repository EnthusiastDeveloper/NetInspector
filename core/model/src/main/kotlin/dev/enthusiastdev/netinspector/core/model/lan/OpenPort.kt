package dev.enthusiastdev.netinspector.core.model.lan

/** design §8.4/§3 - Phase 6 populates this from the extended port probe; unused (always
 * empty) until then. */
data class OpenPort(
    val port: Int,
    val serviceGuess: String?,
    val banner: String?,
)
