package dev.enthusiastdev.netinspector.ui.screens.tools.dns

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import dev.enthusiastdev.netinspector.core.model.diagnostics.DnsQueryOutcome
import dev.enthusiastdev.netinspector.core.model.diagnostics.DnsRecord
import dev.enthusiastdev.netinspector.core.model.diagnostics.DnsRecordType

@Composable
fun DnsRoute(
    modifier: Modifier = Modifier,
    viewModel: DnsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    DnsScreen(
        uiState = uiState,
        onNameChange = viewModel::updateName,
        onRecordTypeChange = viewModel::updateRecordType,
        onCustomServerChange = viewModel::updateCustomServer,
        onRunQuery = viewModel::runQuery,
        modifier = modifier,
    )
}

@Composable
fun DnsScreen(
    uiState: DnsUiState,
    onNameChange: (String) -> Unit,
    onRecordTypeChange: (DnsRecordType) -> Unit,
    onCustomServerChange: (String) -> Unit,
    onRunQuery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().widthIn(max = 600.dp)) {
        DnsForm(uiState, onNameChange, onRecordTypeChange, onCustomServerChange, onRunQuery)
        DnsResults(uiState.outcome)
    }
}

@Composable
private fun DnsForm(
    uiState: DnsUiState,
    onNameChange: (String) -> Unit,
    onRecordTypeChange: (DnsRecordType) -> Unit,
    onCustomServerChange: (String) -> Unit,
    onRunQuery: () -> Unit,
) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val nameLabel = if (uiState.recordType == DnsRecordType.PTR) "IPv4 address" else "Name"
            OutlinedTextField(
                value = uiState.name,
                onValueChange = onNameChange,
                label = { Text(nameLabel) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = onRunQuery) { Text("Query") }
        }
        OutlinedTextField(
            value = uiState.customServer,
            onValueChange = onCustomServerChange,
            label = { Text("Server (blank = system resolver)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DnsRecordType.entries.forEach { type ->
                FilterChip(
                    selected = type == uiState.recordType,
                    onClick = { onRecordTypeChange(type) },
                    label = { Text(type.name) },
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.DnsResults(outcome: DnsQueryOutcome?) {
    when (outcome) {
        is DnsQueryOutcome.Error ->
            Text(
                text = outcome.message,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        is DnsQueryOutcome.Success ->
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    InfoCard(title = "Query time") {
                        InfoRow("Elapsed", "%.1f ms".format(outcome.queryTimeMs))
                        InfoRow("Answers", "${outcome.answers.size}")
                    }
                }
                if (outcome.answers.isEmpty()) {
                    item { Text("No records returned", style = MaterialTheme.typography.bodyMedium) }
                } else {
                    items(outcome.answers) { record -> DnsRecordRow(record) }
                }
            }
        null -> {}
    }
}

@Composable
private fun DnsRecordRow(record: DnsRecord) {
    val typeLabel = record.type?.name ?: "TYPE${record.rawTypeCode}"
    Text(
        text = "${record.name}  $typeLabel  ttl=${record.ttlSeconds}  ${record.data}",
        fontFamily = FontFamily.Monospace,
        style = MaterialTheme.typography.bodySmall,
    )
}
