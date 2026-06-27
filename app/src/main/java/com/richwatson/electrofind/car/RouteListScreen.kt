package com.richwatson.electrofind.car

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
import com.richwatson.electrofind.model.RouteStop
import com.richwatson.electrofind.model.Trip
import com.richwatson.electrofind.preferences.AppPreferences
import com.richwatson.electrofind.repository.ChargerRepository
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.launch

class RouteListScreen(carContext: CarContext) : Screen(carContext) {

    private val app = carContext.applicationContext as ElectroFindApp
    private val prefs = AppPreferences(carContext.applicationContext)
    private val repo: ChargerRepository = app.repository
    private val gson = Gson()

    private var trips: List<Trip> = emptyList()
    private var chargerMap: Map<Long, ChargingLocation> = emptyMap()
    private var isLoading = true

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
                .build()
        }

        val versionName = try {
            carContext.packageManager.getPackageInfo(carContext.packageName, 0).versionName
        } catch (_: PackageManager.NameNotFoundException) { "" }

        val refreshIcon = CarIcon.Builder(
            IconCompat.createWithResource(carContext, R.drawable.ic_car_refresh)
        ).build()

        if (trips.size == 1) {
            return stopsTemplate(trips[0], "Route stops · v$versionName", Action.APP_ICON, refreshIcon)
        }

        // Multiple trips — show trip list
        val listBuilder = ItemList.Builder()
        trips.forEach { trip ->
            val stopCount = trip.stops.size
            val firstName = trip.stops.firstOrNull()?.let { chargerMap[it.activePk]?.name } ?: ""
            val subtitle = buildString {
                append("$stopCount stop${if (stopCount != 1) "s" else ""}")
                if (firstName.isNotEmpty()) append(" · $firstName…")
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
            .build()
    }

    private fun tripStopsScreen(trip: Trip): Screen = object : Screen(carContext) {
        override fun onGetTemplate(): Template {
            val refreshIcon = CarIcon.Builder(
                IconCompat.createWithResource(carContext, R.drawable.ic_car_refresh)
            ).build()
            return stopsTemplate(trip, trip.name, Action.BACK, refreshIcon)
        }
    }

    private fun stopsTemplate(trip: Trip, title: String, headerAction: Action, refreshIcon: CarIcon): ListTemplate {
        val listBuilder = ItemList.Builder()
        trip.stops.forEachIndexed { idx, stop ->
            val charger = chargerMap[stop.activePk]
            val chargerName = charger?.name ?: "Loading…"
            val availability = charger?.availabilitySummary() ?: ""
            val description = if (availability.isNotEmpty()) "$chargerName · $availability" else chargerName

            listBuilder.addItem(
                Row.Builder()
                    .setTitle(stop.displayName(idx))
                    .addText(description)
                    .setOnClickListener {
                        screenManager.push(StopDetailScreen(carContext, stop, chargerMap))
                    }
                    .build()
            )
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
            .build()
    }
}

internal fun ChargingLocation.availabilitySummary(): String {
    val byKw = availabilityByKw
    if (byKw.isEmpty()) return ""
    return byKw.entries
        .sortedByDescending { it.key }
        .joinToString(" · ") { (kw, counts) ->
            val (avail, inUse, fault) = counts
            val parts = listOfNotNull(
                if (avail > 0) "$avail av" else null,
                if (inUse > 0) "$inUse use" else null,
                if (fault > 0) "$fault fault${if (fault > 1) "s" else ""}" else null
            )
            "${kw}kW: ${parts.joinToString(", ")}"
        }
}
