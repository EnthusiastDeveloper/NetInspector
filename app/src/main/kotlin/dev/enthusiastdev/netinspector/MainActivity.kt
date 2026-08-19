package dev.enthusiastdev.netinspector

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import dev.enthusiastdev.netinspector.core.designsystem.theme.NetInspectorTheme
import dev.enthusiastdev.netinspector.ui.NetInspectorApp

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NetInspectorTheme {
                NetInspectorApp()
            }
        }
    }
}
