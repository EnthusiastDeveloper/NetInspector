package dev.enthusiastdev.netinspector.data.diagnostics.icmp

import dev.enthusiastdev.netinspector.core.model.diagnostics.PingProbeResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.net.Inet4Address
import javax.inject.Inject

/** [count] `null` means unbounded ("ping -t" style, run until the caller cancels the flow)
 * rather than overloading a finite sentinel like [Int.MAX_VALUE] - any code that reasons about
 * "how many pings will this send" (duration estimates, history records, ...) sees an explicit
 * absence instead of a number that looks meaningful but isn't. */
data class PingConfig(
    val count: Int? = 5,
    val intervalMs: Long = 1_000,
    val timeoutMs: Int = 1_000,
    val ttl: Int = 64,
    val payloadSize: Int = 32,
)

interface PingRepository {
    /** Streams one [PingProbeResult] per probe as it completes - partial results survive a
     * run that's cancelled or that dies partway through (design §2.3). */
    fun ping(
        address: Inet4Address,
        config: PingConfig = PingConfig(),
    ): Flow<PingProbeResult>
}

class DefaultPingRepository
    @Inject
    constructor(
        private val icmpSocketEngine: IcmpSocketEngine,
        private val pingBinaryEngine: PingBinaryEngine,
    ) : PingRepository {
        // Checked once per process, not per probe: a capability gap (design §9.1's "Fail on
        // some devices" spike outcome), not a transient network condition.
        private val icmpSocketSupported by lazy { icmpSocketEngine.isSupported() }

        // Tier 3 (TCP connect) is available to callers directly via TcpConnectEngine for
        // hosts known to have ICMP disabled; it isn't part of this automatic fallback chain
        // since "no ICMP reply" and "ICMP unsupported on this device" aren't the same signal.
        override fun ping(
            address: Inet4Address,
            config: PingConfig,
        ): Flow<PingProbeResult> =
            flow {
                var sequence = 0
                while (config.count == null || sequence < config.count) {
                    val result =
                        if (icmpSocketSupported) {
                            icmpSocketEngine.probe(address, sequence, config.timeoutMs, config.ttl, config.payloadSize)
                        } else {
                            pingBinaryEngine.probe(address, sequence, config.timeoutMs)
                        }
                    emit(result)
                    sequence++
                    val isLastProbe = config.count != null && sequence >= config.count
                    if (!isLastProbe) delay(config.intervalMs)
                }
            }
    }
