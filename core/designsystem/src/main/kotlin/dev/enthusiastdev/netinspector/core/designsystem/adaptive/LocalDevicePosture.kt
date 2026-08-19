package dev.enthusiastdev.netinspector.core.designsystem.adaptive

import androidx.compose.runtime.compositionLocalOf

/** Provided once, near the app root, with `hingeBounds` already translated (design §11.2). */
val LocalDevicePosture = compositionLocalOf<DevicePosture> { DevicePosture.Normal }
