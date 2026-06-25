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
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.richwatson.electrofind.ElectroFindApp
import com.richwatson.electrofind.R
import com.richwatson.electrofind.api.models.ChargingLocation
import com.richwatson.electrofind.model.CarProfile
import com.richwatson.electrofind.model.RouteStop
import com.richwatson.electrofind.preferences.AppPreferences
import com.richwatson.electrofind.repository.ChargerRepository
import com.richwatson.electrofind.util.KonaChargeCurve
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class StopDetailScreen(
    carContext: CarContext,
    private var stop: RouteStop,
    initialChargerMap: Map<Long, ChargingLocation>
) : Screen(carContext) {

    private val app = carContext.applicationContext as ElectroFindApp
    private val prefs = AppPreferences(carContext.applicationContext)
    private val repo: ChargerRepository = app.repository
    private val gson = Gson()

    private var chargerMap: Map<Long, ChargingLocation> = initialChargerMap
    private var lastRefreshed: String? = null
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    init {
        lifecycleScope.launch {
            while (isActive) {
                delay(60_000)
                try {
                    val fresh = repo.getChargersByPks(stop.chargerPks.toSet())
                    if (fresh.isNotEmpty()) {
                        chargerMap = chargerMap + fresh.associateBy { it.pk }
                        lastRefreshed = LocalTime.now().format(timeFmt)
                        invalidate()
                    }
                } catch (_: Exception) {
                    // keep loop alive if a single refresh fails
                }
            }
        }
    }

    override fun onGetTemplate(): Template {

        val activeCharger = chargerMap[stop.activePk]
            ?: return ListTemplate.Builder()
                .setTitle(stop.displayName(0))
                .setHeaderAction(Action.BACK)
                .setLoading(true)
                .build()

        val editIcon = CarIcon.Builder(
            IconCompat.createWithResource(carContext, R.drawable.ic_car_edit)
        ).build()

        val listBuilder = ItemList.Builder()
        listBuilder.addItem(chargerRow(activeCharger, prefix = "★ ", stop = stop))
        stop.chargerPks.drop(1).forEach { pk ->
            chargerMap[pk]?.let { listBuilder.addItem(chargerRow(it, prefix = "", stop = stop)) }
        }

        val updatedSuffix = lastRefreshed?.let { " · Updated $it" } ?: ""
        return ListTemplate.Builder()
            .setTitle(stop.displayName(stop.chargerPks.indexOf(stop.activePk)) + updatedSuffix)
            .setHeaderAction(Action.BACK)
            .setSingleList(listBuilder.build())
            .addAction(
                Action.Builder()
                    .setIcon(editIcon)
                    .setBackgroundColor(CarColor.PRIMARY)
                    .setOnClickListener { screenManager.push(editMenuScreen()) }
                    .build()
            )
            .build()
    }

    private fun chargerRow(charger: ChargingLocation, prefix: String, stop: RouteStop): Row {
        val availability = charger.availabilitySummary()
        val connectors = charger.connectorPriceSummaries.joinToString(", ") { s ->
            val kw = s.kilowatts?.let { "${it.toInt()} kW" } ?: ""
            val price = when {
                s.isFree -> "Free"
                s.pricePerKwh != null -> "%.2f/kWh".format(s.pricePerKwh)
                else -> ""
            }
            listOf(s.type, kw, price).filter { it.isNotEmpty() }.joinToString(" ")
        }

        val costText = buildCostText(charger, stop)
        val line1 = listOf(availability, connectors).filter { it.isNotEmpty() }.joinToString(" · ")
        val line2 = costText

        val lat = charger.coordinates.latitude
        val lng = charger.coordinates.longitude

        return Row.Builder()
            .setTitle("$prefix${charger.name}")
            .apply {
                if (line1.isNotEmpty()) addText(line1)
                if (line2.isNotEmpty()) addText(line2)
            }
            .setOnClickListener {
                val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(${charger.name})")
                carContext.startCarApp(Intent(CarContext.ACTION_NAVIGATE, uri))
            }
            .build()
    }

    private fun buildCostText(charger: ChargingLocation, stop: RouteStop): String {
        val kw = charger.maxKilowatts ?: return ""
        val price = charger.pricePerKwh ?: return ""
        val connectionFee = charger.connectionFeeMajor ?: 0.0
        val chargingRate = charger.chargingTimeRateMajor ?: 0.0
        val parkingRate = charger.parkingTimeRateMajor ?: 0.0

        val optResult = KonaChargeCurve.simulate(
            stop.arrivalSocPercent.toFloat(),
            stop.departureSocPercent.toFloat(),
            kw,
            stayMinutes = null,
            profile = CarProfile.KONA_LR
        )
        val optCost = KonaChargeCurve.totalCost(optResult, price, connectionFee, chargingRate, parkingRate)

        val stayResult = KonaChargeCurve.simulate(
            stop.arrivalSocPercent.toFloat(),
            stop.departureSocPercent.toFloat(),
            kw,
            stayMinutes = stop.stayMinutes.toDouble(),
            profile = CarProfile.KONA_LR
        )
        val stayCost = KonaChargeCurve.totalCost(stayResult, price, connectionFee, chargingRate, parkingRate, stop.stayMinutes.toDouble())

        return "Optimal £${"%.2f".format(optCost)} · Stay £${"%.2f".format(stayCost)}"
    }

    private fun editMenuScreen(): Screen {
        return object : Screen(carContext) {
            override fun onGetTemplate(): Template {
                val socIdx = stop.arrivalSocPercent
                val depSoc = stop.departureSocPercent
                val stay = stop.stayMinutes

                val listBuilder = ItemList.Builder()
                    .addItem(
                        Row.Builder()
                            .setTitle("Arrival SoC (currently $socIdx%)")
                            .setOnClickListener {
                                screenManager.push(ValuePickerScreen(
                                    carContext,
                                    "Arrival SoC",
                                    listOf(5, 10, 15, 20, 25, 30, 40, 50).map { "$it%" to it },
                                    socIdx
                                ) { value -> saveStop(stop.copy(arrivalSocPercent = value)) })
                            }
                            .build()
                    )
                    .addItem(
                        Row.Builder()
                            .setTitle("Departure SoC (currently $depSoc%)")
                            .setOnClickListener {
                                screenManager.push(ValuePickerScreen(
                                    carContext,
                                    "Departure SoC",
                                    listOf(60, 70, 75, 80, 85, 90, 95, 100).map { "$it%" to it },
                                    depSoc
                                ) { value -> saveStop(stop.copy(departureSocPercent = value)) })
                            }
                            .build()
                    )
                    .addItem(
                        Row.Builder()
                            .setTitle("Stay time (currently $stay min)")
                            .setOnClickListener {
                                screenManager.push(ValuePickerScreen(
                                    carContext,
                                    "Stay time",
                                    listOf(15, 20, 30, 45, 60, 90, 120, 150, 180, 210, 240)
                                        .map { "${it} min" to it },
                                    stay
                                ) { value -> saveStop(stop.copy(stayMinutes = value)) })
                            }
                            .build()
                    )

                return ListTemplate.Builder()
                    .setTitle("Edit stop")
                    .setHeaderAction(Action.BACK)
                    .setSingleList(listBuilder.build())
                    .build()
            }
        }
    }

    private fun saveStop(updated: RouteStop) {
        stop = updated
        val raw = prefs.rawRoutePlan
        val type = object : TypeToken<List<RouteStop>>() {}.type
        val stops: List<RouteStop> = try {
            if (raw.isEmpty()) emptyList() else gson.fromJson(raw, type) ?: emptyList()
        } catch (e: Exception) { emptyList() }
        val newStops = stops.map { if (it.id == updated.id) updated else it }
        prefs.rawRoutePlan = gson.toJson(newStops)
        invalidate()
    }
}
