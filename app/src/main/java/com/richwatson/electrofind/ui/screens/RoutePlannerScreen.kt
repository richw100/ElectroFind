package com.richwatson.electrofind.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import com.richwatson.electrofind.api.models.ChargingLocation
import com.richwatson.electrofind.api.models.timeAgo
import com.richwatson.electrofind.model.RouteStop
import com.richwatson.electrofind.model.Trip
import com.richwatson.electrofind.util.KonaChargeCurve
import com.richwatson.electrofind.viewmodel.ChargerViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class ConfirmPendingAction(val title: String, val body: String, val onConfirm: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutePlannerScreen(chargerViewModel: ChargerViewModel, onShowOnMap: (Long) -> Unit = {}) {
    val state by chargerViewModel.state.collectAsState()
    val trips = state.trips
    val activeTrip = state.activeTrip
    val stops = state.routeStops
    var editStop by remember { mutableStateOf<RouteStop?>(null) }
    var renameTripId by remember { mutableStateOf<String?>(null) }
    var copyStopId by remember { mutableStateOf<String?>(null) }
    var addAltFromStopId by remember { mutableStateOf<String?>(null) }
    var pendingConfirm by remember { mutableStateOf<ConfirmPendingAction?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Route planner") },
                actions = {
                    if (stops.isNotEmpty()) {
                        IconButton(onClick = {
                            pendingConfirm = ConfirmPendingAction(
                                "Clear route?",
                                "Remove all stops from all trips?"
                            ) { chargerViewModel.clearRoute() }
                        }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear route")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (trips.isNotEmpty()) {
                TripTabRow(
                    trips = trips,
                    activeTripId = activeTrip?.id,
                    onSelect = { chargerViewModel.setActiveTripId(it) },
                    onAddTrip = { chargerViewModel.addTrip("Trip ${trips.size + 1}") },
                    onTapActive = { renameTripId = activeTrip?.id }
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
                    Text(
                        text = state.routeChargersRefreshedAt
                            ?.let { "Updated ${timeFmt.format(Date(it))}" }
                            ?: "Not yet refreshed",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { chargerViewModel.refreshRouteChargers() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh availability",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                HorizontalDivider()
            }

            if (stops.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Route,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Text(
                            if (trips.isEmpty()) "No stops yet"
                            else "No stops in ${activeTrip?.name ?: "this trip"}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Find chargers in the Search or Map tabs and tap \"Add to route\".",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(stops, key = { _, s -> s.id }) { idx, stop ->
                        val charger = state.routeChargers[stop.activePk]
                        RouteStopCard(
                            stop = stop,
                            position = idx,
                            totalStops = stops.size,
                            charger = charger,
                            currencySymbol = state.currencySymbol,
                            allChargers = state.routeChargers,
                            onMoveUp = { chargerViewModel.moveRouteStop(stop.id, -1) },
                            onMoveDown = { chargerViewModel.moveRouteStop(stop.id, +1) },
                            onRemove = {
                                val name = charger?.name ?: stop.displayName(idx)
                                pendingConfirm = ConfirmPendingAction(
                                    "Remove stop?",
                                    "Remove \"$name\" from the route?"
                                ) { chargerViewModel.removeFromRoute(stop.id) }
                            },
                            onSetActive = { chargerViewModel.setActiveCharger(stop.id, it) },
                            onRemoveAlternative = { pk ->
                                val name = state.routeChargers[pk]?.name ?: "this charger"
                                pendingConfirm = ConfirmPendingAction(
                                    "Remove alternative?",
                                    "Remove \"$name\" from this stop?"
                                ) { chargerViewModel.removeAlternative(stop.id, pk) }
                            },
                            onMoveAlternative = { from, to -> chargerViewModel.moveAlternative(stop.id, from, to) },
                            onNameChanged = { chargerViewModel.updateRouteStopName(stop.id, it) },
                            onEditSession = { editStop = stop },
                            onShowOnMap = { charger?.let { onShowOnMap(it.pk) } },
                            onToggleFavourite = { chargerViewModel.toggleFavourite(stop.activePk) },
                            onToggleExcluded = { chargerViewModel.toggleExcluded(stop.activePk) },
                            isFavourite = stop.activePk in state.favouritePks,
                            isExcluded = stop.activePk in state.excludedPks,
                            onCopyStop = if (trips.size > 1) { { copyStopId = stop.id } } else null,
                            onAddAsAlternative = if (stops.size > 1) { { addAltFromStopId = stop.id } } else null
                        )
                    }
                }
            }
        }
    }

    editStop?.let { stop ->
        RouteStopEditDialog(
            stop = stop,
            onConfirm = { arrival, departure, stay ->
                chargerViewModel.updateRouteStop(stop.id, arrival, departure, stay)
                editStop = null
            },
            onDismiss = { editStop = null }
        )
    }


    copyStopId?.let { stopId ->
        CopyStopDialog(
            currentTripId = activeTrip?.id,
            trips = trips,
            onCopy = { targetTripId ->
                chargerViewModel.copyStopToTrip(stopId, targetTripId)
                copyStopId = null
            },
            onDismiss = { copyStopId = null }
        )
    }

    addAltFromStopId?.let { stopId ->
        val sourcePk = stops.find { it.id == stopId }?.activePk
        val sourceName = sourcePk?.let { state.routeChargers[it]?.name } ?: "charger"
        if (sourcePk != null) {
            AddAsAlternativeDialog(
                sourceChargerName = sourceName,
                stops = stops,
                sourceStopId = stopId,
                allChargers = state.routeChargers,
                onAdd = { targetStopId ->
                    chargerViewModel.addAlternativeToStop(targetStopId, sourcePk)
                    addAltFromStopId = null
                },
                onDismiss = { addAltFromStopId = null }
            )
        }
    }

    renameTripId?.let { tripId ->
        val trip = trips.find { it.id == tripId }
        val tripIndex = trips.indexOfFirst { it.id == tripId }
        if (trip != null) {
            RenameTripDialog(
                trip = trip,
                tripIndex = tripIndex,
                tripCount = trips.size,
                onRename = { newName -> chargerViewModel.renameTrip(tripId, newName); renameTripId = null },
                onDelete = {
                    pendingConfirm = ConfirmPendingAction(
                        "Delete trip?",
                        "Delete \"${trip.name}\"? All stops will be removed."
                    ) { chargerViewModel.deleteTrip(tripId); renameTripId = null }
                },
                onMoveLeft = { chargerViewModel.reorderTrip(tripId, -1) },
                onMoveRight = { chargerViewModel.reorderTrip(tripId, +1) },
                onDismiss = { renameTripId = null }
            )
        }
    }

    pendingConfirm?.let { action ->
        ConfirmDeleteDialog(
            title = action.title,
            body = action.body,
            onConfirm = { action.onConfirm(); pendingConfirm = null },
            onDismiss = { pendingConfirm = null }
        )
    }
}

@Composable
private fun TripTabRow(
    trips: List<Trip>,
    activeTripId: String?,
    onSelect: (String) -> Unit,
    onAddTrip: () -> Unit,
    onTapActive: () -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(trips, key = { it.id }) { trip ->
            val isActive = trip.id == activeTripId
            FilterChip(
                selected = isActive,
                onClick = { if (isActive) onTapActive() else onSelect(trip.id) },
                label = {
                    val stopCount = trip.stops.size
                    Text(
                        if (stopCount > 0) "${trip.name} ($stopCount)" else trip.name,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            )
        }
        item {
            AssistChip(
                onClick = onAddTrip,
                label = { Text("+ Trip", style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
}

@Composable
private fun RenameTripDialog(
    trip: Trip,
    tripIndex: Int,
    tripCount: Int,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(trip.id) { mutableStateOf(trip.name) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete trip?") },
            text = {
                val n = trip.stops.size
                Text("\"${trip.name}\" and its $n stop${if (n == 1) "" else "s"} will be removed.")
            },
            confirmButton = {
                TextButton(onClick = onDelete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Trip options") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = onMoveLeft, enabled = tripIndex > 0) {
                            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Move left")
                        }
                        IconButton(onClick = onMoveRight, enabled = tripIndex < tripCount - 1) {
                            Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Move right")
                        }
                        Text(
                            "${tripIndex + 1} of $tripCount",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onRename(name.ifBlank { trip.name }) }) { Text("Rename") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showDeleteConfirm = true }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                }
            }
        )
    }
}

@Composable
private fun CopyStopDialog(
    currentTripId: String?,
    trips: List<Trip>,
    onCopy: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val otherTrips = trips.filter { it.id != currentTripId }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Copy stop to trip") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                otherTrips.forEach { trip ->
                    TextButton(onClick = { onCopy(trip.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text(trip.name, modifier = Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddAsAlternativeDialog(
    sourceChargerName: String,
    stops: List<RouteStop>,
    sourceStopId: String,
    allChargers: Map<Long, ChargingLocation>,
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val otherStops = stops.filter { it.id != sourceStopId }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add as alternative to…") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(sourceChargerName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                otherStops.forEach { stop ->
                    val idx = stops.indexOf(stop)
                    val chargerName = allChargers[stop.activePk]?.name ?: ""
                    TextButton(onClick = { onAdd(stop.id) }, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stop.displayName(idx))
                            if (chargerName.isNotEmpty()) {
                                Text(chargerName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ConfirmDeleteDialog(
    title: String,
    body: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun RouteStopCard(
    stop: RouteStop,
    position: Int,
    totalStops: Int,
    charger: ChargingLocation?,
    currencySymbol: String,
    allChargers: Map<Long, ChargingLocation>,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onSetActive: (Int) -> Unit,
    onRemoveAlternative: (Long) -> Unit,
    onMoveAlternative: (fromIndex: Int, toIndex: Int) -> Unit,
    onNameChanged: (String) -> Unit,
    onEditSession: () -> Unit,
    onShowOnMap: () -> Unit,
    onToggleFavourite: () -> Unit,
    onToggleExcluded: () -> Unit,
    isFavourite: Boolean,
    isExcluded: Boolean,
    onCopyStop: (() -> Unit)? = null,
    onAddAsAlternative: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var nameText by remember(stop.id) { mutableStateOf(stop.customName ?: "") }
    val contentColor = MaterialTheme.colorScheme.onSurface
    val placeholderColor = MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {

            // Header: position + editable name + reorder + remove
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "#${position + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 6.dp)
                )
                Box(modifier = Modifier.weight(1f)) {
                    if (nameText.isEmpty()) {
                        Text(
                            stop.displayName(position),
                            style = MaterialTheme.typography.titleSmall,
                            color = placeholderColor
                        )
                    }
                    BasicTextField(
                        value = nameText,
                        onValueChange = { nameText = it; onNameChanged(it) },
                        textStyle = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = contentColor
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                IconButton(onClick = onMoveUp, enabled = position > 0, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onMoveDown, enabled = position < totalStops - 1, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down", modifier = Modifier.size(18.dp))
                }
                if (onAddAsAlternative != null) {
                    IconButton(onClick = onAddAsAlternative, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Add, contentDescription = "Add as alternative to stop", modifier = Modifier.size(18.dp))
                    }
                }
                if (onCopyStop != null) {
                    IconButton(onClick = onCopyStop, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy to trip", modifier = Modifier.size(18.dp))
                    }
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Remove stop", modifier = Modifier.size(18.dp))
                }
            }

            // Alternatives chips — long-press to drag and reorder
            if (stop.chargerPks.size > 1) {
                val listState = rememberLazyListState()
                val reorderState = rememberReorderableLazyListState(listState) { from, to ->
                    onMoveAlternative(from.index, to.index)
                }
                LazyRow(
                    state = listState,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(stop.chargerPks, key = { _, pk -> pk }) { idx, pk ->
                        ReorderableItem(reorderState, key = pk) { isDragging ->
                            val elevation by animateDpAsState(
                                targetValue = if (isDragging) 6.dp else 0.dp,
                                label = "chip-drag-elevation"
                            )
                            FilterChip(
                                selected = idx == stop.activeIndex,
                                onClick = { onSetActive(idx) },
                                label = {
                                    Text(
                                        allChargers[pk]?.name?.take(16) ?: "Stop $idx",
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1
                                    )
                                },
                                trailingIcon = {
                                    IconButton(
                                        onClick = { onRemoveAlternative(pk) },
                                        modifier = Modifier.size(14.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove alternative",
                                            modifier = Modifier.size(10.dp)
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .shadow(elevation, shape = FilterChipDefaults.shape)
                                    .longPressDraggableHandle()
                            )
                        }
                    }
                }
            }

            if (charger == null) {
                Text(
                    "Loading charger data…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // Charger name + operator
                Text(charger.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    "${charger.operator.name} · ${charger.address}, ${charger.city}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Connector prices
                val rpAvailByKw = charger.availabilityByKw
                charger.connectorPriceSummaries.forEach { summary ->
                    val typeLabel = if (summary.count > 1) "${summary.type} ×${summary.count}" else summary.type
                    val kwLabel = summary.kilowatts?.let { kw ->
                        if (kw % 1.0 == 0.0) "${kw.toInt()} kW" else "%.1f kW".format(kw)
                    } ?: ""
                    val priceLabel = when {
                        summary.isFree -> "Free"
                        summary.pricePerKwh != null -> "%s%.2f/kWh".format(currencySymbol, summary.pricePerKwh)
                        else -> "—"
                    }
                    Row(Modifier.fillMaxWidth()) {
                        Text(typeLabel, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                        Text(kwLabel, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(54.dp), textAlign = TextAlign.End)
                        Text(priceLabel, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(84.dp), textAlign = TextAlign.End)
                    }
                }

                // Time-based charges (charger-level, shown once below connector rows)
                val ratesParts = listOfNotNull(
                    charger.connectionFeeMajor?.let { "${currencySymbol}${"%.2f".format(it)} connection fee" },
                    charger.chargingTimeRateMajor?.let { "${currencySymbol}${"%.2f".format(it)}/min charging" },
                    charger.parkingTimeRateMajor?.let { "${currencySymbol}${"%.2f".format(it)}/min parking" }
                )
                if (ratesParts.isNotEmpty()) {
                    Text(
                        ratesParts.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Unified per-speed: availability + session cost
                val session = ChargeSession(stop.arrivalSocPercent, stop.departureSocPercent, stop.stayMinutes)
                val priceGroups = charger.connectorPriceSummaries
                    .filter { it.kilowatts != null && (it.pricePerKwh != null || it.isFree) }
                    .groupBy { it.kilowatts }
                    .map { (_, group) -> group.first() }
                val kwTiers = (rpAvailByKw.keys.map { it.toDouble() } + priceGroups.map { it.kilowatts!! })
                    .distinct().sortedByDescending { it }
                val multiSpeed = kwTiers.size > 1
                kwTiers.forEach { kw ->
                    val avail = rpAvailByKw[kw.toInt()]
                    val pg = priceGroups.find { it.kilowatts == kw }
                    Column(modifier = Modifier.padding(vertical = 1.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (multiSpeed) {
                                Text("${kw.toInt()}kW", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                            }
                            avail?.let { (avl, inUse, fault) ->
                                if (avl > 0) Text("$avl avail", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFF66BB6A))
                                if (inUse > 0) Text("$inUse in use", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFFF57F17))
                                if (fault > 0) Text("$fault broken", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFFB71C1C))
                            }
                        }
                        if (pg != null) {
                            val price = if (pg.isFree) 0.0 else pg.pricePerKwh!!
                            val optResult = KonaChargeCurve.simulate(session.startSoc.toFloat(), session.targetSoc.toFloat(), kw, null)
                            val optCost = KonaChargeCurve.totalCost(optResult, price, charger.connectionFeeMajor ?: 0.0, charger.chargingTimeRateMajor ?: 0.0, charger.parkingTimeRateMajor ?: 0.0, optResult.chargeMinutes)
                            val stayResult = KonaChargeCurve.simulate(session.startSoc.toFloat(), session.targetSoc.toFloat(), kw, session.stayMinutes.toDouble())
                            val stayCost = KonaChargeCurve.totalCost(stayResult, price, charger.connectionFeeMajor ?: 0.0, charger.chargingTimeRateMajor ?: 0.0, charger.parkingTimeRateMajor ?: 0.0, session.stayMinutes.toDouble())
                            val optMins = optResult.chargeMinutes.toInt()
                            val optSoc = optResult.endSocPercent.toInt()
                            val optLabel = if (optMins >= 180) "≥3h → ${optSoc}%" else "$optMins min → ${optSoc}%"
                            val staySoc = stayResult.endSocPercent.toInt()
                            val fmt: (Double) -> String = { c -> if (pg.isFree) "FREE" else "$currencySymbol${"%.2f".format(c)}" }
                            Text("⚡ Optimal: $optLabel  ·  ${fmt(optCost)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text("🕐 In ${session.stayMinutes} min → ${staySoc}%  ·  ${fmt(stayCost)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                timeAgo(charger.cachedAt)?.let {
                    Text(
                        "Updated $it",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (charger.isStale) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

            // SoC / time summary + edit
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Arrive ${stop.arrivalSocPercent}% → Depart ${stop.departureSocPercent}%  ·  ${stop.stayMinutes} min",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onEditSession, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit session", modifier = Modifier.size(16.dp))
                }
            }

            // Action buttons
            Row(modifier = Modifier.fillMaxWidth()) {
                if (charger != null) {
                    TextButton(
                        onClick = onShowOnMap,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Show", style = MaterialTheme.typography.labelSmall)
                    }
                    val lat = charger.coordinates.latitude
                    val lng = charger.coordinates.longitude
                    TextButton(
                        onClick = {
                            val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(${charger.name})")
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Maps", style = MaterialTheme.typography.labelSmall)
                    }
                    charger.externalId?.let { extId ->
                        TextButton(
                            onClick = {
                                val uri = Uri.parse("https://electroverse.octopus.energy/map?extId=$extId")
                                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Electroverse", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onToggleFavourite, modifier = Modifier.size(28.dp)) {
                        Icon(
                            if (isFavourite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = null,
                            tint = if (isFavourite) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(onClick = onToggleExcluded, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.Block,
                            contentDescription = null,
                            tint = if (isExcluded) Color(0xFFE65100) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            if (charger != null) {
                Text(
                    "To add an alternative charger for this stop, find it in Search or Map and tap \"Add to route\" → \"Alt. for #${position + 1}\".",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun RouteStopEditDialog(
    stop: RouteStop,
    onConfirm: (Int, Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var arrival by remember { mutableFloatStateOf(stop.arrivalSocPercent.toFloat()) }
    var departure by remember { mutableFloatStateOf(stop.departureSocPercent.toFloat()) }
    var stay by remember { mutableFloatStateOf((stop.stayMinutes / 5f).coerceIn(0f, 36f)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit session") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Arrival SoC: ${arrival.toInt()}%", style = MaterialTheme.typography.bodySmall)
                Slider(value = arrival, onValueChange = { arrival = it }, valueRange = 0f..100f, steps = 99)

                Text("Departure SoC: ${departure.toInt()}%", style = MaterialTheme.typography.bodySmall)
                Slider(value = departure, onValueChange = { departure = it }, valueRange = 0f..100f, steps = 99)

                Text("Stay time: ${(stay.toInt() * 5)} min", style = MaterialTheme.typography.bodySmall)
                Slider(value = stay, onValueChange = { stay = it }, valueRange = 0f..36f, steps = 35)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(arrival.toInt(), departure.toInt(), stay.toInt() * 5) }) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

