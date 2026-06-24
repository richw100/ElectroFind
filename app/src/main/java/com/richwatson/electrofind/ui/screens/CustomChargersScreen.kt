package com.richwatson.electrofind.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.richwatson.electrofind.model.CustomCharger
import com.richwatson.electrofind.viewmodel.ChargerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomChargersScreen(
    chargerViewModel: ChargerViewModel,
    onAddNew: () -> Unit,
    onEdit: (Long) -> Unit
) {
    val state by chargerViewModel.state.collectAsState()
    var pendingDelete by remember { mutableStateOf<CustomCharger?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("My Chargers") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddNew) {
                Icon(Icons.Default.Add, contentDescription = "Add custom charger")
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (state.rawCustomChargers.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("No custom chargers yet", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Long-press on the Browse Map to add a charger at a specific location, or tap + below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.rawCustomChargers, key = { it.id }) { charger ->
                        CustomChargerRow(
                            charger = charger,
                            onEdit = { onEdit(charger.id) },
                            onDelete = { pendingDelete = charger }
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { charger ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete charger?") },
            text = { Text("\"${charger.name}\" will be permanently removed.") },
            confirmButton = {
                TextButton(onClick = {
                    chargerViewModel.deleteCustomCharger(charger.id)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun CustomChargerRow(
    charger: CustomCharger,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(charger.name, style = MaterialTheme.typography.titleSmall)
                if (charger.address.isNotEmpty()) {
                    Text(
                        charger.address,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(2.dp))
                val speedLabel = when {
                    charger.maxKilowatts < 7.4 -> "Slow"
                    charger.maxKilowatts < 22.0 -> "Fast"
                    else -> "Rapid"
                }
                Text(
                    "${charger.connectorType} · ${charger.maxKilowatts.toInt()} kW ($speedLabel) · £${"%.2f".format(charger.pricePerKwh)}/kWh",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (charger.connectionFeeGbp > 0 || charger.chargingRatePerMin > 0 || charger.idleRatePerMin > 0) {
                    val extras = buildList {
                        if (charger.connectionFeeGbp > 0) add("£${"%.2f".format(charger.connectionFeeGbp)} conn.")
                        if (charger.chargingRatePerMin > 0) add("£${"%.3f".format(charger.chargingRatePerMin)}/min")
                        if (charger.idleRatePerMin > 0) add("£${"%.3f".format(charger.idleRatePerMin)}/min idle")
                    }
                    Text(
                        extras.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
