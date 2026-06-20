package com.richwatson.electrofind.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.richwatson.electrofind.api.models.ChargingLocation
import com.richwatson.electrofind.api.models.DataSource
import com.richwatson.electrofind.viewmodel.ChargerViewModel
import com.richwatson.electrofind.viewmodel.SortOrder
import com.richwatson.electrofind.viewmodel.SpeedFilter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    chargerViewModel: ChargerViewModel,
    onCompare: () -> Unit = {}
) {
    val state by chargerViewModel.state.collectAsState()
    val chargers = remember(state) { chargerViewModel.filteredSortedChargers }
    var showFilters by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("ElectroFind", style = MaterialTheme.typography.titleLarge)
                        if (state.searchQuery.isNotEmpty()) {
                            Text(
                                "${chargers.size} chargers · ${state.searchRadiusMiles} mi · ${state.searchQuery}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    if (state.dataSource == DataSource.BOTH &&
                        state.chargers.isNotEmpty() && state.ocmChargers.isNotEmpty()
                    ) {
                        IconButton(onClick = onCompare) {
                            Icon(Icons.AutoMirrored.Filled.CompareArrows, "Compare sources")
                        }
                    }
                    IconButton(onClick = { showFilters = !showFilters }) {
                        Icon(Icons.Default.FilterList, "Filter / Sort")
                    }
                }
            )
        }
    ) { padding ->
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
            state.ocmError?.let { err ->
                Text(
                    "OCM: $err",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                )
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
                    items(chargers, key = { "${it.sourceDisplay}_${it.pk}" }) { charger ->
                        ChargerCard(charger = charger, showSourceBadge = state.dataSource == DataSource.BOTH, currencySymbol = state.currencySymbol)
                    }
                }
            }
        }
    }
}

@Composable
internal fun FilterBar(vm: ChargerViewModel, showSort: Boolean = true) {
    val state by vm.state.collectAsState()
    var minInput by remember { mutableStateOf(state.minPriceKwh?.toString() ?: "") }
    var maxInput by remember { mutableStateOf(state.maxPriceKwh?.toString() ?: "") }

    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        if (showSort) {
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
        }
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
        Spacer(Modifier.height(6.dp))
        Text("Price per kWh (${state.currencySymbol})", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = minInput,
                onValueChange = { v ->
                    minInput = v
                    vm.setPriceFilter(v.toDoubleOrNull(), state.maxPriceKwh)
                },
                label = { Text("Min") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                prefix = { Text(state.currencySymbol) }
            )
            OutlinedTextField(
                value = maxInput,
                onValueChange = { v ->
                    maxInput = v
                    vm.setPriceFilter(state.minPriceKwh, v.toDoubleOrNull())
                },
                label = { Text("Max") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                prefix = { Text(state.currencySymbol) }
            )
        }
    }
}

@Composable
private fun ChargerCard(charger: ChargingLocation, showSourceBadge: Boolean = false, currencySymbol: String = "€") {
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            charger.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (showSourceBadge) {
                            val (badgeText, badgeColor) = when (charger.sourceDisplay) {
                                DataSource.OCM -> "OCM" to Color(0xFF1565C0)
                                else -> "EV" to Color(0xFF2E7D32)
                            }
                            Box(
                                modifier = Modifier
                                    .background(badgeColor.copy(alpha = 0.15f), shape = MaterialTheme.shapes.extraSmall)
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    badgeText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = badgeColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
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
                    if (charger.pricePerKwh == null && charger.pricingText != null) {
                        Text(
                            charger.pricingText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                PriceBadge(charger, currencySymbol)
            }

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val availColor = if (charger.hasAvailableEvse) Color(0xFF2E7D32) else Color(0xFFB71C1C)
                val availText = if (charger.hasAvailableEvse) "Available" else "In use"
                AssistChip(
                    onClick = {},
                    label = { Text(availText, style = MaterialTheme.typography.labelSmall) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = availColor.copy(alpha = 0.12f))
                )
                charger.maxKilowatts?.let { kw ->
                    AssistChip(
                        onClick = {},
                        label = { Text("${kw.toInt()} kW", style = MaterialTheme.typography.labelSmall) }
                    )
                }
                charger.connectorTypes.forEach { ct ->
                    AssistChip(
                        onClick = {},
                        label = { Text(ct, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            charger.connectionFeeMajor?.let { fee ->
                Text(
                    "+ %s%.2f connection fee".format(currencySymbol, fee),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (charger.isStale) {
                Text(
                    "! Cached data may be out of date",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            val context = LocalContext.current
            Row(modifier = Modifier.align(Alignment.End)) {
                val lat = charger.coordinates.latitude
                val lng = charger.coordinates.longitude
                TextButton(
                    onClick = {
                        val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(${charger.name})")
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Google Maps", style = MaterialTheme.typography.labelSmall)
                }
                if (charger.sourceDisplay == DataSource.ELECTROVERSE) {
                    charger.externalId?.let { extId ->
                        TextButton(
                            onClick = {
                                val uri = Uri.parse("https://electroverse.octopus.energy/map?extId=$extId")
                                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Electroverse", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PriceBadge(charger: ChargingLocation, currencySymbol: String = "€") {
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
                    "%s%.2f".format(currencySymbol, price),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = bgColor
                )
                Text("/kWh", style = MaterialTheme.typography.labelSmall, color = bgColor)
            }
        }
    }
}

internal val SortOrder.label: String get() = when (this) {
    SortOrder.PRICE_ASC -> "Cheapest first"
    SortOrder.PRICE_DESC -> "Most expensive"
    SortOrder.SPEED_DESC -> "Fastest first"
}

internal val SpeedFilter.label: String get() = when (this) {
    SpeedFilter.ALL -> "Any"
    SpeedFilter.FAST -> "7+ kW"
    SpeedFilter.RAPID -> "22+ kW"
    SpeedFilter.ULTRA -> "100+ kW"
}
