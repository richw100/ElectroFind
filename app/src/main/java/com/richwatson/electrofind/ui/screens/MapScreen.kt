package com.richwatson.electrofind.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.richwatson.electrofind.api.models.ChargingLocation
import com.richwatson.electrofind.util.KonaChargeCurve
import com.richwatson.electrofind.viewmodel.ChargerViewModel
import kotlinx.coroutines.delay
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseMapScreen(
    initialLat: Double,
    initialLng: Double,
    chargerViewModel: ChargerViewModel,
    onLocationSelected: (Double, Double, String?) -> Unit,
    onBack: () -> Unit
) {
    val state by chargerViewModel.state.collectAsState()
    val suggestions by chargerViewModel.suggestions.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    var searchText by remember { mutableStateOf("") }
    var suggestionsExpanded by remember { mutableStateOf(false) }
    var pendingCenter by remember { mutableStateOf<GeoPoint?>(null) }
    var fieldFocused by remember { mutableStateOf(false) }

    LaunchedEffect(searchText) {
        if (searchText.length >= 2) {
            delay(400)
            chargerViewModel.fetchSuggestions(searchText)
            suggestionsExpanded = true
        } else {
            chargerViewModel.clearSuggestions()
            suggestionsExpanded = false
        }
    }

    LaunchedEffect(suggestions) {
        if (suggestions.isEmpty()) suggestionsExpanded = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Browse map") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            val savedLat = state.savedMapCenterLat
            val savedLng = state.savedMapCenterLng
            val hasSaved = savedLat != 0.0 || savedLng != 0.0
            ChargerMapView(
                chargers = emptyList(),
                searchLat = initialLat,
                searchLng = initialLng,
                initialZoom = if (hasSaved) state.savedMapZoom else 8.0,
                initialCenter = if (hasSaved) GeoPoint(savedLat, savedLng) else null,
                centerOn = pendingCenter,
                radiusMiles = state.searchRadiusMiles,
                modifier = Modifier.fillMaxSize(),
                onMapPositionSaved = { zoom, lat, lng -> chargerViewModel.saveMapPosition(zoom, lat, lng) },
                onLocationSelected = onLocationSelected
            )

            // Search bar overlay
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
                    .align(Alignment.TopCenter),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column {
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        placeholder = { Text("Search location…") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                            .onFocusChanged { fieldFocused = it.isFocused },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            val top = suggestions.firstOrNull()
                            if (top != null) {
                                suggestionsExpanded = false
                                chargerViewModel.clearSuggestions()
                                keyboardController?.hide()
                                onLocationSelected(top.lat, top.lng, top.primaryName)
                            } else {
                                chargerViewModel.fetchSuggestions(searchText)
                                suggestionsExpanded = true
                            }
                        }),
                        trailingIcon = {
                            IconButton(onClick = {
                                val top = suggestions.firstOrNull()
                                if (top != null) {
                                    suggestionsExpanded = false
                                    chargerViewModel.clearSuggestions()
                                    keyboardController?.hide()
                                    onLocationSelected(top.lat, top.lng, top.primaryName)
                                } else {
                                    chargerViewModel.fetchSuggestions(searchText)
                                    suggestionsExpanded = true
                                }
                            }) {
                                Icon(Icons.Default.Search, contentDescription = "Search")
                            }
                        }
                    )
                    if (suggestionsExpanded && suggestions.isNotEmpty()) {
                        HorizontalDivider()
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                            items(suggestions) { suggestion ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            suggestionsExpanded = false
                                            chargerViewModel.clearSuggestions()
                                            keyboardController?.hide()
                                            onLocationSelected(suggestion.lat, suggestion.lng, suggestion.primaryName)
                                        }
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                    Text(suggestion.primaryName, style = MaterialTheme.typography.bodyMedium)
                                    if (suggestion.secondaryName.isNotEmpty()) {
                                        Text(
                                            suggestion.secondaryName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                HorizontalDivider()
                            }
                        }
                    } else if (fieldFocused && searchText.isEmpty() && state.searchHistory.isNotEmpty()) {
                        HorizontalDivider()
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                            items(state.searchHistory) { entry ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            keyboardController?.hide()
                                            onLocationSelected(entry.lat, entry.lng, entry.label)
                                        }
                                        .padding(start = 16.dp, end = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        entry.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f).padding(vertical = 10.dp)
                                    )
                                    IconButton(onClick = { chargerViewModel.removeFromHistory(entry.label) }) {
                                        Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(18.dp))
                                    }
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsMapScreen(
    chargerViewModel: ChargerViewModel
) {
    val state by chargerViewModel.state.collectAsState()
    val chargers = remember(state) { chargerViewModel.filteredSortedChargers }
    var showFilters by remember { mutableStateOf(false) }
    var priceMode by remember { mutableStateOf(MapPriceMode.PER_KWH) }

    val initialCenter = when {
        state.savedMapCenterLat != 0.0 || state.savedMapCenterLng != 0.0 ->
            GeoPoint(state.savedMapCenterLat, state.savedMapCenterLng)
        state.searchLat != 0.0 || state.searchLng != 0.0 ->
            GeoPoint(state.searchLat, state.searchLng)
        else -> GeoPoint(52.5, -1.5)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Map · ${chargers.size} chargers", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(
                                MapPriceMode.PER_KWH to "/kWh",
                                MapPriceMode.OPTIMAL_COST to "Optimal",
                                MapPriceMode.STAY_COST to "Stay"
                            ).forEach { (mode, label) ->
                                FilterChip(
                                    selected = priceMode == mode,
                                    onClick = { priceMode = mode },
                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
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
        Box(Modifier.padding(padding).fillMaxSize()) {
            ChargerMapView(
                chargers = chargers,
                searchLat = state.searchLat,
                searchLng = state.searchLng,
                initialZoom = state.savedMapZoom,
                initialCenter = initialCenter,
                radiusMiles = state.searchRadiusMiles,
                currencySymbol = state.currencySymbol,
                session = ChargeSession(state.startSocPercent, state.targetSocPercent, state.stayMinutes),
                priceMode = priceMode,
                selectedChargerPk = state.selectedChargerPk,
                modifier = Modifier.fillMaxSize(),
                onMapPositionSaved = { zoom, lat, lng ->
                    chargerViewModel.saveMapPosition(zoom, lat, lng)
                },
                onLocationSelected = { lat, lng, label ->
                    chargerViewModel.searchByCoordinates(lat, lng, label = label, reverseGeocodePrefix = if (label == null) "Map pin" else null)
                }
            )
            if (showFilters) {
                FilterBar(
                    chargerViewModel,
                    showSort = false,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .verticalScroll(rememberScrollState())
                )
            }
        }
    }
}

enum class MapPriceMode { PER_KWH, OPTIMAL_COST, STAY_COST }

@Composable
fun ChargerMapView(
    chargers: List<ChargingLocation>,
    searchLat: Double,
    searchLng: Double,
    initialZoom: Double = 14.0,
    initialCenter: GeoPoint? = null,
    centerOn: GeoPoint? = null,
    radiusMiles: Int = 0,
    currencySymbol: String = "€",
    session: ChargeSession? = null,
    priceMode: MapPriceMode = MapPriceMode.PER_KWH,
    selectedChargerPk: Long? = null,
    modifier: Modifier = Modifier,
    onMapPositionSaved: (zoom: Double, lat: Double, lng: Double) -> Unit = { _, _, _ -> },
    onLocationSelected: ((Double, Double, String?) -> Unit)? = null
) {
    val context = LocalContext()
    var dialogCharger by remember { mutableStateOf<ChargingLocation?>(null) }
    var myLocationPoint by remember { mutableStateOf<GeoPoint?>(null) }

    val mapView = remember {
        org.osmdroid.views.MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
            controller.setZoom(initialZoom)
            controller.setCenter(initialCenter ?: GeoPoint(searchLat, searchLng))
        }
    }

    LaunchedEffect(centerOn) {
        if (centerOn != null) {
            mapView.controller.animateTo(centerOn)
            mapView.controller.setZoom(14.0)
        }
    }

    LaunchedEffect(selectedChargerPk, chargers) {
        if (selectedChargerPk != null) {
            chargers.find { it.pk == selectedChargerPk }?.let { charger ->
                mapView.controller.animateTo(GeoPoint(charger.coordinates.latitude, charger.coordinates.longitude))
                if (mapView.zoomLevelDouble < 14.0) mapView.controller.setZoom(14.0)
            }
        }
    }

    LaunchedEffect(chargers, radiusMiles, myLocationPoint, currencySymbol, priceMode, session, selectedChargerPk) {
        mapView.overlays.clear()

        if (radiusMiles > 0 && (searchLat != 0.0 || searchLng != 0.0)) {
            val radiusMetres = radiusMiles * 1609.344
            val circle = Polygon(mapView).apply {
                points = Polygon.pointsAsCircle(GeoPoint(searchLat, searchLng), radiusMetres)
                fillPaint.color = android.graphics.Color.argb(25, 33, 150, 243)
                outlinePaint.color = android.graphics.Color.argb(180, 33, 150, 243)
                outlinePaint.strokeWidth = 3f
            }
            mapView.overlays.add(circle)
        }

        if (searchLat != 0.0 || searchLng != 0.0) {
            val centreMarker = Marker(mapView).apply {
                position = GeoPoint(searchLat, searchLng)
                title = "Search location"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            mapView.overlays.add(centreMarker)
        }

        myLocationPoint?.let { myLoc ->
            val myMarker = Marker(mapView).apply {
                position = myLoc
                title = "You are here"
                snippet = "Tap to search here"
                icon = myLocationDrawable(context)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                setOnMarkerClickListener { marker, _ ->
                    if (marker.isInfoWindowShown) {
                        onLocationSelected?.invoke(myLoc.latitude, myLoc.longitude, null)
                        marker.closeInfoWindow()
                    } else {
                        marker.showInfoWindow()
                    }
                    true
                }
            }
            mapView.overlays.add(myMarker)
        }

        // First pass: compute session costs for colour normalisation
        val sessionCosts: List<Double?> = chargers.map { charger ->
            val kw = charger.maxKilowatts ?: return@map null
            val price = charger.pricePerKwh ?: return@map null
            if (session == null) return@map null
            when (priceMode) {
                MapPriceMode.OPTIMAL_COST -> {
                    val result = KonaChargeCurve.simulate(session.startSoc.toFloat(), session.targetSoc.toFloat(), kw, null)
                    KonaChargeCurve.totalCost(result, price, charger.connectionFeeMajor ?: 0.0, charger.chargingTimeRateMajor ?: 0.0, charger.parkingTimeRateMajor ?: 0.0, result.chargeMinutes)
                }
                MapPriceMode.STAY_COST -> {
                    val result = KonaChargeCurve.simulate(session.startSoc.toFloat(), session.targetSoc.toFloat(), kw, session.stayMinutes.toDouble())
                    KonaChargeCurve.totalCost(result, price, charger.connectionFeeMajor ?: 0.0, charger.chargingTimeRateMajor ?: 0.0, charger.parkingTimeRateMajor ?: 0.0, session.stayMinutes.toDouble())
                }
                else -> null
            }
        }
        val paidCosts = sessionCosts.filterNotNull().filter { it > 0.0 }
        val minSessionCost = paidCosts.minOrNull()
        val maxSessionCost = paidCosts.maxOrNull()

        // Second pass: create markers
        chargers.forEachIndexed { idx, charger ->
            val sessionCost = sessionCosts[idx]
            val badgeLabel: String? = sessionCost?.let { "%s%.2f".format(currencySymbol, it) }
            val colorFraction: Float? = when {
                sessionCost == null -> null
                sessionCost <= 0.0 -> 0f
                minSessionCost == null || maxSessionCost == null || maxSessionCost <= minSessionCost -> null
                else -> {
                    val raw = ((sessionCost - minSessionCost) / (maxSessionCost - minSessionCost)).toFloat().coerceIn(0f, 1f)
                    Math.pow(raw.toDouble(), 1.5).toFloat()
                }
            }
            val marker = Marker(mapView).apply {
                position = GeoPoint(charger.coordinates.latitude, charger.coordinates.longitude)
                title = charger.name
                snippet = buildString {
                    charger.pricePerKwh?.let { append("%s%.2f/kWh · ".format(currencySymbol, it)) }
                    append(charger.operator.name)
                    append(if (charger.hasAvailableEvse) " · Available" else " · In use")
                }
                icon = priceBadgeDrawable(context, charger.pricePerKwh, charger.isStale, currencySymbol = currencySymbol, labelOverride = badgeLabel, isSelected = charger.pk == selectedChargerPk, colorFraction = colorFraction)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                setOnMarkerClickListener { _, _ ->
                    dialogCharger = charger
                    true
                }
            }
            mapView.overlays.add(marker)
        }
        if (onLocationSelected != null) {
            mapView.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint) = false
                override fun longPressHelper(p: GeoPoint): Boolean {
                    onLocationSelected(p.latitude, p.longitude, null)
                    return true
                }
            }))
        }

        mapView.invalidate()
    }

    DisposableEffect(Unit) {
        val mapListener = object : MapListener {
            override fun onScroll(event: ScrollEvent): Boolean {
                val c = mapView.mapCenter
                onMapPositionSaved(mapView.zoomLevelDouble, c.latitude, c.longitude)
                return false
            }
            override fun onZoom(event: ZoomEvent): Boolean {
                val c = mapView.mapCenter
                onMapPositionSaved(mapView.zoomLevelDouble, c.latitude, c.longitude)
                return false
            }
        }
        mapView.addMapListener(mapListener)
        mapView.onResume()
        onDispose {
            mapView.removeMapListener(mapListener)
            val c = mapView.mapCenter
            onMapPositionSaved(mapView.zoomLevelDouble, c.latitude, c.longitude)
            mapView.onPause()
        }
    }

    val fusedClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    fun fetchAndShowLocation() {
        goToMyLocation(fusedClient, mapView) { loc -> myLocationPoint = GeoPoint(loc.latitude, loc.longitude) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) fetchAndShowLocation()
    }

    Box(modifier = modifier) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        FloatingActionButton(
            onClick = {
                val hasPerm = androidx.core.content.ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_FINE_LOCATION
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (hasPerm) fetchAndShowLocation()
                else permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Icon(Icons.Default.MyLocation, contentDescription = "Go to my location")
        }
    }

    dialogCharger?.let { charger ->
        AlertDialog(
            onDismissRequest = { dialogCharger = null },
            title = { Text(charger.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "${charger.address}, ${charger.city}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        charger.operator.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    charger.pricePerKwh?.let {
                        Text("%s%.2f/kWh".format(currencySymbol, it), style = MaterialTheme.typography.bodyMedium)
                    }
                    charger.connectionFeeMajor?.let {
                        Text("+ %s%.2f connection fee".format(currencySymbol, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    charger.chargingTimeRateMajor?.let {
                        Text("+ %s%.2f/min while charging".format(currencySymbol, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    charger.parkingTimeRateMajor?.let {
                        Text("+ %s%.2f/min idle fee".format(currencySymbol, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val kw = charger.maxKilowatts
                    val price = charger.pricePerKwh
                    if (session != null && kw != null && price != null) {
                        val optResult = KonaChargeCurve.simulate(session.startSoc.toFloat(), session.targetSoc.toFloat(), kw, null)
                        val optCost = KonaChargeCurve.totalCost(optResult, price, charger.connectionFeeMajor ?: 0.0, charger.chargingTimeRateMajor ?: 0.0, charger.parkingTimeRateMajor ?: 0.0, optResult.chargeMinutes)
                        val stayResult = KonaChargeCurve.simulate(session.startSoc.toFloat(), session.targetSoc.toFloat(), kw, session.stayMinutes.toDouble())
                        val stayCost = KonaChargeCurve.totalCost(stayResult, price, charger.connectionFeeMajor ?: 0.0, charger.chargingTimeRateMajor ?: 0.0, charger.parkingTimeRateMajor ?: 0.0, session.stayMinutes.toDouble())
                        val optMins = optResult.chargeMinutes.toInt()
                        val optSoc = optResult.endSocPercent.toInt()
                        val optLabel = if (optMins >= 180) "≥3h to ${optSoc}%" else "$optMins min → ${optSoc}%"
                        Text(
                            "⚡ Optimal: $optLabel  ·  $currencySymbol${"%.2f".format(optCost)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        val staySoc = stayResult.endSocPercent.toInt()
                        Text(
                            "🕐 In ${session.stayMinutes} min → ${staySoc}%  ·  $currencySymbol${"%.2f".format(stayCost)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        if (charger.hasAvailableEvse) "Available" else "In use",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (charger.isStale) {
                        Text(
                            "! Cached data may be out of date",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Row {
                    val lat = charger.coordinates.latitude
                    val lng = charger.coordinates.longitude
                    TextButton(onClick = {
                        val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(${charger.name})")
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        dialogCharger = null
                    }) {
                        Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Maps")
                    }
                    if (charger.externalId != null) {
                        TextButton(onClick = {
                            val uri = Uri.parse("https://electroverse.octopus.energy/map?extId=${charger.externalId}")
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                            dialogCharger = null
                        }) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Electroverse")
                        }
                    }
                }
            },
            dismissButton = { TextButton(onClick = { dialogCharger = null }) { Text("Close") } }
        )
    }
}

@SuppressLint("MissingPermission")
private fun goToMyLocation(
    fusedClient: com.google.android.gms.location.FusedLocationProviderClient,
    mapView: org.osmdroid.views.MapView,
    onLocation: (android.location.Location) -> Unit = {}
) {
    val cts = CancellationTokenSource()
    fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
        .addOnSuccessListener { loc ->
            if (loc != null) {
                mapView.controller.animateTo(GeoPoint(loc.latitude, loc.longitude))
                mapView.controller.setZoom(15.0)
                onLocation(loc)
            }
        }
}

private fun myLocationDrawable(context: Context): Drawable {
    val dp = context.resources.displayMetrics.density
    val size = (24 * dp).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = size / 2f
    val cy = size / 2f

    // Outer blue circle
    val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(60, 33, 150, 243)
    }
    canvas.drawCircle(cx, cy, cx, outerPaint)

    // White ring
    val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
    }
    canvas.drawCircle(cx, cy, cx * 0.65f, ringPaint)

    // Blue dot
    val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(33, 150, 243)
    }
    canvas.drawCircle(cx, cy, cx * 0.45f, dotPaint)

    return BitmapDrawable(context.resources, bitmap)
}

@Composable
private fun LocalContext() = androidx.compose.ui.platform.LocalContext.current

private fun lerpColor(a: Int, b: Int, t: Float): Int {
    val f = t.coerceIn(0f, 1f)
    return android.graphics.Color.rgb(
        (android.graphics.Color.red(a) + (android.graphics.Color.red(b) - android.graphics.Color.red(a)) * f).toInt(),
        (android.graphics.Color.green(a) + (android.graphics.Color.green(b) - android.graphics.Color.green(a)) * f).toInt(),
        (android.graphics.Color.blue(a) + (android.graphics.Color.blue(b) - android.graphics.Color.blue(a)) * f).toInt()
    )
}

private fun priceBadgeDrawable(context: Context, price: Double?, isStale: Boolean = false, currencySymbol: String = "€", labelOverride: String? = null, isSelected: Boolean = false, colorFraction: Float? = null): Drawable {
    val dp = context.resources.displayMetrics.density
    val border = if (isSelected) (3 * dp) else 0f
    val w = (68 * dp).toInt() + (border * 2).toInt()
    val h = (32 * dp).toInt() + (border * 2).toInt()
    val r = 8 * dp

    val green = android.graphics.Color.rgb(46, 125, 50)
    val orange = android.graphics.Color.rgb(230, 119, 0)
    val red = android.graphics.Color.rgb(183, 28, 28)
    val bgColor = when {
        colorFraction != null -> if (colorFraction < 0.5f) lerpColor(green, orange, colorFraction * 2f) else lerpColor(orange, red, (colorFraction - 0.5f) * 2f)
        price == null -> android.graphics.Color.rgb(100, 100, 100)
        price == 0.0 -> android.graphics.Color.rgb(27, 94, 32)
        price < 0.35 -> green
        price < 0.55 -> orange
        else -> red
    }

    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    if (isSelected) {
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
        canvas.drawRoundRect(0f, 0f, w.toFloat(), h.toFloat(), r + border, r + border, borderPaint)
    }

    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgColor }
    canvas.drawRoundRect(border, border, w - border, h - border, r, r, bgPaint)

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 13 * dp
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    val staleTag = if (isStale) "!" else ""
    val label = labelOverride?.let { "$it$staleTag" } ?: when {
        price == null -> "?$staleTag"
        price == 0.0 -> "FREE$staleTag"
        else -> "%s%.2f$staleTag".format(currencySymbol, price)
    }
    val cx = w / 2f
    val cy = h / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
    canvas.drawText(label, cx, cy, textPaint)

    return BitmapDrawable(context.resources, bitmap)
}
