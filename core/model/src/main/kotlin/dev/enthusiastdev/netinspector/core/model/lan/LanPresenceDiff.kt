package dev.enthusiastdev.netinspector.core.model.lan

/** ideas.md #24 - one row of persisted state per LAN host identity ([key] is
 * [Host.nicknameKey]), tracked across periodic background sweeps rather than a single live
 * sweep's in-memory [HostConfidence]. */
data class KnownHostRecord(
    val key: String,
    val displayName: String?,
    val firstSeenMillis: Long,
    val lastSeenMillis: Long,
    val consecutiveMissedSweeps: Int,
    val vanishedAlertSent: Boolean,
)

data class LanPresenceDiff(
    val newHosts: List<KnownHostRecord>,
    val vanishedHosts: List<KnownHostRecord>,
    val reappearedHosts: List<KnownHostRecord>,
    /** Full next-state for every known key, including ones with no alert-worthy change - the
     * caller persists this verbatim. */
    val updatedRecords: List<KnownHostRecord>,
)

/** ideas.md #24 - pure decision logic for the periodic background sweep's
 * new/vanished/reappeared device alerts, kept free of Room/Android so it's JVM-unit-testable
 * (design's testability bar), same shape as [mergeObservation]/[finalizeSweep]. Only
 * [HostConfidence.CONFIRMED] hosts should be passed in [currentConfirmedHosts] - the caller
 * filters, this function doesn't second-guess confidence tiers.
 *
 * [vanishedThreshold] mirrors design §8.3's single-sweep STALE grace period (kept visible one
 * sweep before dropping) but extended to two *periodic* sweeps: a single missed sweep is
 * tolerated as transient/DHCP churn before a "vanished" alert fires.
 *
 * A key in [knownDeviceKeys] (docs/ideas.md #24 - "known device" flag) never
 * appears in [LanPresenceDiff.vanishedHosts] or [LanPresenceDiff.reappearedHosts] - it's still
 * tracked in [updatedRecords] so the flag can later be cleared without losing state.
 *
 * [previousRecords] being empty *is* "first run" for this function's purposes - whether that's
 * because the feature was just enabled or because retention cleanup already purged everything,
 * there's no prior state to compare against either way, so there's nothing to derive a
 * separate flag from. */
fun diffLanPresence(
    previousRecords: Map<String, KnownHostRecord>,
    currentConfirmedHosts: List<Host>,
    nowMillis: Long,
    knownDeviceKeys: Set<String>,
    vanishedThreshold: Int = 2,
): LanPresenceDiff {
    val isFirstRun = previousRecords.isEmpty()
    val currentByKey = currentConfirmedHosts.associateBy { it.nicknameKey() }

    val newHosts = mutableListOf<KnownHostRecord>()
    val vanishedHosts = mutableListOf<KnownHostRecord>()
    val reappearedHosts = mutableListOf<KnownHostRecord>()
    val updatedRecords = mutableListOf<KnownHostRecord>()

    for ((key, host) in currentByKey) {
        val previous = previousRecords[key]
        if (previous == null) {
            val record =
                KnownHostRecord(
                    key = key,
                    displayName = host.primaryHostname,
                    firstSeenMillis = nowMillis,
                    lastSeenMillis = nowMillis,
                    consecutiveMissedSweeps = 0,
                    vanishedAlertSent = false,
                )
            updatedRecords += record
            if (!isFirstRun) newHosts += record
        } else {
            if (previous.vanishedAlertSent && key !in knownDeviceKeys) {
                reappearedHosts += previous
            }
            updatedRecords +=
                previous.copy(
                    displayName = host.primaryHostname ?: previous.displayName,
                    lastSeenMillis = nowMillis,
                    consecutiveMissedSweeps = 0,
                    vanishedAlertSent = false,
                )
        }
    }

    for ((key, previous) in previousRecords) {
        if (key in currentByKey) continue
        val missed = previous.consecutiveMissedSweeps + 1
        val justCrossedThreshold = missed == vanishedThreshold && !previous.vanishedAlertSent
        val updated =
            previous.copy(
                consecutiveMissedSweeps = missed,
                vanishedAlertSent = previous.vanishedAlertSent || justCrossedThreshold,
            )
        updatedRecords += updated
        if (justCrossedThreshold && key !in knownDeviceKeys) {
            vanishedHosts += updated
        }
    }

    return LanPresenceDiff(newHosts, vanishedHosts, reappearedHosts, updatedRecords)
}
