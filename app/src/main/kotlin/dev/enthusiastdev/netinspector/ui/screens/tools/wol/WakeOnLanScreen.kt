package dev.enthusiastdev.netinspector.ui.screens.tools.wol

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.enthusiastdev.netinspector.data.persistence.wol.SavedWolTarget

@Composable
fun WakeOnLanRoute(
    modifier: Modifier = Modifier,
    viewModel: WakeOnLanViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    WakeOnLanScreen(
        uiState = uiState,
        onLabelChange = viewModel::updateLabel,
        onMacChange = viewModel::updateMac,
        onBroadcastAddressChange = viewModel::updateBroadcastAddress,
        onWake = viewModel::wake,
        onSave = viewModel::saveTarget,
        onDelete = viewModel::deleteTarget,
        modifier = modifier,
    )
}

@Composable
fun WakeOnLanScreen(
    uiState: WakeOnLanUiState,
    onLabelChange: (String) -> Unit,
    onMacChange: (String) -> Unit,
    onBroadcastAddressChange: (String) -> Unit,
    onWake: (mac: String, broadcastAddress: String) -> Unit,
    onSave: () -> Unit,
    onDelete: (SavedWolTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().widthIn(max = 600.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = uiState.label,
                    onValueChange = onLabelChange,
                    label = { Text("Label (for saving)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = uiState.mac,
                    onValueChange = onMacChange,
                    label = { Text("MAC address") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = uiState.broadcastAddress,
                    onValueChange = onBroadcastAddressChange,
                    label = { Text("Broadcast address") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onWake(uiState.mac, uiState.broadcastAddress) }) { Text("Wake") }
                    OutlinedButton(onClick = onSave) { Text("Save") }
                }
                uiState.lastResultMessage?.let { Text(text = it, style = MaterialTheme.typography.bodySmall) }
            }
        }

        if (uiState.savedTargets.isNotEmpty()) {
            item { HorizontalDivider() }
            item { Text(text = "Saved targets", style = MaterialTheme.typography.titleSmall) }
            items(uiState.savedTargets) { target -> SavedTargetRow(target, onWake, onDelete) }
        }
    }
}

@Composable
private fun SavedTargetRow(
    target: SavedWolTarget,
    onWake: (mac: String, broadcastAddress: String) -> Unit,
    onDelete: (SavedWolTarget) -> Unit,
) {
    Card {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = target.label, style = MaterialTheme.typography.bodyMedium)
                Text(text = target.mac, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = { onWake(target.mac, target.broadcastAddress) }) {
                Icon(Icons.Filled.PowerSettingsNew, contentDescription = "Wake ${target.label}")
            }
            IconButton(onClick = { onDelete(target) }) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete ${target.label}")
            }
        }
    }
}
