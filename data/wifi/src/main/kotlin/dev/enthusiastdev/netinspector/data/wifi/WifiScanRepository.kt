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

/** design §7.2 - "refuses to recommend from a single scan": [sampleCount] is how many
 * distinct passively-harvested scan generations have contributed to [accessPoints] so far,
 * so the channel recommendation card can gate on it. */
data class WifiScanState(
    val accessPoints: List<AccessPoint>,
    val sampleCount: Int,
)

interface WifiScanRepository {
    /** design §3, §7.2 - one row per BSSID, refreshed in place rather than re-created; an AP
     * missing from the latest scan keeps its last-known state rather than disappearing. */
    val scanState: Flow<WifiScanState>

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
        private data class Accumulator(
            val byBssid: Map<String, AccessPoint> = emptyMap(),
            val sampleCount: Int = 0,
        )

        override val scanState: Flow<WifiScanState> =
            scanGovernor.results
                .scan(Accumulator()) { acc, snapshot ->
                    val merged = acc.byBssid.toMutableMap()
                    for (accessPoint in snapshot.accessPoints) {
                        val firstSeen = acc.byBssid[accessPoint.bssid]?.firstSeen ?: accessPoint.firstSeen
                        merged[accessPoint.bssid] = accessPoint.copy(firstSeen = firstSeen)
                    }
                    Accumulator(merged, acc.sampleCount + 1)
                }.map { WifiScanState(it.byBssid.values.toList(), it.sampleCount) }

        override suspend fun requestScan(isUserInitiated: Boolean) = scanGovernor.requestScan(isUserInitiated)

        override suspend fun budget() = scanGovernor.budget()

        override fun informationElements(bssid: String) =
            summarizeInformationElements(scanGovernor.informationElementsFor(bssid))
    }
