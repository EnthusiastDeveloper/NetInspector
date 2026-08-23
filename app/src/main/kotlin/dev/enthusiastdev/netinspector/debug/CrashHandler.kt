package dev.enthusiastdev.netinspector.debug

import android.os.Build
import dev.enthusiastdev.netinspector.BuildConfig
import dev.enthusiastdev.netinspector.core.common.log.LogRingBuffer
import dev.enthusiastdev.netinspector.core.common.log.toLine
import dev.enthusiastdev.netinspector.core.common.redact.redact
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** improvement-ideas.md #21 - installed in [dev.enthusiastdev.netinspector.NetInspectorApplication]
 * via `Thread.setDefaultUncaughtExceptionHandler`. Purely additive: it always hands off to
 * whatever handler was previously installed, so the system crash dialog and process death
 * behave exactly as they would without this class - it only ever adds a local file write
 * beforehand, and only when the user has opted in. */
@Singleton
class CrashHandler
    @Inject
    constructor(
        private val ringBuffer: LogRingBuffer,
        private val crashReportingContext: CrashReportingContext,
        private val crashReportStore: CrashReportStore,
    ) : Thread.UncaughtExceptionHandler {
        private val previousHandler: Thread.UncaughtExceptionHandler? = Thread.getDefaultUncaughtExceptionHandler()

        override fun uncaughtException(
            thread: Thread,
            throwable: Throwable,
        ) {
            // A secondary failure while handling a crash (disk full, a fresh OutOfMemoryError
            // from a report that's itself too large to allocate) must never stop the real
            // handler below from running.
            runCatching {
                if (crashReportingContext.isReportingEnabled) {
                    val report = buildReportText(thread, throwable)
                    val redacted = redact(report, crashReportingContext.knownSsids)
                    crashReportStore.writeBlocking(redacted)
                }
            }
            previousHandler?.uncaughtException(thread, throwable)
        }

        private fun buildReportText(
            thread: Thread,
            throwable: Throwable,
        ): String {
            // Capped independently of the ring buffer's own capacity - under memory pressure
            // (e.g. an OutOfMemoryError crash) the report itself must stay cheap to build.
            val recentEntries = ringBuffer.snapshot().takeLast(50)
            return buildString {
                appendLine("Time: ${Instant.now()}")
                appendLine("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("Thread: ${thread.name}")
                appendLine()
                appendLine(throwable.stackTraceToString())
                appendLine()
                appendLine("--- last ${recentEntries.size} log lines ---")
                appendLine(recentEntries.joinToString("\n") { it.toLine() })
            }
        }
    }
