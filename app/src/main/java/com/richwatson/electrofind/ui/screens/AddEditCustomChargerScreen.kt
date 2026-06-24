package com.richwatson.electrofind.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.richwatson.electrofind.model.CustomCharger
import com.richwatson.electrofind.viewmodel.ChargerViewModel

private val CONNECTOR_TYPES = listOf("CCS", "Type 2", "CHAdeMO", "Type 1")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditCustomChargerScreen(
    chargerViewModel: ChargerViewModel,
    existingId: Long? = null,
    initialLat: Double? = null,
    initialLng: Double? = null,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    val state by chargerViewModel.state.collectAsState()
    val existing = remember(existingId, state.rawCustomChargers) {
        existingId?.let { id -> state.rawCustomChargers.find { it.id == id } }
    }

    var name by remember(existing) { mutableStateOf(existing?.name ?: "") }
    var address by remember(existing) { mutableStateOf(existing?.address ?: "") }
    var lat by remember(existing, initialLat) { mutableStateOf((existing?.latitude ?: initialLat)?.toString() ?: "") }
    var lng by remember(existing, initialLng) { mutableStateOf((existing?.longitude ?: initialLng)?.toString() ?: "") }
    var pricePerKwh by remember(existing) { mutableStateOf(if (existing != null) "%.2f".format(existing.pricePerKwh) else "") }
    var connectionFee by remember(existing) { mutableStateOf(if (existing?.connectionFeeGbp != null && existing.connectionFeeGbp > 0) "%.2f".format(existing.connectionFeeGbp) else "") }
    var chargingRate by remember(existing) { mutableStateOf(if (existing?.chargingRatePerMin != null && existing.chargingRatePerMin > 0) "%.3f".format(existing.chargingRatePerMin) else "") }
    var idleRate by remember(existing) { mutableStateOf(if (existing?.idleRatePerMin != null && existing.idleRatePerMin > 0) "%.3f".format(existing.idleRatePerMin) else "") }
    var maxKw by remember(existing) { mutableStateOf(if (existing != null) "%.0f".format(existing.maxKilowatts) else "50") }
    var connectorType by remember(existing) { mutableStateOf(existing?.connectorType ?: "CCS") }
    var connectorExpanded by remember { mutableStateOf(false) }

    var nameError by remember { mutableStateOf<String?>(null) }
    var latLngError by remember { mutableStateOf<String?>(null) }

    val isEdit = existing != null
    val title = if (isEdit) "Edit Charger" else "Add Custom Charger"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; nameError = null },
                label = { Text("Name *") },
                modifier = Modifier.fillMaxWidth(),
                isError = nameError != null,
                supportingText = nameError?.let { { Text(it) } }
            )

            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text("Address (optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            Text("Location", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = lat,
                    onValueChange = { lat = it; latLngError = null },
                    label = { Text("Latitude") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = latLngError != null
                )
                OutlinedTextField(
                    value = lng,
                    onValueChange = { lng = it; latLngError = null },
                    label = { Text("Longitude") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = latLngError != null
                )
            }
            if (latLngError != null) {
                Text(latLngError!!, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }

            Text("Pricing", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = pricePerKwh,
                onValueChange = { pricePerKwh = it },
                label = { Text("Price per kWh (£)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                placeholder = { Text("e.g. 0.35") }
            )
            OutlinedTextField(
                value = connectionFee,
                onValueChange = { connectionFee = it },
                label = { Text("Connection fee (£, optional)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                placeholder = { Text("e.g. 1.00") }
            )
            OutlinedTextField(
                value = chargingRate,
                onValueChange = { chargingRate = it },
                label = { Text("Charging rate per min (£, optional)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                placeholder = { Text("e.g. 0.010") }
            )
            OutlinedTextField(
                value = idleRate,
                onValueChange = { idleRate = it },
                label = { Text("Idle rate per min (£, optional)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                placeholder = { Text("e.g. 0.010") }
            )

            Text("Hardware", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = maxKw,
                onValueChange = { maxKw = it },
                label = { Text("Max power (kW)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                placeholder = { Text("e.g. 50") }
            )

            ExposedDropdownMenuBox(
                expanded = connectorExpanded,
                onExpandedChange = { connectorExpanded = it }
            ) {
                OutlinedTextField(
                    value = connectorType,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Connector type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = connectorExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = connectorExpanded,
                    onDismissRequest = { connectorExpanded = false }
                ) {
                    CONNECTOR_TYPES.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type) },
                            onClick = { connectorType = type; connectorExpanded = false }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        nameError = null; latLngError = null
                        val trimmedName = name.trim()
                        if (trimmedName.isEmpty()) { nameError = "Name is required"; return@Button }
                        val parsedLat = lat.toDoubleOrNull()
                        val parsedLng = lng.toDoubleOrNull()
                        if (parsedLat == null || parsedLng == null) { latLngError = "Valid latitude and longitude required"; return@Button }

                        val charger = CustomCharger(
                            id = existing?.id ?: 0L,
                            name = trimmedName,
                            latitude = parsedLat,
                            longitude = parsedLng,
                            address = address.trim(),
                            pricePerKwh = pricePerKwh.toDoubleOrNull() ?: 0.0,
                            connectionFeeGbp = connectionFee.toDoubleOrNull() ?: 0.0,
                            chargingRatePerMin = chargingRate.toDoubleOrNull() ?: 0.0,
                            idleRatePerMin = idleRate.toDoubleOrNull() ?: 0.0,
                            maxKilowatts = maxKw.toDoubleOrNull() ?: 50.0,
                            connectorType = connectorType
                        )
                        if (isEdit) chargerViewModel.updateCustomCharger(charger)
                        else chargerViewModel.addCustomCharger(charger)
                        onSave()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isEdit) "Save changes" else "Add charger")
                }
            }
        }
    }
}
