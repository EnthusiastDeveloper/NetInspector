package dev.enthusiastdev.netinspector.data.diagnostics.traceroute

import dev.enthusiastdev.netinspector.core.model.diagnostics.TracerouteHop
import dev.enthusiastdev.netinspector.data.diagnostics.icmp.IcmpSocketEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetAddress
import javax.inject.Inject

data class TracerouteConfig(
    val maxHops: Int = 30,
    val probesPerHop: Int = 3,
    val timeoutMs: Int = 2_000,
)

interface TracerouteRepository {
    /** design §9.3 - one [TracerouteHop] emission per hop as its probes complete, with a second
     * emission for the same `ttl` if/when that hop's reverse DNS resolves. Stops on target
     * reached, `config.maxHops`, or 5 consecutive fully-timed-out hops. */
    fun traceroute(
        target: Inet4Address,
        config: TracerouteConfig = TracerouteConfig(),
    ): Flow<TracerouteHop>
}

class DefaultTracerouteRepository
    @Inject
    constructor(
        private val icmpSocketEngine: IcmpSocketEngine,
        private val tracerouteSocketEngine: TracerouteSocketEngine,
        private val tracerouteBinaryEngine: TracerouteBinaryEngine,
    ) : TracerouteRepository {
        // Same capability gate as PingRepository (design §9.1) - a process-lifetime check, not
        // per-hop; if S-02 needs to be re-run and fails, this is where the fallback engages.
        private val socketSupported by lazy { icmpSocketEngine.isSupported() }

        override fun traceroute(
            target: Inet4Address,
            config: TracerouteConfig,
        ): Flow<TracerouteHop> =
            channelFlow {
                var consecutiveTimeouts = 0
                for (ttl in 1..config.maxHops) {
                    val probes =
                        (0 until config.probesPerHop).map {
                            if (socketSupported) {
                                tracerouteSocketEngine.probeHop(target, ttl, config.timeoutMs)
                            } else {
                                tracerouteBinaryEngine.probeHop(target, ttl, config.timeoutMs)
                            }
                        }
                    val hop = TracerouteHop(ttl, probes)
                    send(hop)

                    // Fire-and-forget within this channelFlow's own scope so a slow reverse
                    // lookup never delays probing the next hop (design §9.3: "async per-hop
                    // reverse DNS ... filled in as it arrives"). Structured concurrency means
                    // this is still cancelled if the collector stops the run early.
                    hop.respondingAddress?.let { address ->
                        launch {
                            val hostname = resolveHostname(address)
                            if (hostname != null) send(hop.copy(hostname = hostname))
                        }
                    }

                    consecutiveTimeouts = if (hop.isFullyTimedOut) consecutiveTimeouts + 1 else 0
                    if (hop.reachedTarget || consecutiveTimeouts >= MAX_CONSECUTIVE_TIMEOUTS) break
                }
            }

        private suspend fun resolveHostname(address: String): String? =
            withContext(Dispatchers.IO) {
                runCatching {
                    val resolved = InetAddress.getByName(address)
                    resolved.hostName.takeIf { it != resolved.hostAddress }
                }.getOrNull()
            }

        private companion object {
            const val MAX_CONSECUTIVE_TIMEOUTS = 5
        }
    }
