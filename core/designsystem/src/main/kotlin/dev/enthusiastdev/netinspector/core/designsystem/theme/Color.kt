package dev.enthusiastdev.netinspector.core.designsystem.theme

import androidx.compose.ui.graphics.Color

// Seed: a network-diagnostic blue-teal, distinct from stock Material Baseline so the app
// reads as its own thing even on API levels/devices where dynamic color is unavailable.
internal val Seed = Color(0xFF0B6E7A)

internal val md_theme_light_primary = Color(0xFF00696F)
internal val md_theme_light_onPrimary = Color(0xFFFFFFFF)
internal val md_theme_light_primaryContainer = Color(0xFF6FF6FF)
internal val md_theme_light_onPrimaryContainer = Color(0xFF002022)
internal val md_theme_light_secondary = Color(0xFF4A6365)
internal val md_theme_light_onSecondary = Color(0xFFFFFFFF)
internal val md_theme_light_secondaryContainer = Color(0xFFCCE8EA)
internal val md_theme_light_onSecondaryContainer = Color(0xFF051F21)
internal val md_theme_light_tertiary = Color(0xFF4B607C)
internal val md_theme_light_onTertiary = Color(0xFFFFFFFF)
internal val md_theme_light_tertiaryContainer = Color(0xFFD3E4FF)
internal val md_theme_light_onTertiaryContainer = Color(0xFF041C35)
internal val md_theme_light_error = Color(0xFFBA1A1A)
internal val md_theme_light_onError = Color(0xFFFFFFFF)
internal val md_theme_light_errorContainer = Color(0xFFFFDAD6)
internal val md_theme_light_onErrorContainer = Color(0xFF410002)
internal val md_theme_light_background = Color(0xFFFAFDFC)
internal val md_theme_light_onBackground = Color(0xFF191C1C)
internal val md_theme_light_surface = Color(0xFFFAFDFC)
internal val md_theme_light_onSurface = Color(0xFF191C1C)
internal val md_theme_light_surfaceVariant = Color(0xFFDAE5E4)
internal val md_theme_light_onSurfaceVariant = Color(0xFF3F4948)
internal val md_theme_light_outline = Color(0xFF6F7978)

internal val md_theme_dark_primary = Color(0xFF4DD9E4)
internal val md_theme_dark_onPrimary = Color(0xFF00373A)
internal val md_theme_dark_primaryContainer = Color(0xFF004F54)
internal val md_theme_dark_onPrimaryContainer = Color(0xFF6FF6FF)
internal val md_theme_dark_secondary = Color(0xFFB0CCCE)
internal val md_theme_dark_onSecondary = Color(0xFF1B3436)
internal val md_theme_dark_secondaryContainer = Color(0xFF324B4C)
internal val md_theme_dark_onSecondaryContainer = Color(0xFFCCE8EA)
internal val md_theme_dark_tertiary = Color(0xFFB3C8EA)
internal val md_theme_dark_onTertiary = Color(0xFF1C314C)
internal val md_theme_dark_tertiaryContainer = Color(0xFF334764)
internal val md_theme_dark_onTertiaryContainer = Color(0xFFD3E4FF)
internal val md_theme_dark_error = Color(0xFFFFB4AB)
internal val md_theme_dark_onError = Color(0xFF690005)
internal val md_theme_dark_errorContainer = Color(0xFF93000A)
internal val md_theme_dark_onErrorContainer = Color(0xFFFFDAD6)
internal val md_theme_dark_background = Color(0xFF191C1C)
internal val md_theme_dark_onBackground = Color(0xFFE0E3E2)
internal val md_theme_dark_surface = Color(0xFF191C1C)
internal val md_theme_dark_onSurface = Color(0xFFE0E3E2)
internal val md_theme_dark_surfaceVariant = Color(0xFF3F4948)
internal val md_theme_dark_onSurfaceVariant = Color(0xFFBEC9C7)
internal val md_theme_dark_outline = Color(0xFF899391)

// Pure black, for the AMOLED true-black theme. Only background/surface pin to black, an
// OLED panel then draws those pixels off entirely; the rest of the dark palette carries over.
internal val md_theme_amoled_background = Color(0xFF000000)
internal val md_theme_amoled_surface = Color(0xFF000000)

// DarkColorScheme's "neutral" tones are generated from the teal Seed color, so they carry a
// faint green-teal cast even though they read as plain greys - about a 10-unit gap between the
// green channel and the others. That's invisible next to the old #191C1C background, but stark
// against pure black, especially on a high-fidelity OLED panel that renders the cast faithfully
// rather than crushing it. These are the same tones desaturated to true neutral grey at matching
// lightness, so contrast/legibility stays the same and only the hue cast is gone.
internal val md_theme_amoled_onBackground = Color(0xFFE2E2E2)
internal val md_theme_amoled_onSurface = Color(0xFFE2E2E2)
internal val md_theme_amoled_surfaceVariant = Color(0xFF454545)
internal val md_theme_amoled_onSurfaceVariant = Color(0xFFC5C5C5)
internal val md_theme_amoled_outline = Color(0xFF8F8F8F)
