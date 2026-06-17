package com.richwatson.electrofind.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.richwatson.electrofind.api.models.ChargingLocation
import com.richwatson.electrofind.viewmodel.ChargerViewModel
import kotlinx.coroutines.delay
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseMapScreen(
    initialLat: Double,
    initialLng: Double,
    chargerViewModel: ChargerViewModel,
    onLocationSelected: (Double, Double) -> Unit,
    onBack: () -> Unit
) {
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
            ChargerMapView(
                chargers = emptyList(),
                searchLat = initialLat,
                searchLng = initialLng,
                initialZoom = 8.0,
                centerOn = pendingCenter,
                modifier = Modifier.fillMaxSize(),
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
                        trailingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
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

@Composable
fun ChargerMapView(
    chargers: List<ChargingLocation>,
    searchLat: Double,
    searchLng: Double,
    initialZoom: Double = 14.0,
    centerOn: GeoPoint? = null,
    modifier: Modifier = Modifier,
    onLocationSelected: ((Double, Double) -> Unit)? = null
) {
    val context = LocalContext()
    var dialogCharger by remember { mutableStateOf<ChargingLocation?>(null) }

    val mapView = remember {
        org.osmdroid.views.MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
            controller.setZoom(initialZoom)
            controller.setCenter(GeoPoint(searchLat, searchLng))
        }
    }

    LaunchedEffect(centerOn) {
        if (centerOn != null) {
            mapView.controller.animateTo(centerOn)
            mapView.controller.setZoom(14.0)
        }
    }

    LaunchedEffect(chargers) {
        mapView.overlays.clear()

        if (searchLat != 0.0 || searchLng != 0.0) {
            val centreMarker = Marker(mapView).apply {
                position = GeoPoint(searchLat, searchLng)
                title = "Search location"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            mapView.overlays.add(centreMarker)
        }

        chargers.forEach { charger ->
            val marker = Marker(mapView).apply {
                position = GeoPoint(charger.coordinates.latitude, charger.coordinates.longitude)
                title = charger.name
                snippet = buildString {
                    charger.pricePerKwh?.let { append("€%.2f/kWh · ".format(it)) }
                    append(charger.operator.name)
                    append(if (charger.hasAvailableEvse) " · Available" else " · In use")
                }
                icon = priceBadgeDrawable(context, charger.pricePerKwh, charger.isStale)
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
        mapView.onResume()
        onDispose { mapView.onPause() }
    }

    AndroidView(factory = { mapView }, modifier = modifier)

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
                        Text("€%.2f/kWh".format(it), style = MaterialTheme.typography.bodyMedium)
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
                if (charger.externalId != null) {
                    TextButton(onClick = {
                        val uri = Uri.parse("https://electroverse.octopus.energy/map?extId=${charger.externalId}")
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        dialogCharger = null
                    }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("View on Electroverse")
                    }
                } else {
                    TextButton(onClick = { dialogCharger = null }) { Text("Close") }
                }
            },
            dismissButton = if (charger.externalId != null) {
                { TextButton(onClick = { dialogCharger = null }) { Text("Close") } }
            } else null
        )
    }
}

@Composable
private fun LocalContext() = androidx.compose.ui.platform.LocalContext.current

private fun priceBadgeDrawable(context: Context, price: Double?, isStale: Boolean = false): Drawable {
    val dp = context.resources.displayMetrics.density
    val w = (68 * dp).toInt()
    val h = (32 * dp).toInt()
    val r = 8 * dp

    val bgColor = when {
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
        price == null -> "?$staleTag"
        price == 0.0 -> "FREE$staleTag"
        else -> "€%.2f$staleTag".format(price)
    }
    val yPos = h / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
    canvas.drawText(label, w / 2f, yPos, textPaint)

    return BitmapDrawable(context.resources, bitmap)
}
