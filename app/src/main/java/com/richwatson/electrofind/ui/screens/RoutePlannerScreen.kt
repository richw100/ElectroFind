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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.richwatson.electrofind.api.models.ChargingLocation
import com.richwatson.electrofind.model.RouteStop
import com.richwatson.electrofind.util.KonaChargeCurve
import com.richwatson.electrofind.viewmodel.ChargerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutePlannerScreen(chargerViewModel: ChargerViewModel) {
    val state by chargerViewModel.state.collectAsState()
    val stops = state.routeStops
    var infoCharger by remember { mutableStateOf<ChargingLocation?>(null) }
    var infoSession by remember { mutableStateOf<ChargeSession?>(null) }
    var editStop by remember { mutableStateOf<RouteStop?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Route planner") },
                actions = {
                    if (stops.isNotEmpty()) {
                        IconButton(onClick = { chargerViewModel.clearRoute() }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear route")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (stops.isEmpty()) {
            Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
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
                        "No stops yet",
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
                modifier = Modifier.padding(padding).fillMaxSize(),
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
                        onRemove = { chargerViewModel.removeFromRoute(stop.id) },
                        onSetActive = { chargerViewModel.setActiveCharger(stop.id, it) },
                        onRemoveAlternative = { chargerViewModel.removeAlternative(stop.id, it) },
                        onNameChanged = { chargerViewModel.updateRouteStopName(stop.id, it) },
                        onEditSession = { editStop = stop },
                        onShowInfo = {
                            infoCharger = charger
                            infoSession = ChargeSession(
                                stop.arrivalSocPercent,
                                stop.departureSocPercent,
                                stop.stayMinutes,
                                state.activeProfile
                            )
                        },
                        onToggleFavourite = { chargerViewModel.toggleFavourite(stop.activePk) },
                        onToggleExcluded = { chargerViewModel.toggleExcluded(stop.activePk) },
                        isFavourite = stop.activePk in state.favouritePks,
                        isExcluded = stop.activePk in state.excludedPks
                    )
                }
            }
        }
    }

    // Edit SoC / stay time dialog
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

    // Charger info dialog
    infoCharger?.let { charger ->
        ChargerInfoDialog(
            charger = charger,
            session = infoSession,
            currencySymbol = state.currencySymbol,
            favouritePks = state.favouritePks,
            excludedPks = state.excludedPks,
            onToggleFavourite = { chargerViewModel.toggleFavourite(charger.pk) },
            onToggleExcluded = { chargerViewModel.toggleExcluded(charger.pk) },
            onDismiss = { infoCharger = null }
        )
    }
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
    onNameChanged: (String) -> Unit,
    onEditSession: () -> Unit,
    onShowInfo: () -> Unit,
    onToggleFavourite: () -> Unit,
    onToggleExcluded: () -> Unit,
    isFavourite: Boolean,
    isExcluded: Boolean
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
                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Remove stop", modifier = Modifier.size(18.dp))
                }
            }

            // Alternatives chips (shown only when more than one charger)
            if (stop.chargerPks.size > 1) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    stop.chargerPks.forEachIndexed { idx, pk ->
                        val label = allChargers[pk]?.name?.take(16) ?: "Stop $idx"
                        FilterChip(
                            selected = idx == stop.activeIndex,
                            onClick = { onSetActive(idx) },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                            trailingIcon = if (stop.chargerPks.size > 1) {
                                {
                                    IconButton(
                                        onClick = { onRemoveAlternative(pk) },
                                        modifier = Modifier.size(14.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Remove alternative", modifier = Modifier.size(10.dp))
                                    }
                                }
                            } else null
                        )
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

                // Availability
                val evseNodes = charger.evses.edges.map { it.node }
                val nAvail = evseNodes.count { it.status == "AVAILABLE" }
                val nFault = evseNodes.count { it.status in setOf("INOPERATIVE", "FAULTED", "UNAVAILABLE", "OUT_OF_ORDER") }
                val nInUse = evseNodes.size - nAvail - nFault
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (nAvail > 0) Text("● $nAvail available", style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32))
                    if (nInUse > 0) Text("● $nInUse in use", style = MaterialTheme.typography.labelSmall, color = Color(0xFFF57F17))
                    if (nFault > 0) Text("● $nFault fault${if (nFault > 1) "s" else ""}", style = MaterialTheme.typography.labelSmall, color = Color(0xFFB71C1C))
                }

                // Connector prices
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
                        Text(kwLabel, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(64.dp), textAlign = TextAlign.End)
                        Text(priceLabel, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(84.dp), textAlign = TextAlign.End)
                    }
                }

                // Session cost block using stop's own SoC / stay
                val session = ChargeSession(stop.arrivalSocPercent, stop.departureSocPercent, stop.stayMinutes)
                val priceGroups = charger.connectorPriceSummaries
                    .groupBy { it.pricePerKwh to it.isFree }
                    .map { (_, group) -> group.first() }
                    .sortedByDescending { it.kilowatts ?: 0.0 }
                    .filter { it.kilowatts != null && (it.pricePerKwh != null || it.isFree) }
                if (priceGroups.isNotEmpty()) {
                    val multiGroup = priceGroups.size > 1
                    priceGroups.forEach { s ->
                        val kw = s.kilowatts!!
                        val price = if (s.isFree) 0.0 else s.pricePerKwh!!
                        val optResult = KonaChargeCurve.simulate(session.startSoc.toFloat(), session.targetSoc.toFloat(), kw, null)
                        val optCost = KonaChargeCurve.totalCost(optResult, price, charger.connectionFeeMajor ?: 0.0, charger.chargingTimeRateMajor ?: 0.0, charger.parkingTimeRateMajor ?: 0.0, optResult.chargeMinutes)
                        val stayResult = KonaChargeCurve.simulate(session.startSoc.toFloat(), session.targetSoc.toFloat(), kw, session.stayMinutes.toDouble())
                        val stayCost = KonaChargeCurve.totalCost(stayResult, price, charger.connectionFeeMajor ?: 0.0, charger.chargingTimeRateMajor ?: 0.0, charger.parkingTimeRateMajor ?: 0.0, session.stayMinutes.toDouble())
                        val optMins = optResult.chargeMinutes.toInt()
                        val optSoc = optResult.endSocPercent.toInt()
                        val optLabel = if (optMins >= 180) "≥3h → ${optSoc}%" else "$optMins min → ${optSoc}%"
                        val staySoc = stayResult.endSocPercent.toInt()
                        val fmt: (Double) -> String = { c -> if (s.isFree) "FREE" else "$currencySymbol${"%.2f".format(c)}" }
                        Row(verticalAlignment = Alignment.Top) {
                            if (multiGroup) {
                                Text("${kw.toInt()} kW", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(52.dp))
                            }
                            Column {
                                Text("⚡ Optimal: $optLabel  ·  ${fmt(optCost)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Text("🕐 In ${session.stayMinutes} min → ${staySoc}%  ·  ${fmt(stayCost)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
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
                        onClick = onShowInfo,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("Info", style = MaterialTheme.typography.labelSmall)
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
                    // Favourite / exclude icons
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
                    "To add an alternative charger for this stop, find it in Search or Map and tap \"Add to route\" → \"Add as alternative to #${position + 1}\".",
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

@Composable
private fun ChargerInfoDialog(
    charger: ChargingLocation,
    session: ChargeSession?,
    currencySymbol: String,
    favouritePks: Set<Long>,
    excludedPks: Set<Long>,
    onToggleFavourite: () -> Unit,
    onToggleExcluded: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(charger.name) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("${charger.address}, ${charger.city}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(charger.operator.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onToggleFavourite, modifier = Modifier.size(32.dp)) {
                        Icon(
                            if (charger.pk in favouritePks) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = null,
                            tint = if (charger.pk in favouritePks) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onToggleExcluded, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Block,
                            contentDescription = null,
                            tint = if (charger.pk in excludedPks) Color(0xFFE65100) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    if (charger.pk in favouritePks) Text("Favourite", style = MaterialTheme.typography.labelSmall, color = Color(0xFFE53935))
                    if (charger.pk in excludedPks) Text("Excluded", style = MaterialTheme.typography.labelSmall, color = Color(0xFFE65100))
                }

                charger.connectorPriceSummaries.forEach { summary ->
                    val typeLabel = if (summary.count > 1) "${summary.type} ×${summary.count}" else summary.type
                    val kwLabel = summary.kilowatts?.let { kw -> if (kw % 1.0 == 0.0) "${kw.toInt()} kW" else "%.1f kW".format(kw) } ?: ""
                    val priceLabel = when {
                        summary.isFree -> "Free"
                        summary.pricePerKwh != null -> "%s%.2f/kWh".format(currencySymbol, summary.pricePerKwh)
                        else -> "—"
                    }
                    Row(Modifier.fillMaxWidth()) {
                        Text(typeLabel, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text(kwLabel, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(64.dp), textAlign = TextAlign.End)
                        Text(priceLabel, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(88.dp), textAlign = TextAlign.End)
                    }
                }
                charger.connectionFeeMajor?.let { Text("+ %s%.2f connection fee".format(currencySymbol, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                charger.chargingTimeRateMajor?.let { Text("+ %s%.2f/min while charging".format(currencySymbol, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                charger.parkingTimeRateMajor?.let { Text("+ %s%.2f/min idle fee".format(currencySymbol, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }

                if (session != null) {
                    val priceGroups = charger.connectorPriceSummaries
                        .groupBy { it.pricePerKwh to it.isFree }
                        .map { (_, group) -> group.first() }
                        .sortedByDescending { it.kilowatts ?: 0.0 }
                        .filter { it.kilowatts != null && (it.pricePerKwh != null || it.isFree) }
                    val multiGroup = priceGroups.size > 1
                    priceGroups.forEach { s ->
                        val kw = s.kilowatts!!
                        val price = if (s.isFree) 0.0 else s.pricePerKwh!!
                        val optResult = KonaChargeCurve.simulate(session.startSoc.toFloat(), session.targetSoc.toFloat(), kw, null)
                        val optCost = KonaChargeCurve.totalCost(optResult, price, charger.connectionFeeMajor ?: 0.0, charger.chargingTimeRateMajor ?: 0.0, charger.parkingTimeRateMajor ?: 0.0, optResult.chargeMinutes)
                        val stayResult = KonaChargeCurve.simulate(session.startSoc.toFloat(), session.targetSoc.toFloat(), kw, session.stayMinutes.toDouble())
                        val stayCost = KonaChargeCurve.totalCost(stayResult, price, charger.connectionFeeMajor ?: 0.0, charger.chargingTimeRateMajor ?: 0.0, charger.parkingTimeRateMajor ?: 0.0, session.stayMinutes.toDouble())
                        val optMins = optResult.chargeMinutes.toInt()
                        val optSoc = optResult.endSocPercent.toInt()
                        val optLabel = if (optMins >= 180) "≥3h → ${optSoc}%" else "$optMins min → ${optSoc}%"
                        val staySoc = stayResult.endSocPercent.toInt()
                        val fmt: (Double) -> String = { c -> if (s.isFree) "FREE" else "$currencySymbol${"%.2f".format(c)}" }
                        Row(verticalAlignment = Alignment.Top) {
                            if (multiGroup) Text("${kw.toInt()} kW", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(52.dp))
                            Column {
                                Text("⚡ Optimal: $optLabel  ·  ${fmt(optCost)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                Text("🕐 In ${session.stayMinutes} min → ${staySoc}%  ·  ${fmt(stayCost)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                val evseNodes = charger.evses.edges.map { it.node }
                val nAvail = evseNodes.count { it.status == "AVAILABLE" }
                val nFault = evseNodes.count { it.status in setOf("INOPERATIVE", "FAULTED", "UNAVAILABLE", "OUT_OF_ORDER") }
                val nInUse = evseNodes.size - nAvail - nFault
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (nAvail > 0) Text("$nAvail available", style = MaterialTheme.typography.bodySmall, color = Color(0xFF2E7D32))
                    if (nInUse > 0) Text("$nInUse in use", style = MaterialTheme.typography.bodySmall, color = Color(0xFFF57F17))
                    if (nFault > 0) Text("$nFault fault${if (nFault > 1) "s" else ""}", style = MaterialTheme.typography.bodySmall, color = Color(0xFFB71C1C))
                }
                if (charger.isStale) Text("! Cached data may be out of date", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Row {
                val lat = charger.coordinates.latitude
                val lng = charger.coordinates.longitude
                TextButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=$lat,$lng(${charger.name})")))
                    onDismiss()
                }) {
                    Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Maps")
                }
                charger.externalId?.let { extId ->
                    TextButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://electroverse.octopus.energy/map?extId=$extId")))
                        onDismiss()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Electroverse")
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
