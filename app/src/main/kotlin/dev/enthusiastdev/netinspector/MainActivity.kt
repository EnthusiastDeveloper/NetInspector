package dev.enthusiastdev.netinspector

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
            val themeMode by settingsViewModel.uiState.collectAsState()
            val systemInDarkTheme = isSystemInDarkTheme()
            val darkTheme =
                when (themeMode.themeMode) {
                    ThemeMode.SYSTEM -> systemInDarkTheme
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                }
            NetInspectorTheme(darkTheme = darkTheme) {
                NetInspectorApp()
            }
        }
    }
}
