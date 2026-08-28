package dev.enthusiastdev.netinspector.core.designsystem.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.enthusiastdev.netinspector.core.designsystem.util.findActivity

/**
 * Raw fold posture for the current activity, in window coordinates. Callers that draw a split
 * across the hinge must translate it into their own coordinate space with [translatedTo] -
 * `FoldingFeature.bounds` arrives in window coordinates, and drawing with them unmodified
 * places the split visibly in the wrong place (design §11.2, the most common bug in
 * posture-aware layouts).
 */
@Composable
fun rememberDevicePosture(): State<DevicePosture> {
    val activity = LocalContext.current.findActivity()
    return remember(activity) { activity.devicePostureFlow() }
        .collectAsStateWithLifecycle(initialValue = DevicePosture.Normal)
}

/**
 * Translates `hingeBounds` from window coordinates into the local space of whichever
 * composable's [LayoutCoordinates] are passed in (typically obtained from that composable's
 * own `Modifier.onGloballyPositioned`).
 */
fun DevicePosture.translatedTo(containerCoordinates: LayoutCoordinates): DevicePosture {
    val windowBounds =
        when (this) {
            is DevicePosture.Book -> hingeBounds
            is DevicePosture.Tabletop -> hingeBounds
            DevicePosture.Normal -> return this
        }
    val origin = containerCoordinates.positionInWindow()
    val localBounds =
        Rect(
            left = windowBounds.left - origin.x,
            top = windowBounds.top - origin.y,
            right = windowBounds.right - origin.x,
            bottom = windowBounds.bottom - origin.y,
        )
    return when (this) {
        is DevicePosture.Book -> copy(hingeBounds = localBounds)
        is DevicePosture.Tabletop -> copy(hingeBounds = localBounds)
        DevicePosture.Normal -> this
    }
}
