package com.richwatson.electrofind.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import androidx.compose.ui.unit.dp
import com.richwatson.electrofind.api.models.ChargingLocation
import com.richwatson.electrofind.model.CarProfile
import com.richwatson.electrofind.model.RouteStop
import com.richwatson.electrofind.model.Trip
import com.richwatson.electrofind.util.KonaChargeCurve
import com.richwatson.electrofind.viewmodel.ChargerViewModel
import com.richwatson.electrofind.viewmodel.SortOrder
import com.richwatson.electrofind.viewmodel.SpeedFilter

data class ChargeSession(val startSoc: Int, val targetSoc: Int, val stayMinutes: Int, val profile: CarProfile = CarProfile.KONA_LR)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    chargerViewModel: ChargerViewModel,
    onShowOnMap: (ChargingLocation) -> Unit = {},
    onEditCustomCharger: ((Long) -> Unit)? = null
) {
    val state by chargerViewModel.state.collectAsState()
    val chargers = remember(state) { chargerViewModel.filteredSortedChargers }
    var showFilters by remember { mutableStateOf(false) }
    var tripPickerPk by remember { mutableStateOf<Long?>(null) }

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
                FilterBar(chargerViewModel, modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()))
            } else if (state.isLoading && chargers.isEmpty()) {
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
                    item {
                        val nAvailable = chargers.count { it.hasAvailableEvse }
                        val nOutOfOrder = chargers.count { it.hasOutOfOrderEvse && !it.hasAvailableEvse }
                        val nInUse = chargers.size - nAvailable - nOutOfOrder
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            if (nAvailable > 0) Text("● $nAvailable available", style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32))
                            if (nInUse > 0) Text("● $nInUse in use", style = MaterialTheme.typography.labelSmall, color = Color(0xFFF57F17))
                            if (nOutOfOrder > 0) Text("● $nOutOfOrder out of order", style = MaterialTheme.typography.labelSmall, color = Color(0xFFB71C1C))
                        }
                    }
                    items(chargers, key = { it.pk }) { charger ->
                        val distanceMiles = if (state.searchLat != 0.0 || state.searchLng != 0.0)
                            haversineMiles(state.searchLat, state.searchLng, charger.coordinates.latitude, charger.coordinates.longitude)
                        else null
                        ChargerCard(
                            charger = charger,
                            currencySymbol = state.currencySymbol,
                            session = ChargeSession(state.startSocPercent, state.targetSocPercent, state.stayMinutes, state.activeProfile),
                            distanceMiles = distanceMiles,
                            onShowOnMap = { onShowOnMap(charger) },
                            isFavourite = charger.pk in state.favouritePks,
                            isExcluded = charger.pk in state.excludedPks,
                            onToggleFavourite = { chargerViewModel.toggleFavourite(charger.pk) },
                            onToggleExcluded = { chargerViewModel.toggleExcluded(charger.pk) },
                            onAddToRoute = { pk -> tripPickerPk = pk },
                            onEditCustomCharger = if (charger.pk < 0) { { onEditCustomCharger?.invoke(charger.pk) } } else null
                        )
                    }
                }
            }
        }
    }

    tripPickerPk?.let { pk ->
        TripPickerDialog(
            pk = pk,
            trips = state.trips,
            onAddToTrip = { tripId -> chargerViewModel.addToRoute(pk, tripId); tripPickerPk = null },
            onAddAlternative = { stopId -> chargerViewModel.addAlternativeToStop(stopId, pk); tripPickerPk = null },
            onAddTrip = { name -> val id = chargerViewModel.addTrip(name); chargerViewModel.addToRoute(pk, id); tripPickerPk = null },
            onDismiss = { tripPickerPk = null }
        )
    }
}

@Composable
internal fun TripPickerDialog(
    pk: Long,
    trips: List<Trip>,
    onAddToTrip: (tripId: String) -> Unit,
    onAddAlternative: (stopId: String) -> Unit,
    onAddTrip: (name: String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTripId by remember(trips) {
        mutableStateOf(trips.firstOrNull()?.id)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to route") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Trip selector chips
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(trips) { trip ->
                        FilterChip(
                            selected = trip.id == selectedTripId,
                            onClick = { selectedTripId = trip.id },
                            label = { Text(trip.name, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                    item {
                        androidx.compose.material3.AssistChip(
                            onClick = {
                                val name = "Trip ${trips.size + 1}"
                                onAddTrip(name)
                            },
                            label = { Text("+ New trip", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                HorizontalDivider()

                // Options for selected trip
                val selectedTrip = trips.find { it.id == selectedTripId }
                if (selectedTrip != null) {
                    TextButton(
                        onClick = { onAddToTrip(selectedTrip.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Route, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("New stop in ${selectedTrip.name}", modifier = Modifier.weight(1f))
                    }
                    selectedTrip.stops.forEachIndexed { idx, stop ->
                        HorizontalDivider()
                        TextButton(
                            onClick = { onAddAlternative(stop.id) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Alt. for #${idx + 1} · ${stop.displayName(idx)}",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                } else {
                    TextButton(
                        onClick = { onAddTrip("Trip 1") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Route, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Create new trip", modifier = Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FilterBar(vm: ChargerViewModel, showSort: Boolean = true, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsState()
    val filtered = remember(state) { vm.filteredSortedChargers }

    val priceMax = remember(state.chargers) {
        (state.chargers.mapNotNull { it.pricePerKwh }.filter { it > 0 }.maxOrNull()?.toFloat() ?: 1f)
            .coerceAtLeast(0.5f)
    }
    val priceMin = 0f
    var sliderRange by remember(state.minPriceKwh, state.maxPriceKwh, priceMax) {
        mutableStateOf(
            (state.minPriceKwh?.toFloat() ?: priceMin)..(state.maxPriceKwh?.toFloat() ?: priceMax)
        )
    }

    val optimalCostMax = remember(state.chargers, state.startSocPercent, state.targetSocPercent) {
        state.chargers.mapNotNull { vm.optimalCostFor(it) }.filter { it > 0 }.maxOrNull()?.toFloat() ?: 0f
    }
    val stayCostMax = remember(state.chargers, state.startSocPercent, state.targetSocPercent, state.stayMinutes) {
        state.chargers.mapNotNull { vm.stayCostFor(it) }.filter { it > 0 }.maxOrNull()?.toFloat() ?: 0f
    }
    var optSliderRange by remember(state.minOptimalCost, state.maxOptimalCost, optimalCostMax) {
        mutableStateOf((state.minOptimalCost?.toFloat() ?: 0f)..(state.maxOptimalCost?.toFloat() ?: optimalCostMax))
    }
    var staySliderRange by remember(state.minStayCost, state.maxStayCost, stayCostMax) {
        mutableStateOf((state.minStayCost?.toFloat() ?: 0f)..(state.maxStayCost?.toFloat() ?: stayCostMax))
    }

    val nearestMi = remember(filtered, state.searchLat, state.searchLng) {
        if (state.searchLat == 0.0 && state.searchLng == 0.0) null
        else filtered.minOfOrNull {
            haversineMiles(state.searchLat, state.searchLng, it.coordinates.latitude, it.coordinates.longitude)
        }
    }

    Column(modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.showOnlyFavourites,
                onClick = { vm.setShowOnlyFavourites(!state.showOnlyFavourites) },
                leadingIcon = { Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(16.dp)) },
                label = { Text("Favourites only") }
            )
            FilterChip(
                selected = state.hideExcluded,
                onClick = { vm.setHideExcluded(!state.hideExcluded) },
                leadingIcon = { Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(16.dp)) },
                label = { Text("Hide excluded") }
            )
        }
        Spacer(Modifier.height(6.dp))
        if (showSort) {
            Text("Sort by", style = MaterialTheme.typography.labelMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
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
        Text("Max speed", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(null to "Any", 7.0 to "7 kW", 22.0 to "22 kW", 50.0 to "50 kW", 150.0 to "150 kW").forEach { (kw, label) ->
                FilterChip(
                    selected = state.maxSpeedKw == kw,
                    onClick = { vm.setMaxSpeedFilter(kw) },
                    label = { Text(label) }
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("Connector", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("CCS", "Type 2", "CHAdeMO").forEach { connector ->
                FilterChip(
                    selected = connector in state.connectorFilters,
                    onClick = { vm.toggleConnectorFilter(connector) },
                    label = { Text(connector) }
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Price per kWh (${state.currencySymbol})", style = MaterialTheme.typography.labelMedium)
            Text(
                buildString {
                    append("${filtered.size} chargers")
                    nearestMi?.let { append(" · nearest ${"%.1f".format(it)} mi") }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        RangeSlider(
            value = sliderRange,
            onValueChange = { sliderRange = it },
            onValueChangeFinished = {
                val min = if (sliderRange.start <= priceMin + 0.005f) null else sliderRange.start.toDouble()
                val max = if (sliderRange.endInclusive >= priceMax - 0.005f) null else sliderRange.endInclusive.toDouble()
                vm.setPriceFilter(min, max)
            },
            valueRange = priceMin..priceMax,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                if (sliderRange.start <= priceMin + 0.005f) "Any min"
                else "${state.currencySymbol}${"%.2f".format(sliderRange.start)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                if (sliderRange.endInclusive >= priceMax - 0.005f) "Any max"
                else "${state.currencySymbol}${"%.2f".format(sliderRange.endInclusive)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (optimalCostMax > 0f) {
            Spacer(Modifier.height(6.dp))
            Text("Optimal cost (${state.currencySymbol})", style = MaterialTheme.typography.labelMedium)
            RangeSlider(
                value = optSliderRange,
                onValueChange = { optSliderRange = it },
                onValueChangeFinished = {
                    val min = if (optSliderRange.start <= 0.005f) null else optSliderRange.start.toDouble()
                    val max = if (optSliderRange.endInclusive >= optimalCostMax - 0.005f) null else optSliderRange.endInclusive.toDouble()
                    vm.setOptimalCostFilter(min, max)
                },
                valueRange = 0f..optimalCostMax,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    if (optSliderRange.start <= 0.005f) "Any min" else "${state.currencySymbol}${"%.2f".format(optSliderRange.start)}",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (optSliderRange.endInclusive >= optimalCostMax - 0.005f) "Any max" else "${state.currencySymbol}${"%.2f".format(optSliderRange.endInclusive)}",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (stayCostMax > 0f) {
            Spacer(Modifier.height(6.dp))
            Text("Stay cost (${state.currencySymbol})", style = MaterialTheme.typography.labelMedium)
            RangeSlider(
                value = staySliderRange,
                onValueChange = { staySliderRange = it },
                onValueChangeFinished = {
                    val min = if (staySliderRange.start <= 0.005f) null else staySliderRange.start.toDouble()
                    val max = if (staySliderRange.endInclusive >= stayCostMax - 0.005f) null else staySliderRange.endInclusive.toDouble()
                    vm.setStayCostFilter(min, max)
                },
                valueRange = 0f..stayCostMax,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    if (staySliderRange.start <= 0.005f) "Any min" else "${state.currencySymbol}${"%.2f".format(staySliderRange.start)}",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (staySliderRange.endInclusive >= stayCostMax - 0.005f) "Any max" else "${state.currencySymbol}${"%.2f".format(staySliderRange.endInclusive)}",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ChargerCard(
    charger: ChargingLocation,
    currencySymbol: String = "€",
    session: ChargeSession? = null,
    distanceMiles: Double? = null,
    onShowOnMap: (() -> Unit)? = null,
    isFavourite: Boolean = false,
    isExcluded: Boolean = false,
    onToggleFavourite: () -> Unit = {},
    onToggleExcluded: () -> Unit = {},
    onAddToRoute: ((Long) -> Unit)? = null,
    onEditCustomCharger: (() -> Unit)? = null
) {
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
                    if (charger.pricePerKwh == null && charger.pricingText != null) {
                        Text(
                            charger.pricingText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row {
                    IconButton(onClick = onToggleFavourite, modifier = Modifier.size(36.dp)) {
                        Icon(
                            if (isFavourite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (isFavourite) "Remove from favourites" else "Add to favourites",
                            tint = if (isFavourite) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = onToggleExcluded, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.Block,
                            contentDescription = if (isExcluded) "Remove from excluded" else "Exclude charger",
                            tint = if (isExcluded) Color(0xFFE65100) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val (availText, availColor) = when {
                    charger.hasAvailableEvse  -> "Available"    to Color(0xFF2E7D32)
                    charger.hasOutOfOrderEvse -> "Out of order" to Color(0xFFB71C1C)
                    else                      -> "In use"       to Color(0xFFF57F17)
                }
                AssistChip(
                    onClick = {},
                    label = { Text(availText, style = MaterialTheme.typography.labelSmall) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = availColor.copy(alpha = 0.12f))
                )
                distanceMiles?.let { d ->
                    AssistChip(
                        onClick = {},
                        label = { Text("${"%.1f".format(d)} mi", style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            val availByKw = charger.availabilityByKw
            charger.connectorPriceSummaries.forEach { summary ->
                ConnectorPriceRow(summary, currencySymbol, availByKw)
            }

            charger.connectionFeeMajor?.let { fee ->
                Text(
                    "+ %s%.2f connection fee".format(currencySymbol, fee),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            charger.chargingTimeRateMajor?.let { rate ->
                Text(
                    "+ %s%.2f/min while charging".format(currencySymbol, rate),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            charger.parkingTimeRateMajor?.let { rate ->
                Text(
                    "+ %s%.2f/min idle fee".format(currencySymbol, rate),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (session != null) {
                val priceGroups = charger.connectorPriceSummaries
                    .groupBy { it.pricePerKwh to it.isFree }
                    .map { (_, group) -> group.first() }
                    .sortedByDescending { it.kilowatts ?: 0.0 }
                    .filter { it.kilowatts != null && (it.pricePerKwh != null || it.isFree) }
                if (priceGroups.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    val multiGroup = priceGroups.size > 1
                    priceGroups.forEach { s ->
                        val kw = s.kilowatts!!
                        val price = if (s.isFree) 0.0 else s.pricePerKwh!!
                        val optResult = KonaChargeCurve.simulate(session.startSoc.toFloat(), session.targetSoc.toFloat(), kw, null, profile = session.profile)
                        val optCost = KonaChargeCurve.totalCost(optResult, price, charger.connectionFeeMajor ?: 0.0, charger.chargingTimeRateMajor ?: 0.0, charger.parkingTimeRateMajor ?: 0.0, optResult.chargeMinutes)
                        val stayResult = KonaChargeCurve.simulate(session.startSoc.toFloat(), session.targetSoc.toFloat(), kw, session.stayMinutes.toDouble(), profile = session.profile)
                        val stayCost = KonaChargeCurve.totalCost(stayResult, price, charger.connectionFeeMajor ?: 0.0, charger.chargingTimeRateMajor ?: 0.0, charger.parkingTimeRateMajor ?: 0.0, session.stayMinutes.toDouble())
                        val optMins = optResult.chargeMinutes.toInt()
                        val optSoc = optResult.endSocPercent.toInt()
                        val optLabel = if (optMins >= 180) "≥3h → ${optSoc}%" else "$optMins min → ${optSoc}%"
                        val staySoc = stayResult.endSocPercent.toInt()
                        val fmt: (Double) -> String = { c -> if (s.isFree) "FREE" else "$currencySymbol${"%.2f".format(c)}" }
                        Row(verticalAlignment = Alignment.Top) {
                            if (multiGroup) {
                                Text(
                                    "${kw.toInt()} kW",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(52.dp)
                                )
                            }
                            Column {
                                Text(
                                    "⚡ Optimal: $optLabel  ·  ${fmt(optCost)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "🕐 In ${session.stayMinutes} min → ${staySoc}%  ·  ${fmt(stayCost)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
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
                onAddToRoute?.let { addFn ->
                    TextButton(
                        onClick = { addFn(charger.pk) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.Route, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Route", style = MaterialTheme.typography.labelSmall)
                    }
                }
                onShowOnMap?.let {
                    TextButton(
                        onClick = it,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Map", style = MaterialTheme.typography.labelSmall)
                    }
                }
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
                    Text("Maps", style = MaterialTheme.typography.labelSmall)
                }
                onEditCustomCharger?.let { editFn ->
                    TextButton(
                        onClick = editFn,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Edit", style = MaterialTheme.typography.labelSmall)
                    }
                }
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

@Composable
private fun ConnectorPriceRow(
    summary: com.richwatson.electrofind.api.models.ConnectorPriceSummary,
    currencySymbol: String,
    availByKw: Map<Int, Triple<Int, Int, Int>> = emptyMap()
) {
    val typeLabel = if (summary.count > 1) "${summary.type} ×${summary.count}" else summary.type
    val kwLabel = summary.kilowatts?.let { kw ->
        if (kw % 1.0 == 0.0) "${kw.toInt()} kW" else "%.1f kW".format(kw)
    } ?: ""
    val priceLabel = when {
        summary.isFree -> "Free"
        summary.pricePerKwh != null -> "%s%.2f/kWh".format(currencySymbol, summary.pricePerKwh)
        else -> "—"
    }
    val kwKey = summary.kilowatts?.toInt() ?: 0
    val (avail, inUse, fault) = availByKw[kwKey] ?: Triple(0, 0, 0)
    val availStr = buildString {
        if (avail > 0) append("$avail av")
        if (inUse > 0) { if (isNotEmpty()) append(" "); append("$inUse use") }
        if (fault > 0) { if (isNotEmpty()) append(" "); append("$fault fault${if (fault > 1) "s" else ""}") }
    }
    Row(Modifier.fillMaxWidth()) {
        Text(typeLabel, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
        Text(kwLabel, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(54.dp), textAlign = TextAlign.End)
        Text(priceLabel, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(84.dp), textAlign = TextAlign.End)
        if (availStr.isNotEmpty()) {
            Text(
                availStr,
                style = MaterialTheme.typography.labelSmall,
                color = if (avail > 0) Color(0xFF2E7D32) else Color(0xFFF57F17),
                modifier = Modifier.width(72.dp),
                textAlign = TextAlign.End
            )
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
    SortOrder.OPTIMAL_COST_ASC -> "Optimal cost"
    SortOrder.STAY_COST_ASC -> "Stay cost"
}

internal val SpeedFilter.label: String get() = when (this) {
    SpeedFilter.ALL -> "Any"
    SpeedFilter.FAST -> "7+ kW"
    SpeedFilter.RAPID -> "22+ kW"
    SpeedFilter.DC_FAST -> "50+ kW"
    SpeedFilter.ULTRA -> "100+ kW"
}

private fun haversineMiles(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val R = 3958.8
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    return R * 2 * atan2(sqrt(a), sqrt(1 - a))
}
