package dev.enthusiastdev.netinspector.core.designsystem.adaptive

import android.app.Activity
import androidx.compose.ui.geometry.Rect
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Raw fold state in window coordinates. A non-separating `HALF_OPENED` (some devices, some
 * angles) falls through to [DevicePosture.Normal] rather than producing a split with nothing
 * to split around (design §11.2).
 */
fun Activity.devicePostureFlow(): Flow<DevicePosture> =
    WindowInfoTracker.getOrCreate(this).windowLayoutInfo(this).map(WindowLayoutInfo::toDevicePosture)

private fun WindowLayoutInfo.toDevicePosture(): DevicePosture {
    val foldingFeature =
        displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
            ?: return DevicePosture.Normal

    if (foldingFeature.state != FoldingFeature.State.HALF_OPENED || !foldingFeature.isSeparating) {
        return DevicePosture.Normal
    }

    val windowBounds =
        Rect(
            left = foldingFeature.bounds.left.toFloat(),
            top = foldingFeature.bounds.top.toFloat(),
            right = foldingFeature.bounds.right.toFloat(),
            bottom = foldingFeature.bounds.bottom.toFloat(),
        )

    return when (foldingFeature.orientation) {
        FoldingFeature.Orientation.VERTICAL -> DevicePosture.Book(windowBounds)
        FoldingFeature.Orientation.HORIZONTAL -> DevicePosture.Tabletop(windowBounds)
        else -> DevicePosture.Normal
    }
}
