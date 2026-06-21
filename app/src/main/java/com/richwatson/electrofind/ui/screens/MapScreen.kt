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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedButton
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
import androidx.compose.ui.platform.LocalFocusManager
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
    val focusManager = LocalFocusManager.current
    var searchText by remember { mutableStateOf("") }
    var suggestionsExpanded by remember { mutableStateOf(false) }
    var pendingCenter by remember { mutableStateOf<GeoPoint?>(null) }
    var fieldFocused by remember { mutableStateOf(false) }
    // Tracks the visible map centre so "Search here" always searches where the map is.
    var mapCenterLat by remember {
        mutableStateOf(if (state.savedMapCenterLat != 0.0 || state.savedMapCenterLng != 0.0) state.savedMapCenterLat else initialLat)
    }
    var mapCenterLng by remember {
        mutableStateOf(if (state.savedMapCenterLat != 0.0 || state.savedMapCenterLng != 0.0) state.savedMapCenterLng else initialLng)
    }

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
                chargers = state.favouriteChargers,
                searchLat = initialLat,
                searchLng = initialLng,
                initialZoom = if (hasSaved) state.savedMapZoom else 8.0,
                initialCenter = if (hasSaved) GeoPoint(savedLat, savedLng) else null,
                centerOn = pendingCenter,
                radiusMiles = 0,
                currencySymbol = state.currencySymbol,
                favouritePks = state.favouritePks,
                excludedPks = state.excludedPks,
                onToggleFavourite = { chargerViewModel.toggleFavourite(it) },
                onToggleExcluded = { chargerViewModel.toggleExcluded(it) },
                modifier = Modifier.fillMaxSize(),
                onMapPositionSaved = { zoom, lat, lng ->
                    chargerViewModel.saveMapPosition(zoom, lat, lng)
                    mapCenterLat = lat
                    mapCenterLng = lng
                },
                onMapTapped = {
                    focusManager.clearFocus()
                    suggestionsExpanded = false
                },
                onLocationSelected = onLocationSelected
            )

            // "Search here" button — searches at whatever the map is centred on
            ElevatedButton(
                onClick = { onLocationSelected(mapCenterLat, mapCenterLng, null) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp)
            ) {
                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Search here")
            }

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
                                pendingCenter = GeoPoint(top.lat, top.lng)
                                mapCenterLat = top.lat
                                mapCenterLng = top.lng
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
                                    pendingCenter = GeoPoint(top.lat, top.lng)
                                    mapCenterLat = top.lat
                                    mapCenterLng = top.lng
                                } else {
                                    chargerViewModel.fetchSuggestions(searchText)
                                    suggestionsExpanded = true
                                }
                            }) {
                                Icon(Icons.Default.Search, contentDescription = "Go to location")
                            }
                        }
                    )
                    if (suggestionsExpanded && suggestions.isNotEmpty()) {
                        HorizontalDivider()
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                            items(suggestions) { suggestion ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                suggestionsExpanded = false
                                                chargerViewModel.clearSuggestions()
                                                keyboardController?.hide()
                                                pendingCenter = GeoPoint(suggestion.lat, suggestion.lng)
                                                mapCenterLat = suggestion.lat
                                                mapCenterLng = suggestion.lng
                                            }
                                            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp)
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
                                    IconButton(
                                        onClick = {
                                            suggestionsExpanded = false
                                            chargerViewModel.clearSuggestions()
                                            keyboardController?.hide()
                                            onLocationSelected(suggestion.lat, suggestion.lng, suggestion.primaryName)
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.Search,
                                            contentDescription = "Search here",
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.primary
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
                                            pendingCenter = GeoPoint(entry.lat, entry.lng)
                                            mapCenterLat = entry.lat
                                            mapCenterLng = entry.lng
                                            suggestionsExpanded = false
                                        }
                                        .padding(start = 16.dp, end = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        entry.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f).padding(vertical = 10.dp)
                                    )
                                    IconButton(
                                        onClick = {
                                            keyboardController?.hide()
                                            onLocationSelected(entry.lat, entry.lng, entry.label)
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.Search,
                                            contentDescription = "Search here",
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
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
    chargerViewModel: ChargerViewModel,
    onClear: () -> Unit = {}
) {
    val state by chargerViewModel.state.collectAsState()
    val chargers = remember(state) {
        val results = chargerViewModel.filteredSortedChargers
        val resultPks = results.map { it.pk }.toSet()
        val extraFavourites = state.favouriteChargers.filter { it.pk !in resultPks }
        results + extraFavourites
    }
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
                    IconButton(onClick = {
                        chargerViewModel.clearResults()
                        onClear()
                    }) {
                        Icon(Icons.Default.Close, "Clear results")
                    }
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
                session = ChargeSession(state.startSocPercent, state.targetSocPercent, state.stayMinutes, state.activeProfile),
                priceMode = priceMode,
                selectedChargerPk = state.selectedChargerPk,
                favouritePks = state.favouritePks,
                excludedPks = state.excludedPks,
                onToggleFavourite = { chargerViewModel.toggleFavourite(it) },
                onToggleExcluded = { chargerViewModel.toggleExcluded(it) },
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
    favouritePks: Set<Long> = emptySet(),
    excludedPks: Set<Long> = emptySet(),
    onToggleFavourite: (Long) -> Unit = {},
    onToggleExcluded: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
    onMapPositionSaved: (zoom: Double, lat: Double, lng: Double) -> Unit = { _, _, _ -> },
    onMapTapped: () -> Unit = {},
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

    LaunchedEffect(chargers, radiusMiles, myLocationPoint, currencySymbol, priceMode, session, selectedChargerPk, favouritePks, excludedPks) {
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

        // First pass: compute a score per charger for colour normalisation.
        // PER_KWH uses raw price; session modes use simulated cost.
        // Free chargers score 0 (always green) and are excluded from the rank baseline.
        val scores: List<Double?> = chargers.map { charger ->
            when (priceMode) {
                MapPriceMode.PER_KWH -> charger.pricePerKwh
                else -> {
                    val kw = charger.maxKilowatts ?: return@map null
                    val price = charger.pricePerKwh ?: return@map null
                    if (session == null) return@map null
                    when (priceMode) {
                        MapPriceMode.OPTIMAL_COST -> {
                            val result = KonaChargeCurve.simulate(session.startSoc.toFloat(), session.targetSoc.toFloat(), kw, null, profile = session.profile)
                            KonaChargeCurve.totalCost(result, price, charger.connectionFeeMajor ?: 0.0, charger.chargingTimeRateMajor ?: 0.0, charger.parkingTimeRateMajor ?: 0.0, result.chargeMinutes)
                        }
                        MapPriceMode.STAY_COST -> {
                            val result = KonaChargeCurve.simulate(session.startSoc.toFloat(), session.targetSoc.toFloat(), kw, session.stayMinutes.toDouble(), profile = session.profile)
                            KonaChargeCurve.totalCost(result, price, charger.connectionFeeMajor ?: 0.0, charger.chargingTimeRateMajor ?: 0.0, charger.parkingTimeRateMajor ?: 0.0, session.stayMinutes.toDouble())
                        }
                        else -> null
                    }
                }
            }
        }
        // Sorted paid scores for blended colouring: 50% percentile rank + 50% linear min-max.
        // Percentile ensures spread; linear preserves the signal that genuinely cheap chargers
        // look meaningfully greener than moderately cheap ones.
        val sortedPaidScores = scores.filterNotNull().filter { it > 0.0 }.sorted()
        val minPaidScore = sortedPaidScores.firstOrNull() ?: 0.0
        val maxPaidScore = sortedPaidScores.lastOrNull() ?: 0.0

        // Colour constants reused in badge rows
        val colorGreen  = android.graphics.Color.rgb(46, 125, 50)
        val colorOrange = android.graphics.Color.rgb(230, 119, 0)
        val colorRed    = android.graphics.Color.rgb(183, 28, 28)
        val colorGray   = android.graphics.Color.rgb(100, 100, 100)
        val colorFree   = android.graphics.Color.rgb(27, 94, 32)

        // Second pass: create markers
        chargers.forEachIndexed { idx, charger ->
            val score = scores[idx]
            // Only show session-cost label in session-cost modes; PER_KWH badge shows price via price param
            val badgeLabel: String? = if (priceMode != MapPriceMode.PER_KWH) score?.let { "%s%.2f".format(currencySymbol, it) } else null
            // Blended colour fraction: 50% percentile rank + 50% linear min-max.
            // Gives a spread across the full colour range while still showing meaningful
            // differences between genuinely cheap and moderately priced chargers.
            val colorFraction: Float? = when {
                score == null -> null
                score <= 0.0 -> 0f
                sortedPaidScores.size <= 1 -> 0f
                else -> {
                    val percentile = sortedPaidScores.count { it < score }.toFloat() / sortedPaidScores.size.toFloat()
                    val linear = if (maxPaidScore > minPaidScore)
                        ((score - minPaidScore) / (maxPaidScore - minPaidScore)).toFloat().coerceIn(0f, 1f)
                    else 0f
                    ((percentile + linear) / 2f).coerceIn(0f, 1f)
                }
            }

            // Multi-row badge: one row per distinct price, showing only the fastest connector at that price
            val summaries = charger.connectorPriceSummaries
                .groupBy { it.pricePerKwh to it.isFree }
                .map { (_, group) -> group.first() }  // first() is fastest (already sorted desc by kW)
                .sortedByDescending { it.kilowatts ?: 0.0 }
                .take(4)
            val rows: List<Pair<String, Int>>? = if (summaries.size < 2) null else {
                summaries.map { s ->
                    val kwLabel = s.kilowatts?.let { "${it.toInt()}kW" } ?: "?kW"
                    val priceLabel = when {
                        s.isFree -> "FREE"
                        session != null && s.kilowatts != null && s.pricePerKwh != null &&
                            (priceMode == MapPriceMode.OPTIMAL_COST || priceMode == MapPriceMode.STAY_COST) -> {
                            val sim = KonaChargeCurve.simulate(
                                session.startSoc.toFloat(), session.targetSoc.toFloat(),
                                s.kilowatts,
                                if (priceMode == MapPriceMode.STAY_COST) session.stayMinutes.toDouble() else null,
                                profile = session.profile
                            )
                            val stayMins = if (priceMode == MapPriceMode.STAY_COST) session.stayMinutes.toDouble() else sim.chargeMinutes
                            val cost = KonaChargeCurve.totalCost(sim, s.pricePerKwh,
                                charger.connectionFeeMajor ?: 0.0, charger.chargingTimeRateMajor ?: 0.0,
                                charger.parkingTimeRateMajor ?: 0.0, stayMins)
                            "%s%.2f".format(currencySymbol, cost)
                        }
                        s.pricePerKwh != null -> "%s%.2f".format(currencySymbol, s.pricePerKwh)
                        else -> "?"
                    }
                    val rowColor = when {
                        s.isFree -> colorFree
                        s.pricePerKwh == null -> colorGray
                        s.pricePerKwh < 0.35 -> colorGreen
                        s.pricePerKwh < 0.55 -> colorOrange
                        else -> colorRed
                    }
                    Pair("$kwLabel  $priceLabel", rowColor)
                }
            }

            val marker = Marker(mapView).apply {
                position = GeoPoint(charger.coordinates.latitude, charger.coordinates.longitude)
                title = charger.name
                snippet = buildString {
                    charger.pricePerKwh?.let { append("%s%.2f/kWh · ".format(currencySymbol, it)) }
                    append(charger.operator.name)
                    val statusText = when {
                        charger.hasAvailableEvse  -> "Available"
                        charger.hasOutOfOrderEvse -> "Out of order"
                        else                      -> "In use"
                    }
                    append(" · $statusText")
                }
                icon = priceBadgeDrawable(
                    context, charger.pricePerKwh, charger.isStale,
                    currencySymbol = currencySymbol,
                    labelOverride = badgeLabel,
                    isSelected = charger.pk == selectedChargerPk,
                    colorFraction = colorFraction,
                    isFavourite = charger.pk in favouritePks,
                    isExcluded = charger.pk in excludedPks,
                    isInUse = !charger.hasAvailableEvse && !charger.hasOutOfOrderEvse,
                    isOutOfOrder = charger.hasOutOfOrderEvse,
                    rows = rows
                )
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                setOnMarkerClickListener { _, _ ->
                    dialogCharger = charger
                    true
                }
            }
            mapView.overlays.add(marker)
        }
        mapView.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                onMapTapped()
                return false
            }
            override fun longPressHelper(p: GeoPoint): Boolean {
                onLocationSelected?.invoke(p.latitude, p.longitude, null)
                return onLocationSelected != null
            }
        }))

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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { onToggleFavourite(charger.pk) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                if (charger.pk in favouritePks) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Toggle favourite",
                                tint = if (charger.pk in favouritePks) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = { onToggleExcluded(charger.pk) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Block,
                                contentDescription = "Toggle excluded",
                                tint = if (charger.pk in excludedPks) Color(0xFFE65100) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        if (charger.pk in favouritePks) Text("Favourite", style = MaterialTheme.typography.labelSmall, color = Color(0xFFE53935))
                        if (charger.pk in excludedPks) Text("Excluded", style = MaterialTheme.typography.labelSmall, color = Color(0xFFE65100))
                    }
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
                            Text(typeLabel, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Text(kwLabel, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(64.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                            Text(priceLabel, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(88.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                        }
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
                            val optResult = KonaChargeCurve.simulate(session.startSoc.toFloat(), session.targetSoc.toFloat(), kw, null, profile = session.profile)
                            val optCost = KonaChargeCurve.totalCost(optResult, price, charger.connectionFeeMajor ?: 0.0, charger.chargingTimeRateMajor ?: 0.0, charger.parkingTimeRateMajor ?: 0.0, optResult.chargeMinutes)
                            val stayResult = KonaChargeCurve.simulate(session.startSoc.toFloat(), session.targetSoc.toFloat(), kw, session.stayMinutes.toDouble(), profile = session.profile)
                            val stayCost = KonaChargeCurve.totalCost(stayResult, price, charger.connectionFeeMajor ?: 0.0, charger.chargingTimeRateMajor ?: 0.0, charger.parkingTimeRateMajor ?: 0.0, session.stayMinutes.toDouble())
                            val optMins = optResult.chargeMinutes.toInt()
                            val optSoc = optResult.endSocPercent.toInt()
                            val optLabel = if (optMins >= 180) "≥3h → ${optSoc}%" else "$optMins min → ${optSoc}%"
                            val staySoc = stayResult.endSocPercent.toInt()
                            val fmt: (Double) -> String = { c -> if (s.isFree) "FREE" else "$currencySymbol${"%.2f".format(c)}" }
                            Row(verticalAlignment = androidx.compose.ui.Alignment.Top) {
                                if (multiGroup) {
                                    Text(
                                        "${kw.toInt()} kW",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.width(52.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        "⚡ Optimal: $optLabel  ·  ${fmt(optCost)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        "🕐 In ${session.stayMinutes} min → ${staySoc}%  ·  ${fmt(stayCost)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
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

private fun priceBadgeDrawable(
    context: Context, price: Double?, isStale: Boolean = false,
    currencySymbol: String = "€", labelOverride: String? = null,
    isSelected: Boolean = false, colorFraction: Float? = null,
    isFavourite: Boolean = false, isExcluded: Boolean = false,
    isInUse: Boolean = false, isOutOfOrder: Boolean = false,
    rows: List<Pair<String, Int>>? = null
): Drawable {
    val dp = context.resources.displayMetrics.density
    val border = if (isSelected) (3 * dp) else 0f
    val r = 8 * dp
    val isMultiRow = rows != null && rows.size > 1
    val rowH = 18 * dp
    val padV = 3 * dp

    val w: Int
    val h: Int
    if (isMultiRow) {
        w = (84 * dp).toInt() + (border * 2).toInt()
        h = (rowH * rows!!.size + padV * 2).toInt() + (border * 2).toInt()
    } else {
        w = (68 * dp).toInt() + (border * 2).toInt()
        h = (32 * dp).toInt() + (border * 2).toInt()
    }

    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    if (isSelected) {
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
        canvas.drawRoundRect(0f, 0f, w.toFloat(), h.toFloat(), r + border, r + border, borderPaint)
    }

    if (isMultiRow) {
        canvas.save()
        val clipPath = android.graphics.Path().apply {
            addRoundRect(border, border, w - border, h - border, r, r, android.graphics.Path.Direction.CW)
        }
        canvas.clipPath(clipPath)
        rows!!.forEachIndexed { i, (label, bgColor) ->
            val rowTop = border + padV + i * rowH
            val rowBottom = rowTop + rowH
            canvas.drawRect(border, rowTop, w - border, rowBottom,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgColor })
            if (i < rows.size - 1) {
                canvas.drawRect(border, rowBottom - dp * 0.5f, w - border, rowBottom,
                    Paint().apply { color = android.graphics.Color.argb(80, 255, 255, 255) })
            }
            val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                textSize = 11 * dp
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
            }
            canvas.drawText(label, w / 2f, rowTop + rowH / 2f - (tp.descent() + tp.ascent()) / 2f, tp)
        }
        canvas.restore()
    } else {
        val green = android.graphics.Color.rgb(46, 125, 50)
        val orange = android.graphics.Color.rgb(230, 119, 0)
        val red = android.graphics.Color.rgb(183, 28, 28)
        val bgColor = when {
            colorFraction != null -> if (colorFraction < 0.5f) lerpColor(green, orange, colorFraction * 2f)
                                     else lerpColor(orange, red, (colorFraction - 0.5f) * 2f)
            price == null -> android.graphics.Color.rgb(100, 100, 100)
            price == 0.0  -> android.graphics.Color.rgb(27, 94, 32)
            price < 0.35  -> green
            price < 0.55  -> orange
            else           -> red
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
            price == 0.0  -> "FREE$staleTag"
            else           -> "%s%.2f$staleTag".format(currencySymbol, price)
        }
        canvas.drawText(label, w / 2f, h / 2f - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint)
    }

    if (!isSelected) {
        val strokeColor: Int? = when {
            isOutOfOrder -> android.graphics.Color.rgb(183, 28, 28)
            isInUse      -> android.graphics.Color.rgb(230, 130, 0)
            else         -> null
        }
        if (strokeColor != null) {
            // Draw at (0,0,w,h) so the outer half clips to the bitmap edge — corners align exactly with badge shape.
            // White halo (wider) drawn first so the coloured ring is visible on any background colour.
            canvas.drawRoundRect(0f, 0f, w.toFloat(), h.toFloat(), r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = 5f * dp
            })
            canvas.drawRoundRect(0f, 0f, w.toFloat(), h.toFloat(), r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = strokeColor
                style = Paint.Style.STROKE
                strokeWidth = 3f * dp
            })
        }
    }

    fun drawSymbol(symbol: String, x: Float, y: Float, align: Paint.Align, fillColor: Int) {
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11 * dp; textAlign = align
            color = android.graphics.Color.argb(200, 0, 0, 0)
            style = Paint.Style.STROKE; strokeWidth = 2.5f * dp; strokeJoin = Paint.Join.ROUND
        }
        canvas.drawText(symbol, x, y, outline)
        canvas.drawText(symbol, x, y, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11 * dp; textAlign = align
            color = fillColor; style = Paint.Style.FILL; isFakeBoldText = true
        })
    }

    val symY = if (isMultiRow) border + padV + rowH / 2f + 4 * dp else border + 11 * dp

    if (isInUse || isOutOfOrder) {
        drawSymbol(if (isOutOfOrder) "✕" else "⚡", border + 2 * dp, symY,
            Paint.Align.LEFT, android.graphics.Color.WHITE)
    }
    if (isFavourite || isExcluded) {
        drawSymbol(
            if (isFavourite) "♥" else "✕", w - border - 2 * dp, symY, Paint.Align.RIGHT,
            if (isFavourite) android.graphics.Color.rgb(255, 100, 100) else android.graphics.Color.WHITE
        )
    }

    return BitmapDrawable(context.resources, bitmap)
}
