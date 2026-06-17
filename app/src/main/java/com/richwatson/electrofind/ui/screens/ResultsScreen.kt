package com.richwatson.electrofind.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.richwatson.electrofind.api.models.ChargingLocation
import com.richwatson.electrofind.viewmodel.ChargerViewModel
import com.richwatson.electrofind.viewmodel.SortOrder
import com.richwatson.electrofind.viewmodel.SpeedFilter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    chargerViewModel: ChargerViewModel,
    onBack: () -> Unit
) {
    val state by chargerViewModel.state.collectAsState()
    // remember(state) forces ResultsScreen to read `state` in its own scope, so when state
    // changes (chargers added, loading completes) ResultsScreen itself recomposes and chargers
    // is recomputed — without this, only nested Scaffold lambdas recompose and chargers stays stale
    val chargers = remember(state) { chargerViewModel.filteredSortedChargers }
    var showFilters by remember { mutableStateOf(false) }
    var showMap by remember { mutableStateOf(false) }

    val goBack = {
        chargerViewModel.clearResults()
        onBack()
    }
    BackHandler { goBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${chargers.size} chargers within 3 miles of ${state.searchQuery}") },
                navigationIcon = {
                    IconButton(onClick = goBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (!showMap) {
                        IconButton(onClick = { showFilters = !showFilters }) {
                            Icon(Icons.Default.FilterList, "Filter / Sort")
                        }
                    }
                    IconButton(onClick = { showMap = !showMap; showFilters = false }) {
                        Icon(
                            if (showMap) Icons.AutoMirrored.Filled.ViewList else Icons.Default.Map,
                            contentDescription = if (showMap) "List view" else "Map view"
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (showMap) {
            if (state.searchLat != 0.0 || state.searchLng != 0.0) {
                ChargerMapView(
                    chargers = chargers,
                    searchLat = state.searchLat,
                    searchLng = state.searchLng,
                    modifier = Modifier.padding(padding).fillMaxSize(),
                    onLocationSelected = { lat, lng ->
                        chargerViewModel.searchByCoordinates(lat, lng)
                    }
                )
            } else {
                Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Map unavailable — search coordinates not set.")
                }
            }
        } else {
            Column(Modifier.padding(padding).fillMaxSize()) {
                if (state.isLoading) {
                    if (state.fetchProgress > 0f) {
                        LinearProgressIndicator(
                            progress = { state.fetchProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    if (state.loadingStatus.isNotEmpty()) {
                        Text(
                            state.loadingStatus,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp)
                        )
                    }
                }
                if (showFilters) {
                    FilterBar(chargerViewModel)
                    HorizontalDivider()
                }

                if (state.isLoading && chargers.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator()
                            if (state.loadingStatus.isNotEmpty()) {
                                Text(
                                    state.loadingStatus,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else if (chargers.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(state.error ?: "No chargers found. Try a different location or connector filter.")
                        if (state.loadingStatus.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                state.loadingStatus,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(chargers, key = { it.pk }) { charger ->
                            ChargerCard(charger)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterBar(vm: ChargerViewModel) {
    val state by vm.state.collectAsState()
    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        // Sort order
        Text("Sort by", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SortOrder.entries.forEach { order ->
                FilterChip(
                    selected = state.sortOrder == order,
                    onClick = { vm.setSortOrder(order) },
                    label = { Text(order.label) }
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        // Speed filter
        Text("Min speed", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SpeedFilter.entries.forEach { filter ->
                FilterChip(
                    selected = state.speedFilter == filter,
                    onClick = { vm.setSpeedFilter(filter) },
                    label = { Text(filter.label) }
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        // Connector filter
        Text("Connector", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("ALL", "CCS", "Type 2", "CHAdeMO").forEach { connector ->
                FilterChip(
                    selected = state.connectorFilter == connector,
                    onClick = { vm.setConnectorFilter(connector) },
                    label = { Text(connector) }
                )
            }
        }
    }
}

@Composable
private fun ChargerCard(charger: ChargingLocation) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        charger.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        charger.operator.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "${charger.address}, ${charger.city}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Price badge
                PriceBadge(charger)
            }

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Availability
                val availColor = if (charger.hasAvailableEvse) Color(0xFF2E7D32) else Color(0xFFB71C1C)
                val availText = if (charger.hasAvailableEvse) "Available" else "In use"
                AssistChip(
                    onClick = {},
                    label = { Text(availText, style = MaterialTheme.typography.labelSmall) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = availColor.copy(alpha = 0.12f))
                )
                // Max speed
                charger.maxKilowatts?.let { kw ->
                    AssistChip(
                        onClick = {},
                        label = { Text("${kw.toInt()} kW", style = MaterialTheme.typography.labelSmall) }
                    )
                }
                // Connectors
                charger.connectorTypes.forEach { ct ->
                    AssistChip(
                        onClick = {},
                        label = { Text(ct, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            // Connection fee note
            charger.connectionFeeMajor?.let { fee ->
                Text(
                    "+ €%.2f connection fee".format(fee),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PriceBadge(charger: ChargingLocation) {
    val price = charger.pricePerKwh
    val bgColor = when {
        price == null -> MaterialTheme.colorScheme.surfaceVariant
        price == 0.0 && charger.evses.edges.flatMap { it.node.connectors.edges }
            .any { it.node.isChargingFree } -> Color(0xFF1B5E20)
        price < 0.35 -> Color(0xFF2E7D32)
        price < 0.55 -> Color(0xFFF57F17)
        else -> Color(0xFFB71C1C)
    }
    Box(
        modifier = Modifier
            .background(bgColor.copy(alpha = 0.15f), shape = MaterialTheme.shapes.small)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        if (price == null) {
            Text("?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = bgColor)
        } else if (price == 0.0) {
            Text("FREE", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = bgColor)
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "€%.2f".format(price),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = bgColor
                )
                Text("/kWh", style = MaterialTheme.typography.labelSmall, color = bgColor)
            }
        }
    }
}

private val SortOrder.label: String get() = when (this) {
    SortOrder.PRICE_ASC -> "Cheapest first"
    SortOrder.PRICE_DESC -> "Most expensive"
    SortOrder.SPEED_DESC -> "Fastest first"
}

private val SpeedFilter.label: String get() = when (this) {
    SpeedFilter.ALL -> "Any"
    SpeedFilter.FAST -> "7+ kW"
    SpeedFilter.RAPID -> "22+ kW"
    SpeedFilter.ULTRA -> "100+ kW"
}
