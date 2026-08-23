package dev.enthusiastdev.netinspector.data.lan

import dev.enthusiastdev.netinspector.core.common.net.Ipv4Subnet
import dev.enthusiastdev.netinspector.core.model.lan.Host
import dev.enthusiastdev.netinspector.core.model.lan.HostConfidence
import dev.enthusiastdev.netinspector.core.model.lan.HostObservation
import dev.enthusiastdev.netinspector.data.lan.enrich.HostEnricher
import dev.enthusiastdev.netinspector.data.lan.sweep.HostSweeper
import java.net.Inet4Address
import javax.inject.Inject

/**
 * design §8.2 - Stage B (the active sweep) followed by Stage C (enrichment of whatever Stage B
 * confirmed). Bundled behind one collaborator so [DefaultLanDiscoveryRepository]'s constructor
 * - which already holds the three Stage A probes, `WifiManager` and `Clock` - doesn't also have
 * to name every Stage B/C dependency directly; those two stages are always run back to back
 * with no meaningful seam for the repository to intervene between them.
 */
class LanSweepPipeline
    @Inject
    constructor(
        private val hostSweeper: HostSweeper,
        private val hostEnricher: HostEnricher,
    ) {
        suspend fun run(
            subnet: Ipv4Subnet,
            gateway: Inet4Address?,
            currentHosts: () -> Collection<Host>,
            onObservation: suspend (HostObservation) -> Unit,
            onProgress: (probed: Int, total: Int) -> Unit,
        ) {
            hostSweeper.sweep(subnet, onObservation, onProgress)

            // design §8.2 Stage C - "confirmed hosts only." Self isn't worth probing: it's this
            // device, not a discovery target.
            val confirmedHosts = currentHosts().filter { it.confidence == HostConfidence.CONFIRMED && !it.isSelf }
            hostEnricher.enrich(confirmedHosts, gateway, onObservation)
        }
    }
