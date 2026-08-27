package dev.enthusiastdev.netinspector.core.designsystem.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * Walks the [ContextWrapper] chain to the host [Activity].
 *
 * `LocalContext.current as Activity` is the usual shortcut, but it is wrong inside anything the
 * platform hosts in its own window (a `Dialog`, a `ModalBottomSheet`, a `Popup`): there
 * `LocalContext` is that window's `ContextThemeWrapper`, not the Activity, and the cast throws
 * `ClassCastException`. The Activity is still reachable through `baseContext`, so unwrap rather
 * than cast.
 */
tailrec fun Context.findActivity(): Activity =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> error("Expected an Activity in the Context chain but found $this")
    }
