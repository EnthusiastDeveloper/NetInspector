package dev.enthusiastdev.netinspector

import android.util.Log
import timber.log.Timber

/** Drops VERBOSE/DEBUG/INFO in release builds; WARN/ERROR still reach logcat. */
class ReleaseTree : Timber.Tree() {
    override fun log(
        priority: Int,
        tag: String?,
        message: String,
        t: Throwable?,
    ) {
        if (priority < Log.WARN) return
        Log.println(priority, tag ?: "NetInspector", message)
    }
}
