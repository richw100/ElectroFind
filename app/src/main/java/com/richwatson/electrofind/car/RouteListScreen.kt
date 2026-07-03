package com.richwatson.electrofind.car

import android.content.Intent
import android.net.Uri
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.richwatson.electrofind.R
import com.richwatson.electrofind.ElectroFindApp
import com.richwatson.electrofind.api.models.ChargingLocation
import com.richwatson.electrofind.api.models.isStaleForRefresh
import com.richwatson.electrofind.model.RouteStop
import com.richwatson.electrofind.model.Trip
import com.richwatson.electrofind.preferences.AppPreferences
import com.richwatson.electrofind.repository.ChargerRepository
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce

@OptIn(kotlinx.coroutines.FlowPreview::class)
class RouteListScreen(carContext: CarContext) : Screen(carContext) {

    private val app = carContext.applicationContext as ElectroFindApp
    private val prefs = AppPreferences(carContext.applicationContext)
    private val repo: ChargerRepository = app.repository
    private val gson = Gson()

    private var trips: List<Trip> = emptyList()
    private var chargerMap: Map<Long, ChargingLocation> = emptyMap()
    private var isLoading = true
    // Room's table-level invalidation re-emits observeByPks() for ANY write to
    // cached_chargers, not just ones affecting our PKs — skip re-parsing JSON that hasn't changed.
    private val parsedCache = mutableMapOf<Long, Pair<String, ChargingLocation>>()

    init {
        loadData()
    }

    private fun loadData() {
        trips = loadTrips()

        if (trips.isEmpty()) {
            isLoading = false
            invalidate()
            return
        }

        lifecycleScope.launch {
            val allPks = trips.flatMap { it.stops }.flatMap { it.chargerPks }.toSet()
            val chargers = repo.getChargersByPks(allPks)
            chargerMap = chargers.associateBy { it.pk }
            isLoading = false
            invalidate()
        }

        // Keep chargerMap live: when StopDetailScreen's Worker saves fresh data to Room,
        // the Flow here re-emits so we pick up the update before the user re-enters a stop
        lifecycleScope.launch {
            val allPks = trips.flatMap { it.stops }.flatMap { it.chargerPks }
            if (allPks.isEmpty()) return@launch
            app.database.chargerDao().observeByPks(allPks).debounce(300).collect { entities ->
                val (updated, changed) = diffCachedChargers(entities, parsedCache, gson)
                if (updated.isNotEmpty()) {
                    chargerMap = chargerMap + updated
                    if (changed) invalidate()
                }
            }
        }
    }

    private fun loadTrips(): List<Trip> {
        val raw = prefs.rawTrips
        if (raw.isNotEmpty() && raw != "[]") {
            return try {
                val type = object : TypeToken<List<Trip>>() {}.type
                gson.fromJson<List<Trip>>(raw, type) ?: emptyList()
            } catch (e: Exception) {
                Log.e("RouteListScreen", "Failed to parse rawTrips", e)
                emptyList()
            }
        }
        // Legacy fallback
        val legacyRaw = prefs.rawRoutePlan
        if (legacyRaw.isNotEmpty()) {
            return try {
                val type = object : TypeToken<List<RouteStop>>() {}.type
                val stops = gson.fromJson<List<RouteStop>>(legacyRaw, type) ?: emptyList()
                if (stops.isEmpty()) emptyList() else listOf(Trip(name = "Trip 1", stops = stops))
            } catch (e: Exception) { emptyList() }
        }
        return emptyList()
    }

    override fun onGetTemplate(): Template {
        if (isLoading) {
            return ListTemplate.Builder()
                .setTitle("Route stops")
                .setLoading(true)
                .setHeaderAction(Action.APP_ICON)
                .build()
        }

        if (trips.isEmpty()) {
            return MessageTemplate.Builder("No route planned.\nAdd stops in the ElectroFind app.")
                .setTitle("Route stops")
                .setHeaderAction(Action.APP_ICON)
                .addAction(
                    Action.Builder()
                        .setTitle("Refresh")
                        .setOnClickListener { loadData() }
                        .build()
                )
                .addAction(
                    Action.Builder()
                        .setTitle("Refresh period")
                        .setOnClickListener { screenManager.push(refreshPeriodPickerScreen()) }
                        .build()
                )
                .build()
        }

        val versionName = try {
            carContext.packageManager.getPackageInfo(carContext.packageName, 0).versionName
        } catch (_: PackageManager.NameNotFoundException) { "" }

        val refreshIcon = CarIcon.Builder(
            IconCompat.createWithResource(carContext, R.drawable.ic_car_refresh)
        ).build()
        val editIcon = CarIcon.Builder(
            IconCompat.createWithResource(carContext, R.drawable.ic_car_edit)
        ).build()

        if (trips.size == 1) {
            return stopsTemplate(trips[0], "Route stops · v$versionName", Action.APP_ICON, refreshIcon, editIcon)
        }

        // Multiple trips — show trip list
        val listBuilder = ItemList.Builder()
        trips.forEach { trip ->
            val stopCount = trip.stops.size
            val firstCharger = trip.stops.firstOrNull()?.let { chargerMap[it.activePk] }
            val firstName = firstCharger?.name ?: ""
            val firstWarn = if (firstCharger != null && isStaleForRefresh(firstCharger.cachedAt, prefs.refreshPeriodMs)) "! " else ""
            val subtitle = buildString {
                append("$stopCount stop${if (stopCount != 1) "s" else ""}")
                if (firstName.isNotEmpty()) append(" · $firstWarn$firstName…")
            }
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(trip.name)
                    .addText(subtitle)
                    .setOnClickListener { screenManager.push(tripStopsScreen(trip)) }
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setTitle("Route stops · v$versionName")
            .setHeaderAction(Action.APP_ICON)
            .setSingleList(listBuilder.build())
            .addAction(
                Action.Builder()
                    .setIcon(refreshIcon)
                    .setBackgroundColor(CarColor.PRIMARY)
                    .setOnClickListener { loadData() }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setIcon(editIcon)
                    .setBackgroundColor(CarColor.SECONDARY)
                    .setOnClickListener { screenManager.push(refreshPeriodPickerScreen()) }
                    .build()
            )
            .build()
    }

    private fun tripStopsScreen(trip: Trip): Screen = object : Screen(carContext) {
        override fun onGetTemplate(): Template {
            val refreshIcon = CarIcon.Builder(
                IconCompat.createWithResource(carContext, R.drawable.ic_car_refresh)
            ).build()
            val editIcon = CarIcon.Builder(
                IconCompat.createWithResource(carContext, R.drawable.ic_car_edit)
            ).build()
            return stopsTemplate(trip, trip.name, Action.BACK, refreshIcon, editIcon)
        }
    }

    private fun refreshPeriodPickerScreen(): Screen = object : Screen(carContext) {
        var page = 0
        override fun onGetTemplate(): Template {
            val current = (prefs.refreshPeriodMs / 60_000L).toInt()
            val rows = listOf(1, 2, 5, 10, 15, 30).map { minutes ->
                Row.Builder()
                    .setTitle(if (minutes == current) "★ $minutes min" else "$minutes min")
                    .setOnClickListener {
                        prefs.refreshPeriodMs = minutes * 60_000L
                        invalidate()
                        screenManager.pop()
                    }
                    .build()
            }
            return carContext.pagedListTemplate("Refresh period", rows, page) { page = it; invalidate() }
        }
    }

    private fun stopsTemplate(trip: Trip, title: String, headerAction: Action, refreshIcon: CarIcon, editIcon: CarIcon): ListTemplate {
        val navigateIcon = CarIcon.Builder(
            IconCompat.createWithResource(carContext, R.drawable.ic_car_navigate)
        ).build()

        val listBuilder = ItemList.Builder()
        trip.stops.forEachIndexed { idx, stop ->
            val charger = chargerMap[stop.activePk]
            val rowBuilder = Row.Builder()
                .setTitle(stop.displayName(idx))
                .setOnClickListener {
                    screenManager.push(StopDetailScreen(carContext, stop, chargerMap, idx))
                }

            if (charger == null) {
                rowBuilder.addText("Loading…")
            } else {
                val (line1, line2) = charger.chargerDetailLines(stop)
                val warn = if (isStaleForRefresh(charger.cachedAt, prefs.refreshPeriodMs)) "! " else ""
                rowBuilder.addText(if (line1.isNotEmpty()) "$warn${charger.name} · $line1" else "$warn${charger.name}")
                if (line2.isNotEmpty()) rowBuilder.addText(line2)
                val lat = charger.coordinates.latitude
                val lng = charger.coordinates.longitude
                val mapsUri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(${charger.name})")
                rowBuilder.addAction(
                    Action.Builder()
                        .setIcon(navigateIcon)
                        .setOnClickListener {
                            carContext.startCarApp(Intent(CarContext.ACTION_NAVIGATE, mapsUri))
                        }
                        .build()
                )
            }
            listBuilder.addItem(rowBuilder.build())
        }

        return ListTemplate.Builder()
            .setTitle(title)
            .setHeaderAction(headerAction)
            .setSingleList(listBuilder.build())
            .addAction(
                Action.Builder()
                    .setIcon(refreshIcon)
                    .setBackgroundColor(CarColor.PRIMARY)
                    .setOnClickListener { loadData() }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setIcon(editIcon)
                    .setBackgroundColor(CarColor.SECONDARY)
                    .setOnClickListener { screenManager.push(refreshPeriodPickerScreen()) }
                    .build()
            )
            .build()
    }
}
