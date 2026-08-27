package dev.enthusiastdev.netinspector.core.common.log

data class LogEntry(
    val timestampMillis: Long,
    val priority: Int,
    val tag: String?,
    val message: String,
)

/** ideas.md #22 - a bounded, thread-safe (Timber can log from any thread) sink
 * fed by every planted `Timber.Tree`, kept in memory only for on-demand debug-bundle export.
 * Oldest entry is evicted on overflow, so capacity - not app log volume - bounds its size. */
class LogRingBuffer(
    private val capacity: Int,
) {
    private val entries = ArrayDeque<LogEntry>(capacity)

    @Synchronized
    fun add(entry: LogEntry) {
        if (entries.size >= capacity) entries.removeFirst()
        entries.addLast(entry)
    }

    @Synchronized
    fun snapshot(): List<LogEntry> = entries.toList()
}

/** Priority is `android.util.Log`'s raw int (2=VERBOSE..6=ERROR) - this module has no
 * `android.*` dependency, so the mapping is duplicated here rather than imported. */
private fun priorityLabel(priority: Int): String =
    when (priority) {
        2 -> "V"
        3 -> "D"
        4 -> "I"
        5 -> "W"
        6 -> "E"
        else -> "?"
    }

fun LogEntry.toLine(): String = "$timestampMillis ${priorityLabel(priority)}/${tag ?: "-"}: $message"
