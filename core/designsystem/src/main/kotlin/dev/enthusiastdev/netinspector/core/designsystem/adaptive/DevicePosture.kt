package dev.enthusiastdev.netinspector.core.designsystem.adaptive

import androidx.compose.ui.geometry.Rect

/**
 * Fold posture, layered on top of window size class rather than replacing it (design §11.2).
 * `hingeBounds` starts in window coordinates ([devicePostureFlow]) and is translated into a
 * specific composable's local space by [translatedTo] before it is used to place content.
 */
sealed interface DevicePosture {
    data object Normal : DevicePosture

    data class Book(
        val hingeBounds: Rect,
    ) : DevicePosture

    data class Tabletop(
        val hingeBounds: Rect,
    ) : DevicePosture
}
