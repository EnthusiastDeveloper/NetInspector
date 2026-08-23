package dev.enthusiastdev.netinspector.debug

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.enthusiastdev.netinspector.core.common.log.LogRingBuffer
import dev.enthusiastdev.netinspector.core.common.log.toLine
import dev.enthusiastdev.netinspector.core.common.redact.redact
import dev.enthusiastdev.netinspector.data.lan.LanDiscoveryRepository
import dev.enthusiastdev.netinspector.data.persistence.diagnostics.DiagnosticRunRepository
import dev.enthusiastdev.netinspector.data.wifi.ConnectionRepository
import dev.enthusiastdev.netinspector.data.wifi.WifiScanRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** improvement-ideas.md #22 - builds the shareable debug bundle from whatever the app already
 * has live in memory (no new persisted history is introduced for this). Zips land in
 * `cacheDir`, not `filesDir`: unlike a crash report, a bundle is transient and regenerable on
 * demand, so it's fine for the OS to reclaim under storage pressure. */
@Singleton
class DebugBundleBuilder
    @Inject
    constructor(
        private val connectionRepository: ConnectionRepository,
        private val lanDiscoveryRepository: LanDiscoveryRepository,
        private val wifiScanRepository: WifiScanRepository,
        private val diagnosticRunRepository: DiagnosticRunRepository,
        private val ringBuffer: LogRingBuffer,
        @ApplicationContext private val context: Context,
    ) {
        suspend fun build(): File =
            withContext(Dispatchers.IO) {
                val connection = connectionRepository.connectionSnapshot.first()
                val hosts = lanDiscoveryRepository.hosts.first()
                val progress = lanDiscoveryRepository.progress.first()
                val scanState = wifiScanRepository.scanState.first()
                val diagnosticRuns =
                    diagnosticRunRepository.recent(RECENT_DIAGNOSTIC_RUN_LIMIT).first().map {
                        DiagnosticRunSummary(
                            toolType = it.toolType,
                            target = it.target,
                            timestampMillis = it.timestampMillis,
                            summary = it.summary,
                        )
                    }

                val knownSsids =
                    buildSet {
                        connection?.ssid?.let(::add)
                        scanState.accessPoints.forEach { add(it.ssid) }
                    }

                val snapshotText =
                    redact(
                        formatDebugSnapshot(connection, hosts, progress, scanState.accessPoints, diagnosticRuns),
                        knownSsids,
                    )
                val logsText = redact(ringBuffer.snapshot().joinToString("\n") { it.toLine() }, knownSsids)

                writeZip(snapshotText, logsText)
            }

        private fun writeZip(
            snapshotText: String,
            logsText: String,
        ): File {
            val shareDir = File(context.cacheDir, "share").apply { mkdirs() }
            val zipFile = File(shareDir, "netinspector_debug_${System.currentTimeMillis()}.zip")
            ZipOutputStream(zipFile.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("snapshot.txt"))
                zip.write(snapshotText.toByteArray())
                zip.closeEntry()

                zip.putNextEntry(ZipEntry("logs.txt"))
                zip.write(logsText.toByteArray())
                zip.closeEntry()
            }
            return zipFile
        }

        private companion object {
            const val RECENT_DIAGNOSTIC_RUN_LIMIT = 20
        }
    }
