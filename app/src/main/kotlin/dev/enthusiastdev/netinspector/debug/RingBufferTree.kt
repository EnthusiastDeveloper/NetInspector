package dev.enthusiastdev.netinspector.debug

import dev.enthusiastdev.netinspector.core.common.log.LogEntry
import dev.enthusiastdev.netinspector.core.common.log.LogRingBuffer
import timber.log.Timber
import javax.inject.Inject

/** ideas.md #22 - unlike [dev.enthusiastdev.netinspector.ReleaseTree] (which drops
 * VERBOSE/DEBUG/INFO in release builds to keep logcat quiet), this tree captures every priority
 * in both build types: the ring buffer exists for on-demand debug-bundle export, not for
 * logcat noise control. */
class RingBufferTree
    @Inject
    constructor(
        private val ringBuffer: LogRingBuffer,
    ) : Timber.Tree() {
        override fun log(
            priority: Int,
            tag: String?,
            message: String,
            t: Throwable?,
        ) {
            val fullMessage = if (t != null) "$message\n${t.stackTraceToString()}" else message
            ringBuffer.add(LogEntry(System.currentTimeMillis(), priority, tag, fullMessage))
        }
    }
