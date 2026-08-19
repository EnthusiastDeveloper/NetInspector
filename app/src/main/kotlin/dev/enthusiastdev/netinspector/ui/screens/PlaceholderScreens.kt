package dev.enthusiastdev.netinspector.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

// Real screens land in Phases 1, 3, 5 and 7 (design §11.1). These placeholders exist only to
// prove the Phase 0 navigation shell - four destinations, adaptive nav container, back stack.
@Composable
fun PlaceholderScreen(
    label: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = label, style = MaterialTheme.typography.headlineSmall)
    }
}
