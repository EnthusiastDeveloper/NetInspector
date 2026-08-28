package dev.enthusiastdev.netinspector

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.AndroidEntryPoint
import dev.enthusiastdev.netinspector.core.designsystem.theme.NetInspectorTheme
import dev.enthusiastdev.netinspector.core.model.settings.ThemeMode
import dev.enthusiastdev.netinspector.ui.NetInspectorApp
import dev.enthusiastdev.netinspector.ui.screens.settings.SettingsViewModel

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // design §10 - the settings screen's theme preference; reads the same
            // [SettingsViewModel] the Settings screen itself edits rather than a second
            // preference reader, so the two can never disagree about the current mode.
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val settingsState by settingsViewModel.uiState.collectAsState()
            val systemInDarkTheme = isSystemInDarkTheme()
            val darkTheme =
                when (settingsState.themeMode) {
                    ThemeMode.SYSTEM -> systemInDarkTheme
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK, ThemeMode.AMOLED -> true
                }
            val trueBlack = settingsState.themeMode == ThemeMode.AMOLED
            NetInspectorTheme(darkTheme = darkTheme, trueBlack = trueBlack) {
                // docs/ideas.md #36 - app-wide text/UI scale, applied once here so every
                // screen picks it up with no per-screen changes. Multiplies onto the current
                // fontScale rather than replacing it, so this stays additive with (not a
                // replacement for) the system's own accessibility font-size setting.
                val baseDensity = LocalDensity.current
                val scaledDensity =
                    Density(
                        density = baseDensity.density,
                        fontScale = baseDensity.fontScale * settingsState.uiFontScale,
                    )
                CompositionLocalProvider(LocalDensity provides scaledDensity) {
                    NetInspectorApp()
                }
            }
        }
    }
}
