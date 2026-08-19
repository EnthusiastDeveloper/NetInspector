package dev.enthusiastdev.netinspector.core.model.wifi

import java.time.Instant

/** design §6.1 - remaining active-scan tokens in the current rolling window, and when the
 * next one frees up. `nextAvailableAt` is null while `remainingCalls > 0`. */
data class ScanBudget(
    val remainingCalls: Int,
    val quota: Int,
    val nextAvailableAt: Instant?,
)
