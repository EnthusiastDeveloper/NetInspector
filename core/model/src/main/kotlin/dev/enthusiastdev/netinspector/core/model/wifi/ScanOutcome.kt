package dev.enthusiastdev.netinspector.core.model.wifi

import java.time.Instant

/** design §2.3 - user-visible scan errors as a domain type, never a thrown exception. */
sealed interface ScanOutcome {
    data object Started : ScanOutcome

    data class Throttled(
        val retryAt: Instant,
    ) : ScanOutcome

    data class Failed(
        val reason: String,
    ) : ScanOutcome
}
