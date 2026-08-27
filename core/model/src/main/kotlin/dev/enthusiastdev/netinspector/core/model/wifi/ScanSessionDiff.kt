package dev.enthusiastdev.netinspector.core.model.wifi

import kotlin.math.abs

/** ideas.md #6 - a persisted `ScanObservationEntity` projected down to just the
 * fields the diff cares about. [band]/[security]/[standard] stay as the entity's raw stored
 * strings rather than parsed back into [Band]/[SecurityType]/[WifiStandard] - the diff only
 * ever needs equality on them, so parsing would add a failure mode (an unparseable stored
 * string) for no behavioural benefit. */
data class ObservedAp(
    val bssid: String,
    val ssid: String,
    val rssiDbm: Int,
    val band: String,
    val centerFrequencyMhz: Int,
    val channelWidthMhz: Int,
    val security: String,
    val standard: String,
)

data class ApChange(
    val before: ObservedAp,
    val after: ObservedAp,
) {
    val rssiDeltaDbm: Int get() = after.rssiDbm - before.rssiDbm
    val securityChanged: Boolean get() = before.security != after.security
    val standardChanged: Boolean get() = before.standard != after.standard
    val channelChanged: Boolean
        get() =
            before.centerFrequencyMhz != after.centerFrequencyMhz ||
                before.channelWidthMhz != after.channelWidthMhz
}

data class ScanSessionDiff(
    val added: List<ObservedAp>,
    val removed: List<ObservedAp>,
    val changed: List<ApChange>,
    /** Matched in both sessions with nothing notable different - a count, not a list, since
     * the point of a diff is showing what's different, not restating everything that isn't. */
    val unchangedCount: Int,
)

/** ideas.md #6 - pure diff between two scan sessions, keyed by [ObservedAp.bssid],
 * same associate-by-key/set-difference shape `diffLanPresence` (lan package) uses.
 *
 * [notableRssiDeltaDbm] exists because ordinary scan-to-scan RSSI jitter - every AP moves a
 * couple of dBm between any two real scans, even with nothing physically different - would
 * otherwise flag nearly every matched AP as "changed" and make the view useless for its actual
 * purpose ("before/after I moved the router"). A matched AP is only reported in [changed] when
 * security, standard, or channel differ outright, or the RSSI move is at least this large. */
fun diffScanSessions(
    before: List<ObservedAp>,
    after: List<ObservedAp>,
    notableRssiDeltaDbm: Int = 6,
): ScanSessionDiff {
    val beforeByBssid = before.associateBy { it.bssid }
    val afterByBssid = after.associateBy { it.bssid }

    val added = (afterByBssid.keys - beforeByBssid.keys).map { afterByBssid.getValue(it) }
    val removed = (beforeByBssid.keys - afterByBssid.keys).map { beforeByBssid.getValue(it) }

    var unchangedCount = 0
    val changed =
        (beforeByBssid.keys intersect afterByBssid.keys).mapNotNull { bssid ->
            val change = ApChange(beforeByBssid.getValue(bssid), afterByBssid.getValue(bssid))
            val notableRssiMove = abs(change.rssiDeltaDbm) >= notableRssiDeltaDbm
            val notable =
                change.securityChanged || change.standardChanged || change.channelChanged || notableRssiMove
            if (notable) {
                change
            } else {
                unchangedCount++
                null
            }
        }

    return ScanSessionDiff(added, removed, changed, unchangedCount)
}
