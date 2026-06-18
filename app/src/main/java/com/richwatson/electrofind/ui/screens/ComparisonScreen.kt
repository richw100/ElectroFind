package com.richwatson.electrofind.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.richwatson.electrofind.api.models.ChargingLocation
import com.richwatson.electrofind.viewmodel.ChargerViewModel
import kotlin.math.*

data class ComparisonEntry(
    val lat: Double,
    val lng: Double,
    val name: String,
    val electroverse: ChargingLocation?,
    val ocm: ChargingLocation?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComparisonScreen(chargerViewModel: ChargerViewModel, onBack: () -> Unit) {
    val state by chargerViewModel.state.collectAsState()
    val entries = remember(state.chargers, state.ocmChargers) {
        buildComparison(state.chargers, state.ocmChargers)
    }

    val matched = entries.count { it.electroverse != null && it.ocm != null }
    val evOnly = entries.count { it.electroverse != null && it.ocm == null }
    val ocmOnly = entries.count { it.electroverse == null && it.ocm != null }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Compare sources") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Summary row
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "$matched matched · $evOnly Electroverse only · $ocmOnly OCM only",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(entries) { entry ->
                    ComparisonCard(entry)
                }
            }
        }
    }
}

@Composable
private fun ComparisonCard(entry: ComparisonEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(entry.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            if (entry.electroverse != null) {
                Text(
                    "${entry.electroverse.address}, ${entry.electroverse.city}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (entry.ocm != null) {
                Text(
                    "${entry.ocm.address}, ${entry.ocm.city}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Electroverse column
                Column(Modifier.weight(1f)) {
                    Text(
                        "Electroverse",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (entry.electroverse == null) {
                        Text("No match", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        val ev = entry.electroverse
                        val price = ev.pricePerKwh
                        Text(
                            if (price != null) "€%.2f/kWh".format(price) else "Price unknown",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(ev.operator.name, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            if (ev.hasAvailableEvse) "Available" else "In use",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                VerticalDivider(modifier = Modifier.height(80.dp))

                // OCM column
                Column(Modifier.weight(1f)) {
                    Text(
                        "Open Charge Map",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    if (entry.ocm == null) {
                        Text("No match", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        val ocm = entry.ocm
                        Text(
                            ocm.pricingText ?: "Price unknown",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(ocm.operator.name, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val connCount = ocm.evses.edges.sumOf { it.node.connectors.edges.size }
                        if (connCount > 0) {
                            Text("$connCount connector(s)", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

private fun buildComparison(
    evChargers: List<ChargingLocation>,
    ocmChargers: List<ChargingLocation>
): List<ComparisonEntry> {
    val matched = mutableListOf<ComparisonEntry>()
    val unmatchedOcm = ocmChargers.toMutableList()

    evChargers.forEach { ev ->
        val evLat = ev.coordinates.latitude
        val evLng = ev.coordinates.longitude
        val nearest = unmatchedOcm.minByOrNull { ocm ->
            haversineMeters(evLat, evLng, ocm.coordinates.latitude, ocm.coordinates.longitude)
        }
        if (nearest != null && haversineMeters(evLat, evLng, nearest.coordinates.latitude, nearest.coordinates.longitude) < 150) {
            matched.add(ComparisonEntry(evLat, evLng, ev.name, ev, nearest))
            unmatchedOcm.remove(nearest)
        } else {
            matched.add(ComparisonEntry(evLat, evLng, ev.name, ev, null))
        }
    }
    unmatchedOcm.forEach { ocm ->
        matched.add(
            ComparisonEntry(
                ocm.coordinates.latitude, ocm.coordinates.longitude,
                ocm.name, null, ocm
            )
        )
    }
    return matched
}

private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val R = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    return R * 2 * atan2(sqrt(a), sqrt(1 - a))
}
