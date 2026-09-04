package dev.enthusiastdev.netinspector.ui.screens.tools.dns

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import dev.enthusiastdev.netinspector.core.model.connection.NetworkTransport
import dev.enthusiastdev.netinspector.core.model.diagnostics.DnsQueryOutcome
import dev.enthusiastdev.netinspector.core.model.diagnostics.DnsRecord
import dev.enthusiastdev.netinspector.core.model.diagnostics.DnsRecordType
import dev.enthusiastdev.netinspector.core.model.diagnostics.QueriedDnsServer
import dev.enthusiastdev.netinspector.core.model.diagnostics.RegisteredDnsNetwork

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
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(modifier = Modifier.fillMaxHeight().widthIn(max = 600.dp)) {
            DnsForm(uiState, onNameChange, onRecordTypeChange, onCustomServerChange, onRunQuery)
            if (uiState.registeredNetworks.isNotEmpty()) {
                DnsRegisteredServersCard(
                    networks = uiState.registeredNetworks,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            DnsResults(uiState.outcome, uiState.queriedServer, uiState.activeTransportAtQuery)
        }
    }
}

/** design §9.4 - "registered on device": what the OS itself has configured, per active
 * network. Shown independently of [DnsResults] since it's a device-level fact, not a lookup
 * result - visible before the first query and unaffected by whether one succeeded. */
@Composable
private fun DnsRegisteredServersCard(
    networks: List<RegisteredDnsNetwork>,
    modifier: Modifier = Modifier,
) {
    InfoCard(title = "Registered on device", modifier = modifier) {
        networks.forEachIndexed { index, network ->
            if (index > 0) HorizontalDivider()
            Text(text = network.transport.label(), style = MaterialTheme.typography.titleSmall)
            InfoRow("IPv4", network.ipv4Servers.addressListLabel())
            InfoRow("IPv6", network.ipv6Servers.addressListLabel())
            InfoRow("Private DNS", network.privateDnsLabel())
        }
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
private fun ColumnScope.DnsResults(
    outcome: DnsQueryOutcome?,
    queriedServer: QueriedDnsServer?,
    activeTransportAtQuery: NetworkTransport?,
) {
    when (outcome) {
        is DnsQueryOutcome.Error ->
            Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (queriedServer != null) QueriedDnsServerCard(queriedServer, activeTransportAtQuery)
                Text(text = outcome.message, color = MaterialTheme.colorScheme.error)
            }
        is DnsQueryOutcome.Success ->
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (queriedServer != null) {
                    item { QueriedDnsServerCard(queriedServer, activeTransportAtQuery) }
                }
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

/** design §9.4 - "used for this lookup": the literal destination this specific query targeted,
 * as opposed to [DnsRegisteredServersCard]'s device-level configuration. */
@Composable
private fun QueriedDnsServerCard(
    queriedServer: QueriedDnsServer,
    activeTransportAtQuery: NetworkTransport?,
) {
    InfoCard(title = "Used for this lookup") {
        when (queriedServer) {
            is QueriedDnsServer.Explicit -> {
                InfoRow("Server", "${queriedServer.address.hostAddress}:${queriedServer.port}")
                InfoRow("Active network", activeTransportAtQuery?.label() ?: "Unknown")
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Matches registered", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = if (queriedServer.matchesRegistered) "Yes" else "No (custom server)",
                        style = MaterialTheme.typography.bodyMedium,
                        color =
                            if (queriedServer.matchesRegistered) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.tertiary
                            },
                    )
                }
            }
            QueriedDnsServer.SystemResolver -> {
                InfoRow("Server", "System resolver")
                Text(
                    text =
                        "The exact destination isn't observable from the app - see the " +
                            "\"Registered on device\" card above for what's configured.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
