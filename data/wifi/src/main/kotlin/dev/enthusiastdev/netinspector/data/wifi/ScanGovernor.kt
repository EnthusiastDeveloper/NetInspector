package dev.enthusiastdev.netinspector.data.wifi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.enthusiastdev.netinspector.core.common.vendor.VendorLookup
import dev.enthusiastdev.netinspector.core.model.wifi.ScanBudget
import dev.enthusiastdev.netinspector.core.model.wifi.ScanOutcome
import dev.enthusiastdev.netinspector.core.model.wifi.ScanSnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * design §6.1 - owns all active scanning against the platform's undocumented, unrecoverable
 * `startScan()` throttle (4 calls / 2 min foreground). `results` is driven entirely by
 * [WifiManager.SCAN_RESULTS_AVAILABLE_ACTION] - including scans this app never asked for -
 * never by a `requestScan()` return value, since most updates arrive for free that way.
 */
@Singleton
class ScanGovernor
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val wifiManager: WifiManager,
        private val clock: Clock,
    ) {
        private val mutex = Mutex()
        private val activeScanTimestamps = ArrayDeque<Instant>()

        // design §6.1 - checked once: a device capability, not something that changes mid-session.
        private val throttlingDisabledOnDevice by lazy { !wifiManager.isScanThrottleEnabled() }

        suspend fun requestScan(isUserInitiated: Boolean): ScanOutcome =
            mutex.withLock {
                pruneWindow()
                val now = clock.instant()
                val retryAt = nextAvailableAt(now, isUserInitiated)
                if (retryAt != null) {
                    return@withLock ScanOutcome.Throttled(retryAt)
                }
                if (!wifiManager.startScan()) {
                    return@withLock ScanOutcome.Failed("startScan() returned false")
                }
                activeScanTimestamps.addLast(now)
                ScanOutcome.Started
            }

        suspend fun budget(): ScanBudget =
            mutex.withLock {
                pruneWindow()
                if (throttlingDisabledOnDevice) {
                    val retryAt = nextAvailableAt(clock.instant(), isUserInitiated = true)
                    ScanBudget(remainingCalls = if (retryAt == null) 1 else 0, quota = 1, nextAvailableAt = retryAt)
                } else {
                    val remaining = (QUOTA - activeScanTimestamps.size).coerceAtLeast(0)
                    val retryAt = if (remaining > 0) null else activeScanTimestamps.first().plus(WINDOW_LENGTH)
                    ScanBudget(remainingCalls = remaining, quota = QUOTA, nextAvailableAt = retryAt)
                }
            }

        /** design §6.1 - passive harvesting: every `SCAN_RESULTS_AVAILABLE_ACTION`, ours or
         * not. Lifecycle-scoping (register on STARTED / unregister on STOPPED) is the
         * collector's job via `repeatOnLifecycle`; this flow just registers on collection
         * start and unregisters on cancellation, same shape as [ConnectivityDataSource]. */
        val results: Flow<ScanSnapshot> =
            callbackFlow {
                val receiver =
                    object : BroadcastReceiver() {
                        override fun onReceive(
                            context: Context,
                            intent: Intent,
                        ) {
                            trySend(currentSnapshot())
                        }
                    }

                context.registerReceiver(
                    receiver,
                    IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
                    Context.RECEIVER_NOT_EXPORTED,
                )

                // A receiver only fires on the *next* completed scan; without this, a
                // collector that starts after a scan already ran (the common case - the
                // screen opens, requests one, and the results arrive before or without a
                // fresh broadcast if another app just scanned) waits with nothing to show.
                trySend(currentSnapshot())

                awaitClose { context.unregisterReceiver(receiver) }
            }

        private fun currentSnapshot(): ScanSnapshot {
            val now = clock.instant()
            val connectedBssid = wifiManager.connectionInfo?.bssid?.normalizedBssid()
            val accessPoints =
                latestScanResults().map { it.toAccessPoint(connectedBssid, now, VendorLookup::vendorFor) }
            return ScanSnapshot(accessPoints = accessPoints, timestamp = now)
        }

        /** design §6.2 - the raw material for lazy information-element parsing on the AP
         * detail screen: `getScanResults()` is a cheap in-memory read (cached by the platform
         * until the next scan), not a fresh probe, so calling it again here on demand costs
         * nothing beyond what [currentSnapshot] already paid for the list view. */
        fun informationElementsFor(bssid: String): List<Pair<Int, ByteArray>> =
            latestScanResults()
                .firstOrNull { it.BSSID == bssid }
                ?.informationElements
                ?.map { it.id to it.bytes.toByteArray() }
                ?: emptyList()

        private fun latestScanResults(): List<ScanResult> =
            try {
                wifiManager.scanResults
            } catch (ignored: SecurityException) {
                emptyList()
            }

        private fun nextAvailableAt(
            now: Instant,
            isUserInitiated: Boolean,
        ): Instant? {
            if (throttlingDisabledOnDevice) {
                val last = activeScanTimestamps.lastOrNull() ?: return null
                val readyAt = last.plus(UNTHROTTLED_INTERVAL)
                return readyAt.takeIf { it.isAfter(now) }
            }
            val cap = if (isUserInitiated) QUOTA else QUOTA - RESERVED_FOR_USER_REFRESH
            return if (activeScanTimestamps.size < cap) null else activeScanTimestamps.first().plus(WINDOW_LENGTH)
        }

        private fun pruneWindow() {
            val cutoff = clock.instant().minus(WINDOW_LENGTH)
            while (activeScanTimestamps.isNotEmpty() && activeScanTimestamps.first().isBefore(cutoff)) {
                activeScanTimestamps.removeFirst()
            }
        }

        private companion object {
            const val QUOTA = 4
            const val RESERVED_FOR_USER_REFRESH = 2
            val WINDOW_LENGTH: Duration = Duration.ofMinutes(2)
            val UNTHROTTLED_INTERVAL: Duration = Duration.ofSeconds(5)
        }
    }
