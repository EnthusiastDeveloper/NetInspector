package dev.enthusiastdev.netinspector.debug

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/** improvement-ideas.md #21 - crash reports live in `filesDir` (durable across restarts,
 * unlike the debug bundle's `cacheDir` zips which are transient/regenerable). */
@Singleton
class CrashReportStore
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val dir = File(context.filesDir, "crash_reports")

        /** Called from [CrashHandler] on the crashing thread - must never throw, since a
         * secondary failure here (disk full, `OutOfMemoryError` mid-write) must not prevent
         * the real [Thread.UncaughtExceptionHandler] chain from running. Filenames are
         * timestamp-prefixed so a plain lexical sort is also chronological order, with no
         * extra metadata needed to enforce the retention cap below. */
        fun writeBlocking(redactedText: String) {
            runCatching {
                dir.mkdirs()
                val name = "crash_${TIMESTAMP_FORMAT.format(Instant.now())}.txt"
                File(dir, name).writeText(redactedText)
                pruneOldReports()
            }
        }

        private fun pruneOldReports() {
            val files = dir.listFiles()?.sortedBy { it.name } ?: return
            if (files.size <= MAX_REPORTS) return
            files.dropLast(MAX_REPORTS).forEach { it.delete() }
        }

        suspend fun hasReports(): Boolean = withContext(Dispatchers.IO) { dir.listFiles()?.isNotEmpty() == true }

        suspend fun latestReport(): File? = withContext(Dispatchers.IO) { dir.listFiles()?.maxByOrNull { it.name } }

        private companion object {
            const val MAX_REPORTS = 20
            val TIMESTAMP_FORMAT: DateTimeFormatter =
                DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS").withZone(ZoneOffset.UTC)
        }
    }
