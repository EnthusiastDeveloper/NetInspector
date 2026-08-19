package dev.enthusiastdev.netinspector.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

private fun style(
    weight: FontWeight,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    letterSpacing: TextUnit,
) = TextStyle(fontWeight = weight, fontSize = fontSize, lineHeight = lineHeight, letterSpacing = letterSpacing)

internal val NetInspectorTypography =
    Typography(
        displayLarge = style(FontWeight.Normal, 57.sp, 64.sp, (-0.25).sp),
        displayMedium = style(FontWeight.Normal, 45.sp, 52.sp, 0.sp),
        displaySmall = style(FontWeight.Normal, 36.sp, 44.sp, 0.sp),
        headlineLarge = style(FontWeight.Normal, 32.sp, 40.sp, 0.sp),
        headlineMedium = style(FontWeight.Normal, 28.sp, 36.sp, 0.sp),
        headlineSmall = style(FontWeight.Normal, 24.sp, 32.sp, 0.sp),
        titleLarge = style(FontWeight.Normal, 22.sp, 28.sp, 0.sp),
        titleMedium = style(FontWeight.Medium, 16.sp, 24.sp, 0.15.sp),
        titleSmall = style(FontWeight.Medium, 14.sp, 20.sp, 0.1.sp),
        bodyLarge = style(FontWeight.Normal, 16.sp, 24.sp, 0.5.sp),
        bodyMedium = style(FontWeight.Normal, 14.sp, 20.sp, 0.25.sp),
        bodySmall = style(FontWeight.Normal, 12.sp, 16.sp, 0.4.sp),
        labelLarge = style(FontWeight.Medium, 14.sp, 20.sp, 0.1.sp),
        labelMedium = style(FontWeight.Medium, 12.sp, 16.sp, 0.5.sp),
        labelSmall = style(FontWeight.Medium, 11.sp, 16.sp, 0.5.sp),
    )
