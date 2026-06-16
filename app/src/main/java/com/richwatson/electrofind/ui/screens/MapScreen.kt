package com.richwatson.electrofind.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.richwatson.electrofind.api.models.ChargingLocation
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseMapScreen(
    initialLat: Double,
    initialLng: Double,
    onLocationSelected: (Double, Double) -> Unit,
    onBack: () -> Unit
) {
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
        ChargerMapView(
            chargers = emptyList(),
            searchLat = initialLat,
            searchLng = initialLng,
            initialZoom = 8.0,
            modifier = Modifier.padding(padding).fillMaxSize(),
            onLocationSelected = onLocationSelected
        )
    }
}

@Composable
fun ChargerMapView(
    chargers: List<ChargingLocation>,
    searchLat: Double,
    searchLng: Double,
    initialZoom: Double = 14.0,
    modifier: Modifier = Modifier,
    onLocationSelected: ((Double, Double) -> Unit)? = null
) {
    val context = LocalContext.current

    val mapView = remember {
        org.osmdroid.views.MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
            controller.setZoom(initialZoom)
            controller.setCenter(GeoPoint(searchLat, searchLng))
        }
    }

    LaunchedEffect(chargers) {
        mapView.overlays.clear()

        // Search centre pin
        val centreMarker = Marker(mapView).apply {
            position = GeoPoint(searchLat, searchLng)
            title = "Search location"
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }
        mapView.overlays.add(centreMarker)

        chargers.forEach { charger ->
            val marker = Marker(mapView).apply {
                position = GeoPoint(charger.coordinates.latitude, charger.coordinates.longitude)
                title = charger.name
                snippet = buildString {
                    charger.pricePerKwh?.let { append("€%.2f/kWh · ".format(it)) }
                    append(charger.operator.name)
                    append(if (charger.hasAvailableEvse) " · Available" else " · In use")
                }
                icon = priceBadgeDrawable(context, charger.pricePerKwh)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
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
}

private fun priceBadgeDrawable(context: Context, price: Double?): Drawable {
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
    val label = when {
        price == null -> "?"
        price == 0.0 -> "FREE"
        else -> "€%.2f".format(price)
    }
    val yPos = h / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
    canvas.drawText(label, w / 2f, yPos, textPaint)

    return BitmapDrawable(context.resources, bitmap)
}
