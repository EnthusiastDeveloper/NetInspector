package dev.enthusiastdev.netinspector.core.model.wifi

import java.time.Instant

/** design §6.1 - one passively-harvested scan generation, keyed by BSSID by the repository
 * layer (this type just carries what a single `SCAN_RESULTS_AVAILABLE_ACTION` produced). */
data class ScanSnapshot(
    val accessPoints: List<AccessPoint>,
    val timestamp: Instant,
)
