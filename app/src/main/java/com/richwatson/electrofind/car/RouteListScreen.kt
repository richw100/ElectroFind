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
import com.richwatson.electrofind.preferences.AppPreferences
import com.richwatson.electrofind.repository.ChargerRepository
import kotlinx.coroutines.launch

class RouteListScreen(carContext: CarContext) : Screen(carContext) {

    private val app = carContext.applicationContext as ElectroFindApp
    private val prefs = AppPreferences(carContext.applicationContext)
    private val repo: ChargerRepository = app.repository
    private val gson = Gson()

    private var routeStops: List<RouteStop> = emptyList()
    private var chargerMap: Map<Long, ChargingLocation> = emptyMap()
    private var isLoading = true

    init {
        loadData()
    }

    private fun loadData() {
        val raw = prefs.rawRoutePlan
        routeStops = if (raw.isEmpty()) emptyList() else try {
            val type = object : TypeToken<List<RouteStop>>() {}.type
            gson.fromJson<List<RouteStop>>(raw, type) ?: emptyList()
        } catch (e: Exception) { emptyList() }

        if (routeStops.isEmpty()) {
            isLoading = false
            invalidate()
            return
        }

        lifecycleScope.launch {
            val allPks = routeStops.flatMap { it.chargerPks }.toSet()
            val chargers = repo.getChargersByPks(allPks)
            chargerMap = chargers.associateBy { it.pk }
            isLoading = false
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        if (isLoading) {
            return ListTemplate.Builder()
                .setTitle("Route stops")
                .setLoading(true)
                .setHeaderAction(Action.APP_ICON)
                .build()
        }

        if (routeStops.isEmpty()) {
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

        val listBuilder = ItemList.Builder()
        routeStops.forEachIndexed { idx, stop ->
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

        val refreshIcon = CarIcon.Builder(
            IconCompat.createWithResource(carContext, R.drawable.ic_car_refresh)
        ).build()

        return ListTemplate.Builder()
            .setTitle("Route stops")
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
}

internal fun ChargingLocation.availabilitySummary(): String {
    val evseNodes = evses.edges.map { it.node }
    val nAvail = evseNodes.count { it.status == "AVAILABLE" }
    val nFault = evseNodes.count { it.status in setOf("INOPERATIVE", "FAULTED", "UNAVAILABLE", "OUT_OF_ORDER") }
    val nInUse = evseNodes.size - nAvail - nFault
    return listOfNotNull(
        if (nAvail > 0) "$nAvail avail" else null,
        if (nInUse > 0) "$nInUse in use" else null,
        if (nFault > 0) "$nFault fault${if (nFault > 1) "s" else ""}" else null
    ).joinToString(" · ")
}
