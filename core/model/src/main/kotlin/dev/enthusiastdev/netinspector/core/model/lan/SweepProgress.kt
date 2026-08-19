package dev.enthusiastdev.netinspector.core.model.lan

/** design §8.2/Phase 5 UI acceptance - "scan progress with addresses-probed count." */
data class SweepProgress(
    val isRunning: Boolean,
    val addressesProbed: Int,
    val addressesTotal: Int,
)

/** design §8.2 - the sweep refuses a prefix shorter than /22 (a /16 is 65,534 probes)
 * without explicit confirmation; passive (Stage A) discovery is unaffected. */
sealed interface SweepOutcome {
    data object Started : SweepOutcome

    data class NeedsConfirmation(
        val hostCount: Long,
    ) : SweepOutcome

    data object AlreadyRunning : SweepOutcome
}
