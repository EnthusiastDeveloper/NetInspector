package dev.enthusiastdev.netinspector.ui.screens.tools.dns

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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
    val focusManager = LocalFocusManager.current
    // Drop the soft keyboard before the results render - otherwise the freshly-appended cards
    // land behind the IME and the screen looks like it did nothing (the original bug report).
    val runQuery = {
        focusManager.clearFocus()
        onRunQuery()
    }
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(modifier = Modifier.fillMaxHeight().widthIn(max = 600.dp)) {
            DnsForm(uiState, onNameChange, onRecordTypeChange, onCustomServerChange, runQuery)
            // One scroll container for the "Registered DNS servers" card and every result row.
            // The registered card is variable height (one entry per active Wi-Fi/cellular/
            // Ethernet network - three or more on a phone with two SIMs up), so it can't sit in
            // a fixed region above the results without pushing them off a short screen entirely.
            // imePadding keeps the last rows scrollable clear of the keyboard while a field is
            // focused.
            DnsResults(uiState, modifier = Modifier.weight(1f).fillMaxWidth().imePadding())
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
                // Uri type: no autocapitalisation or autocorrect, which on some keyboards
                // mangle a typed hostname (a trailing space inserted after every dot).
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onRunQuery() }),
                modifier = Modifier.weight(1f),
            )
            Button(onClick = onRunQuery) { Text("Query") }
        }
        OutlinedTextField(
            value = uiState.customServer,
            onValueChange = onCustomServerChange,
            label = { Text("Server") },
            // The placeholder says where a blank field actually sends: the active network's
            // first registered server, or the system resolver when there isn't one to name.
            placeholder = {
                Text(
                    uiState.defaultServerHint?.let { "blank = $it (first registered)" }
                        ?: "blank = system resolver",
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onRunQuery() }),
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
private fun DnsResults(
    uiState: DnsUiState,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (uiState.registeredNetworks.isNotEmpty()) {
            item(key = "registered") { DnsRegisteredServersCard(uiState.registeredNetworks) }
        }
        if (uiState.isRunning) {
            item(key = "running") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Querying...", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        dnsOutcomeItems(uiState)
    }
}

private fun LazyListScope.dnsOutcomeItems(uiState: DnsUiState) {
    val queriedServer = uiState.queriedServer
    val respondedFrom = (uiState.outcome as? DnsQueryOutcome.Success)?.respondedFrom
    if (queriedServer != null) {
        item(key = "queried") {
            QueriedDnsServerCard(queriedServer, uiState.activeTransportAtQuery, respondedFrom, uiState.activePrivateDns)
        }
    }
    when (val outcome = uiState.outcome) {
        is DnsQueryOutcome.Error ->
            item(key = "error") {
                Text(text = outcome.message, color = MaterialTheme.colorScheme.error)
            }
        is DnsQueryOutcome.Success -> {
            item(key = "queryTime") {
                InfoCard(title = "Query time") {
                    InfoRow("Elapsed", "%.1f ms".format(outcome.queryTimeMs))
                    InfoRow("Answers", "${outcome.answers.size}")
                }
            }
            if (outcome.answers.isEmpty()) {
                item(key = "noRecords") {
                    Text("No records returned", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                items(outcome.answers) { record -> DnsRecordRow(record) }
            }
        }
        null -> {}
    }
}

/** design §9.4's "Registered DNS servers": what the OS itself has configured, per active
 * network. Shown independently of the lookup result since it's a device-level fact - visible
 * before the first query and unaffected by whether one succeeded. */
@Composable
private fun DnsRegisteredServersCard(
    networks: List<RegisteredDnsNetwork>,
    modifier: Modifier = Modifier,
) {
    InfoCard(title = "Registered DNS servers", modifier = modifier) {
        networks.forEachIndexed { index, network ->
            if (index > 0) HorizontalDivider()
            Text(text = network.transport.label(), style = MaterialTheme.typography.titleSmall)
            InfoRow("IPv4", network.ipv4Servers.addressListLabel())
            InfoRow("IPv6", network.ipv6Servers.addressListLabel())
            InfoRow("Private DNS", network.privateDnsLabel())
        }
    }
}

/** design §9.4 - "used for this lookup": the literal destination this specific query targeted,
 * as opposed to [DnsRegisteredServersCard]'s device-level configuration. [respondedFrom] is the
 * datagram source of the reply on the raw-socket path (null for the system resolver). */
@Composable
private fun QueriedDnsServerCard(
    queriedServer: QueriedDnsServer,
    activeTransportAtQuery: NetworkTransport?,
    respondedFrom: String?,
    activePrivateDns: Boolean,
) {
    InfoCard(title = "Used for this lookup") {
        when (queriedServer) {
            is QueriedDnsServer.Explicit -> {
                InfoRow("Server", "${queriedServer.address.hostAddress}:${queriedServer.port}")
                if (queriedServer.autoSelected) {
                    InfoRow("Chosen", "first registered server (field left blank)")
                }
                InfoRow("Active network", activeTransportAtQuery?.label() ?: "Unknown")
                respondedFrom?.let { InfoRow("Replied from", it) }
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
                        if (activePrivateDns) {
                            "Private DNS is on, so the query is encrypted and sent by the system " +
                                "resolver; its destination isn't observable from the app."
                        } else {
                            "The exact destination isn't observable from the app - see the " +
                                "\"Registered DNS servers\" card above for what's configured."
                        },
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
