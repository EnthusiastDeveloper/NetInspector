package dev.enthusiastdev.netinspector.usecase

import dev.enthusiastdev.netinspector.core.model.wifi.ScanSnapshot
import dev.enthusiastdev.netinspector.data.persistence.scan.ScanHistoryRepository
import javax.inject.Inject

/** design §10/§2.2 - "use cases exist only where logic spans repositories": this one reads a
 * scan generation from `:data:wifi`'s [ScanSnapshot] and writes it to `:data:persistence`'s
 * scan history, two repositories in two different `:data:*` modules that (design §2.1) can
 * never depend on each other directly - `:app` is the only layer that can see both. */
class RecordWifiScanUseCase
    @Inject
    constructor(
        private val scanHistoryRepository: ScanHistoryRepository,
    ) {
        suspend operator fun invoke(snapshot: ScanSnapshot) {
            val connectedBssid = snapshot.accessPoints.firstOrNull { it.isConnected }?.bssid
            scanHistoryRepository.record(snapshot, connectedBssid)
        }
    }
