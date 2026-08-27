package dev.enthusiastdev.netinspector.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.enthusiastdev.netinspector.core.common.net.Ipv4Subnet
import dev.enthusiastdev.netinspector.core.model.lan.HostConfidence
import dev.enthusiastdev.netinspector.core.model.wifi.ScanOutcome
import dev.enthusiastdev.netinspector.data.lan.LanDiscoveryRepository
import dev.enthusiastdev.netinspector.data.persistence.lan.KnownLanHostRepository
import dev.enthusiastdev.netinspector.data.persistence.preferences.AutoScanSettingsRepository
import dev.enthusiastdev.netinspector.data.persistence.preferences.LanAcknowledgementRepository
import dev.enthusiastdev.netinspector.data.wifi.ConnectionRepository
import dev.enthusiastdev.netinspector.data.wifi.ScanGovernor
import dev.enthusiastdev.netinspector.usecase.RecordWifiScanUseCase
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.net.Inet4Address
import java.time.Duration

/** ideas.md #23/#24 - the opt-in periodic background job: records a
 * throttle-respecting Wi-Fi scan (#23) and, if also enabled, runs a LAN sweep and diffs it for
 * new/vanished/reappeared devices (#24). `:app` is the only layer allowed to see both
 * `:data:wifi` and `:data:lan` (design §2.1), same as `DevicesViewModel`. Same shape as
 * `RetentionCleanupWorker`; ADR C-10 governs both - deferrable work only, `Result.success()`
 * regardless of what got skipped this run, since a gap is meant to be visible, not retried into
 * invisibility.
 *
 * Deliberately spans both halves (Wi-Fi + LAN) in one job to avoid two separate
 * battery-relevant background wake-ups, so its constructor is a flat, independent dependency
 * per half rather than something a natural grouping would shrink - `@Suppress`d below rather
 * than added purely to satisfy the linter. */
@Suppress("LongParameterList")
@HiltWorker
class PeriodicScanWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val scanGovernor: ScanGovernor,
        private val recordWifiScan: RecordWifiScanUseCase,
        private val connectionRepository: ConnectionRepository,
        private val lanDiscoveryRepository: LanDiscoveryRepository,
        private val lanAcknowledgementRepository: LanAcknowledgementRepository,
        private val knownLanHostRepository: KnownLanHostRepository,
        private val autoScanSettingsRepository: AutoScanSettingsRepository,
        private val lanChangeNotifier: LanChangeNotifier,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            // Defensive re-check, not the primary gate - AutoScanScheduler.cancel() is the
            // primary gate, this only covers a run already queued when the toggle flipped off.
            if (!autoScanSettingsRepository.autoScanEnabled.first()) return Result.success()

            recordWifiScanIfPossible()
            if (autoScanSettingsRepository.alertOnLanHostChanges.first()) sweepLanIfPossible()

            return Result.success()
        }

        private suspend fun recordWifiScanIfPossible() {
            when (val outcome = scanGovernor.requestScan(isUserInitiated = false)) {
                is ScanOutcome.Started -> {
                    // design §6.1 - `results` emits the currently-cached snapshot immediately on
                    // collection start; `drop(1)` skips that stale emission and waits for the
                    // real post-scan broadcast this request triggered.
                    val snapshot =
                        withTimeoutOrNull(SCAN_RESULT_TIMEOUT.toMillis()) { scanGovernor.results.drop(1).first() }
                    snapshot?.let { recordWifiScan(it) }
                }
                is ScanOutcome.Throttled -> Timber.d("Periodic Wi-Fi scan skipped: throttled until %s", outcome.retryAt)
                is ScanOutcome.Failed -> Timber.d("Periodic Wi-Fi scan failed: %s", outcome.reason)
            }
        }

        private suspend fun sweepLanIfPossible() {
            // design §11.4 - never run an active LAN sweep before the user has seen the
            // first-run consent screen, background job or not.
            if (!lanAcknowledgementRepository.isAcknowledged.first()) return

            val connection =
                withTimeoutOrNull(CONNECTION_WAIT_TIMEOUT.toMillis()) {
                    connectionRepository.connectionSnapshot.first { it?.ipv4 != null }
                } ?: return
            val ipv4 = connection.ipv4 ?: return
            val selfAddress = ipv4.address as? Inet4Address ?: return
            val subnet = Ipv4Subnet(selfAddress, ipv4.prefixLength)

            lanDiscoveryRepository.sweep(subnet, connection.gateway, selfAddress, connection.bssid)
            val confirmedHosts =
                lanDiscoveryRepository.hosts.first().filter { it.confidence == HostConfidence.CONFIRMED }
            val diff = knownLanHostRepository.applySweep(confirmedHosts)
            lanChangeNotifier.notify(diff)
        }

        companion object {
            private val SCAN_RESULT_TIMEOUT: Duration = Duration.ofSeconds(10)
            private val CONNECTION_WAIT_TIMEOUT: Duration = Duration.ofSeconds(5)
        }
    }
