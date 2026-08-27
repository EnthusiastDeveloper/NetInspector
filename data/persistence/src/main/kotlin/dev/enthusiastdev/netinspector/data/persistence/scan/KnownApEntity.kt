package dev.enthusiastdev.netinspector.data.persistence.scan

import androidx.room.Entity
import androidx.room.PrimaryKey

/** design §10 - stable per-BSSID record, indefinite retention (never swept by the periodic
 * cleanup worker, unlike [ScanSessionEntity]/[ScanObservationEntity]).
 *
 * ideas.md #11 - [security]/[standard]/[primaryChannel] are the AP's capabilities
 * as of the latest scan; [previousSecurity]/[previousStandard]/[previousPrimaryChannel] are a
 * frozen snapshot of what they were immediately before the last notable change (only updated
 * when [dev.enthusiastdev.netinspector.core.model.wifi.diffApCapabilities] finds one), with
 * [lastCapabilityChangeMillis] marking when. All seven are nullable and stay null until a real
 * baseline has been observed - a pre-migration row or a brand-new BSSID has no capabilities to
 * compare against yet, and treating that as "everything changed" would flag a false positive on
 * every known AP the moment this ships. `security` is a comma-joined set of `SecurityType` names
 * and `standard` a `WifiStandard` name, same raw-string convention `ScanObservationEntity` and
 * `ObservedAp` already use - only the UI parses them back for display. */
@Entity(tableName = "known_ap")
data class KnownApEntity(
    @PrimaryKey val bssid: String,
    val ssid: String,
    val vendor: String?,
    val firstSeenMillis: Long,
    val lastSeenMillis: Long,
    val bestRssiDbm: Int,
    val security: String? = null,
    val standard: String? = null,
    val primaryChannel: Int? = null,
    val previousSecurity: String? = null,
    val previousStandard: String? = null,
    val previousPrimaryChannel: Int? = null,
    val lastCapabilityChangeMillis: Long? = null,
)
