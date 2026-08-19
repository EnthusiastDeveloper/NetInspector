package dev.enthusiastdev.netinspector.data.wifi

import dev.enthusiastdev.netinspector.core.common.wifi.summarizeInformationElements
import dev.enthusiastdev.netinspector.core.model.wifi.AccessPoint
import dev.enthusiastdev.netinspector.core.model.wifi.InformationElementSummary
import dev.enthusiastdev.netinspector.core.model.wifi.ScanBudget
import dev.enthusiastdev.netinspector.core.model.wifi.ScanOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import javax.inject.Inject

interface WifiScanRepository {
    /** design §3 - one row per BSSID, refreshed in place rather than re-created; an AP
     * missing from the latest scan keeps its last-known state rather than disappearing. */
    val accessPoints: Flow<List<AccessPoint>>

    suspend fun requestScan(isUserInitiated: Boolean): ScanOutcome

    suspend fun budget(): ScanBudget

    /** design §6.2 - parses information elements only for this one BSSID, on demand. */
    fun informationElements(bssid: String): InformationElementSummary
}

class DefaultWifiScanRepository
    @Inject
    constructor(
        private val scanGovernor: ScanGovernor,
    ) : WifiScanRepository {
        override val accessPoints: Flow<List<AccessPoint>> =
            scanGovernor.results
                .scan(emptyMap<String, AccessPoint>()) { byBssid, snapshot ->
                    val merged = byBssid.toMutableMap()
                    for (accessPoint in snapshot.accessPoints) {
                        val firstSeen = byBssid[accessPoint.bssid]?.firstSeen ?: accessPoint.firstSeen
                        merged[accessPoint.bssid] = accessPoint.copy(firstSeen = firstSeen)
                    }
                    merged
                }.map { it.values.toList() }

        override suspend fun requestScan(isUserInitiated: Boolean) = scanGovernor.requestScan(isUserInitiated)

        override suspend fun budget() = scanGovernor.budget()

        override fun informationElements(bssid: String) =
            summarizeInformationElements(scanGovernor.informationElementsFor(bssid))
    }
