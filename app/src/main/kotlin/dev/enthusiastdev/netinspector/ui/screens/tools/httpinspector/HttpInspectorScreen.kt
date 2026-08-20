package dev.enthusiastdev.netinspector.ui.screens.tools.httpinspector

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.enthusiastdev.netinspector.core.designsystem.component.InfoCard
import dev.enthusiastdev.netinspector.core.designsystem.component.InfoRow
import dev.enthusiastdev.netinspector.core.model.diagnostics.HttpHop
import dev.enthusiastdev.netinspector.core.model.diagnostics.HttpInspectionOutcome
import dev.enthusiastdev.netinspector.core.model.diagnostics.TlsCertificateInfo

@Composable
fun HttpInspectorRoute(
    modifier: Modifier = Modifier,
    viewModel: HttpInspectorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    HttpInspectorScreen(
        uiState = uiState,
        onUrlChange = viewModel::updateUrl,
        onRunInspection = viewModel::runInspection,
        modifier = modifier,
    )
}

@Composable
fun HttpInspectorScreen(
    uiState: HttpInspectorUiState,
    onUrlChange: (String) -> Unit,
    onRunInspection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(modifier = Modifier.fillMaxHeight().widthIn(max = 600.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = uiState.url,
                    onValueChange = onUrlChange,
                    label = { Text("URL") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = onRunInspection) { Text("Inspect") }
            }

            when (val outcome = uiState.outcome) {
                is HttpInspectionOutcome.Error ->
                    Text(
                        text = outcome.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp),
                    )
                is HttpInspectionOutcome.Success ->
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        outcome.tls?.let { item { TlsCard(it) } }
                        items(outcome.chain) { hop -> HopCard(hop, isFinal = hop === outcome.chain.last()) }
                    }
                null -> {}
            }
        }
    }
}

@Composable
private fun HopCard(
    hop: HttpHop,
    isFinal: Boolean,
) {
    InfoCard(title = "${hop.statusLine}${if (isFinal) "" else " (redirect)"}") {
        Text(text = hop.url, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        hop.headers.forEach { (name, value) -> InfoRow(name, value) }
    }
}

@Composable
private fun TlsCard(tls: TlsCertificateInfo) {
    InfoCard(title = "TLS certificate") {
        InfoRow("Subject", tls.subject)
        InfoRow("Issuer", tls.issuer)
        InfoRow("Valid from", tls.validFrom)
        InfoRow("Valid until", tls.validUntil)
    }
}
