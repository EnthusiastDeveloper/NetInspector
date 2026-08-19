package dev.enthusiastdev.netinspector.ui.screens.tools.subnetcalc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.enthusiastdev.netinspector.core.common.net.Ipv4Subnet
import dev.enthusiastdev.netinspector.core.common.net.VlsmAllocation
import dev.enthusiastdev.netinspector.core.designsystem.component.InfoCard
import dev.enthusiastdev.netinspector.core.designsystem.component.InfoRow

@Composable
fun SubnetCalculatorRoute(
    modifier: Modifier = Modifier,
    viewModel: SubnetCalculatorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    SubnetCalculatorScreen(
        uiState = uiState,
        onAddressChange = viewModel::updateAddress,
        onPrefixChange = viewModel::updatePrefix,
        onVlsmChange = viewModel::updateVlsm,
        modifier = modifier,
    )
}

@Composable
fun SubnetCalculatorScreen(
    uiState: SubnetCalculatorUiState,
    onAddressChange: (String) -> Unit,
    onPrefixChange: (String) -> Unit,
    onVlsmChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().widthIn(max = 600.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = uiState.addressInput,
                    onValueChange = onAddressChange,
                    label = { Text("Address") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = uiState.prefixInput,
                    onValueChange = onPrefixChange,
                    label = { Text("Prefix") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(0.35f),
                )
            }
        }

        uiState.errorMessage?.let {
            item { Text(text = it, color = MaterialTheme.colorScheme.error) }
        }

        uiState.subnet?.let { subnet ->
            item { SubnetInfoCard(subnet, uiState.netmaskText.orEmpty()) }
        }

        item {
            OutlinedTextField(
                value = uiState.vlsmInput,
                onValueChange = onVlsmChange,
                label = { Text("VLSM: host counts, comma-separated (e.g. 100,50,20)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        uiState.vlsmAllocations?.let { allocations ->
            items(allocations) { allocation -> VlsmRow(allocation) }
        }
    }
}

@Composable
private fun SubnetInfoCard(
    subnet: Ipv4Subnet,
    netmaskText: String,
) {
    InfoCard(title = "Network") {
        InfoRow("Network address", "${subnet.networkAddress.hostAddress}/${subnet.prefixLength}")
        InfoRow("Netmask", netmaskText)
        InfoRow("Broadcast", subnet.broadcastAddress?.hostAddress ?: "none (/31 or /32)")
        val range = subnet.usableHostRange
        InfoRow("Usable range", if (range != null) "${range.start.hostAddress} - ${range.end.hostAddress}" else "none")
        InfoRow("Usable hosts", "${subnet.hostCount}")
    }
}

@Composable
private fun VlsmRow(allocation: VlsmAllocation) {
    val subnet = allocation.subnet
    val text =
        "${allocation.requestedHosts} hosts -> ${subnet.networkAddress.hostAddress}/${subnet.prefixLength} " +
            "(${subnet.hostCount} usable)"
    Text(text = text, style = MaterialTheme.typography.bodySmall)
}
