package dev.enthusiastdev.netinspector.core.model.lan

import java.time.Instant

/** design §8.3 - every probe appends one of these rather than overwriting fields, so
 * conflicting signals about the same host are preserved rather than silently resolved. */
data class Evidence(
    val source: EvidenceSource,
    val observedAt: Instant,
    val detail: String? = null,
)
