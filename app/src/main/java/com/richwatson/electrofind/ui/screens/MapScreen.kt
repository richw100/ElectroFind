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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.richwatson.electrofind.api.models.ChargingLocation
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
    onLocationSelected: (Double, Double) -> Unit,
    onBack: () -> Unit
) {
    val state by chargerViewModel.state.collectAsState()
    val suggestions by chargerViewModel.suggestions.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    var searchText by remember { mutableStateOf("") }
    var suggestionsExpanded by remember { mutableStateOf(false) }
    var pendingCenter by remember { mutableStateOf<GeoPoint?>(null) }

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
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            val top = suggestions.firstOrNull()
                            if (top != null) {
                                searchText = top.primaryName
                                suggestionsExpanded = false
                                chargerViewModel.clearSuggestions()
                                keyboardController?.hide()
                                pendingCenter = GeoPoint(top.lat, top.lng)
                            } else {
                                chargerViewModel.fetchSuggestions(searchText)
                                suggestionsExpanded = true
                            }
                        }),
                        trailingIcon = {
                            IconButton(onClick = {
                                val top = suggestions.firstOrNull()
                                if (top != null) {
                                    searchText = top.primaryName
                                    suggestionsExpanded = false
                                    chargerViewModel.clearSuggestions()
                                    keyboardController?.hide()
                                    pendingCenter = GeoPoint(top.lat, top.lng)
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
                                            searchText = suggestion.primaryName
                                            suggestionsExpanded = false
                                            chargerViewModel.clearSuggestions()
                                            keyboardController?.hide()
                                            pendingCenter = GeoPoint(suggestion.lat, suggestion.lng)
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
                title = { Text("Map · ${chargers.size} chargers") },
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
                modifier = Modifier.fillMaxSize(),
                onMapPositionSaved = { zoom, lat, lng ->
                    chargerViewModel.saveMapPosition(zoom, lat, lng)
                },
                onLocationSelected = { lat, lng ->
                    chargerViewModel.searchByCoordinates(lat, lng)
                }
            )
            if (showFilters) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    FilterBar(chargerViewModel, showSort = false)
                }
            }
        }
    }
}

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
    modifier: Modifier = Modifier,
    onMapPositionSaved: (zoom: Double, lat: Double, lng: Double) -> Unit = { _, _, _ -> },
    onLocationSelected: ((Double, Double) -> Unit)? = null
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

    LaunchedEffect(chargers, radiusMiles, myLocationPoint, currencySymbol) {
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
                        onLocationSelected?.invoke(myLoc.latitude, myLoc.longitude)
                        marker.closeInfoWindow()
                    } else {
                        marker.showInfoWindow()
                    }
                    true
                }
            }
            mapView.overlays.add(myMarker)
        }

        chargers.forEach { charger ->
            val marker = Marker(mapView).apply {
                position = GeoPoint(charger.coordinates.latitude, charger.coordinates.longitude)
                title = charger.name
                snippet = buildString {
                    charger.pricePerKwh?.let { append("%s%.2f/kWh · ".format(currencySymbol, it)) }
                    append(charger.operator.name)
                    append(if (charger.hasAvailableEvse) " · Available" else " · In use")
                }
                icon = priceBadgeDrawable(context, charger.pricePerKwh, charger.isStale, isOcm = charger.sourceDisplay == com.richwatson.electrofind.api.models.DataSource.OCM, currencySymbol = currencySymbol)
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
                    onLocationSelected(p.latitude, p.longitude)
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

private fun priceBadgeDrawable(context: Context, price: Double?, isStale: Boolean = false, isOcm: Boolean = false, currencySymbol: String = "€"): Drawable {
    val dp = context.resources.displayMetrics.density
    val w = (68 * dp).toInt()
    val h = (32 * dp).toInt()
    val r = 8 * dp

    val bgColor = when {
        isOcm -> android.graphics.Color.rgb(33, 150, 243)
        price == null -> android.graphics.Color.rgb(100, 100, 100)
        price == 0.0 -> android.graphics.Color.rgb(27, 94, 32)
        price < 0.35 -> android.graphics.Color.rgb(46, 125, 50)
        price < 0.55 -> android.graphics.Color.rgb(230, 119, 0)
        else -> android.graphics.Color.rgb(183, 28, 28)
    }

    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgColor }
    canvas.drawRoundRect(0f, 0f, w.toFloat(), h.toFloat(), r, r, bgPaint)

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 13 * dp
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    val staleTag = if (isStale) "!" else ""
    val label = when {
        isOcm -> "OCM$staleTag"
        price == null -> "?$staleTag"
        price == 0.0 -> "FREE$staleTag"
        else -> "%s%.2f$staleTag".format(currencySymbol, price)
    }
    val yPos = h / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
    canvas.drawText(label, w / 2f, yPos, textPaint)

    return BitmapDrawable(context.resources, bitmap)
}
