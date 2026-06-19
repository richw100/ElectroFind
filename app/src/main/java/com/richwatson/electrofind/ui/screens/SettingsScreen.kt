package com.richwatson.electrofind.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.richwatson.electrofind.api.models.DataSource
import com.richwatson.electrofind.preferences.AppPreferences
import com.richwatson.electrofind.viewmodel.ChargerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    chargerViewModel: ChargerViewModel,
    appPreferences: AppPreferences,
    onBack: () -> Unit
) {
    val state by chargerViewModel.state.collectAsState()
    var ocmKeyInput by remember { mutableStateOf(appPreferences.ocmApiKey) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Data source", style = MaterialTheme.typography.titleMedium)
            DataSource.entries.forEach { source ->
                val label = when (source) {
                    DataSource.ELECTROVERSE -> "Electroverse only"
                    DataSource.OCM -> "Open Charge Map only"
                    DataSource.BOTH -> "Both (interleaved)"
                }
                val desc = when (source) {
                    DataSource.ELECTROVERSE -> "Structured pricing, live availability via Electroverse"
                    DataSource.OCM -> "Community database, wider coverage, text-based pricing"
                    DataSource.BOTH -> "Show results from both — Electroverse sorted by price, OCM appended below"
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { chargerViewModel.setDataSource(source) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (state.dataSource == source)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = state.dataSource == source,
                            onClick = { chargerViewModel.setDataSource(source) }
                        )
                        Column(Modifier.padding(start = 8.dp)) {
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            Text("Search radius", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 3, 5, 10).forEach { miles ->
                    FilterChip(
                        selected = state.searchRadiusMiles == miles,
                        onClick = { chargerViewModel.setSearchRadius(miles) },
                        label = { Text("$miles mi") }
                    )
                }
            }

            HorizontalDivider()

            Text("Open Charge Map API key", style = MaterialTheme.typography.titleMedium)
            Text(
                "Required for OCM searches. Register free at openchargemap.org",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = ocmKeyInput,
                onValueChange = { ocmKeyInput = it },
                label = { Text("API key") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    appPreferences.ocmApiKey = ocmKeyInput
                })
            )
            Button(
                onClick = { appPreferences.ocmApiKey = ocmKeyInput },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Save key")
            }
        }
    }
}
