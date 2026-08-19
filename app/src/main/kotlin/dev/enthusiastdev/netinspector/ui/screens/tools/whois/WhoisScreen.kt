package dev.enthusiastdev.netinspector.ui.screens.tools.whois

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.enthusiastdev.netinspector.core.model.diagnostics.WhoisHop
import dev.enthusiastdev.netinspector.core.model.diagnostics.WhoisOutcome

@Composable
fun WhoisRoute(
    modifier: Modifier = Modifier,
    viewModel: WhoisViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    WhoisScreen(
        uiState = uiState,
        onTargetChange = viewModel::updateTarget,
        onRunQuery = viewModel::runQuery,
        modifier = modifier,
    )
}

@Composable
fun WhoisScreen(
    uiState: WhoisUiState,
    onTargetChange: (String) -> Unit,
    onRunQuery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().widthIn(max = 600.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = uiState.target,
                onValueChange = onTargetChange,
                label = { Text("Domain or IP") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = onRunQuery) { Text("Query") }
        }

        when (val outcome = uiState.outcome) {
            is WhoisOutcome.Error ->
                Text(
                    text = outcome.message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                )
            is WhoisOutcome.Success ->
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(outcome.hops) { hop -> HopSection(hop) }
                }
            null -> {}
        }
    }
}

@Composable
private fun HopSection(hop: WhoisHop) {
    Column {
        Text(text = hop.server, style = MaterialTheme.typography.titleSmall)
        Text(text = hop.responseText, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
    }
}
