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
import com.richwatson.electrofind.ElectroFindApp
import com.richwatson.electrofind.R
import com.richwatson.electrofind.api.models.ChargingLocation
import com.richwatson.electrofind.model.CarProfile
import com.richwatson.electrofind.model.RouteStop
import com.richwatson.electrofind.preferences.AppPreferences
import com.richwatson.electrofind.repository.ChargerRepository
import com.richwatson.electrofind.util.KonaChargeCurve
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

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
                    val fresh = stop.chargerPks.mapNotNull { pk ->
                        repo.fetchChargingLocation(pk.toString())
                    }
                    if (fresh.isNotEmpty()) {
                        chargerMap = chargerMap + fresh.associateBy { it.pk }
                        lastRefreshed = LocalTime.now().format(timeFmt)
                        invalidate()
                    }
                } catch (e: Exception) {
                    Log.e("StopDetailScreen", "refresh error", e)
                }
            }
        }
    }

    override fun onGetTemplate(): Template = try {
        onGetTemplateInternal()
    } catch (e: Exception) {
        Log.e("StopDetailScreen", "onGetTemplate crash", e)
        MessageTemplate.Builder("Error: ${e.message}")
            .setTitle(stop.displayName(0))
            .setHeaderAction(Action.BACK)
            .build()
    }

    private fun onGetTemplateInternal(): Template {

        val activeCharger = chargerMap[stop.activePk]
            ?: return ListTemplate.Builder()
                .setTitle(stop.displayName(0))
                .setHeaderAction(Action.BACK)
                .setLoading(true)
                .build()

        val editIcon = CarIcon.Builder(
            IconCompat.createWithResource(carContext, R.drawable.ic_car_edit)
        ).build()
        val navigateIcon = CarIcon.Builder(
            IconCompat.createWithResource(carContext, R.drawable.ic_car_navigate)
        ).build()

        val listBuilder = ItemList.Builder()
        stop.chargerPks.forEachIndexed { idx, pk ->
            val charger = chargerMap[pk] ?: return@forEachIndexed
            val prefix = if (idx == stop.activeIndex) "★ " else ""
            val onSelect = if (idx != stop.activeIndex) {
                { saveStop(stop.copy(activeIndex = idx)); Unit }
            } else null
            listBuilder.addItem(chargerRow(charger, prefix, stop, navigateIcon, onSelect))
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

    private fun chargerRow(charger: ChargingLocation, prefix: String, stop: RouteStop, navigateIcon: CarIcon, onSelect: (() -> Unit)? = null): Row {
        val availByKw = charger.availabilityByKw

        val line1 = charger.connectorPriceSummaries
            .mapNotNull { s -> s.kilowatts?.toInt()?.let { kw -> kw to s } }
            .distinctBy { it.first }
            .sortedByDescending { it.first }
            .take(3)
            .joinToString(" · ") { (kw, s) ->
                val (avail, inUse, _) = availByKw[kw] ?: Triple(0, 0, 0)
                val mins = KonaChargeCurve.simulate(
                    stop.arrivalSocPercent.toFloat(),
                    stop.departureSocPercent.toFloat(),
                    s.kilowatts!!, null,
                    profile = CarProfile.KONA_LR
                ).chargeMinutes
                val avParts = listOfNotNull(
                    if (avail > 0) "${avail}av" else null,
                    if (inUse > 0) "${inUse}use" else null
                ).joinToString(" ")
                "${kw}kW${if (avParts.isNotEmpty()) ": $avParts" else ""} ${formatMins(mins)}"
            }

        val connectorTypes = charger.connectorPriceSummaries
            .map { it.type }
            .distinct()
            .filter { it.isNotEmpty() }

        val costText = buildCostText(charger, stop)
        val line2Parts = mutableListOf<String>()
        if (costText.isNotEmpty()) line2Parts.add(costText)
        if (connectorTypes.isNotEmpty()) line2Parts.add(connectorTypes.joinToString(", "))
        val line2 = line2Parts.joinToString("  ·  ")

        val lat = charger.coordinates.latitude
        val lng = charger.coordinates.longitude
        val mapsUri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(${charger.name})")

        return Row.Builder()
            .setTitle("$prefix${charger.name}")
            .apply {
                if (line1.isNotEmpty()) addText(line1)
                if (line2.isNotEmpty()) addText(line2)
                if (onSelect != null) setOnClickListener(onSelect)
            }
            .addAction(
                Action.Builder()
                    .setIcon(navigateIcon)
                    .setOnClickListener {
                        carContext.startCarApp(Intent(CarContext.ACTION_NAVIGATE, mapsUri))
                    }
                    .build()
            )
            .build()
    }

    private fun formatMins(minutes: Double): String {
        val m = minutes.roundToInt()
        return if (m < 60) "~${m}min" else "~${m / 60}h ${"%02d".format(m % 60)}m"
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

        return buildString {
            append("Optimal £${"%.2f".format(optCost)} · Stay £${"%.2f".format(stayCost)}")
            if (connectionFee > 0) append(" · £${"%.2f".format(connectionFee)} conn fee")
            if (chargingRate > 0) append(" · £${"%.2f".format(chargingRate)}/min")
            if (parkingRate > 0) append(" · £${"%.2f".format(parkingRate)}/min parking")
        }
    }

    private fun paramPickerScreen(
        title: String,
        options: List<Pair<String, Int>>,
        current: Int,
        onConfirm: (Int) -> Unit
    ): Screen = object : Screen(carContext) {
        override fun onGetTemplate(): Template {
            val listBuilder = ItemList.Builder()
            options.forEach { (label, value) ->
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle(if (value == current) "★ $label" else label)
                        .setOnClickListener { onConfirm(value); screenManager.pop() }
                        .build()
                )
            }
            return ListTemplate.Builder()
                .setTitle(title)
                .setHeaderAction(Action.BACK)
                .setSingleList(listBuilder.build())
                .build()
        }
    }

    private fun stayTimePickerScreen(currentMinutes: Int, onConfirm: (Int) -> Unit): Screen =
        object : Screen(carContext) {
            private var selectedHours: Int? = null

            override fun onGetTemplate(): Template {
                val h = selectedHours
                if (h == null) {
                    val curHours = currentMinutes / 60
                    val listBuilder = ItemList.Builder()
                    (0..5).forEach { hours ->
                        listBuilder.addItem(Row.Builder()
                            .setTitle("${if (hours == curHours) "★ " else ""}$hours hour${if (hours != 1) "s" else ""}")
                            .setOnClickListener { selectedHours = hours; invalidate() }
                            .build())
                    }
                    return ListTemplate.Builder()
                        .setTitle("Stay time")
                        .setHeaderAction(Action.BACK)
                        .setSingleList(listBuilder.build())
                        .build()
                } else {
                    val curMins = currentMinutes % 60
                    val minuteOptions = if (h == 0) listOf(10, 20, 30, 40, 50) else listOf(0, 10, 20, 30, 40, 50)
                    val listBuilder = ItemList.Builder()
                    minuteOptions.forEach { mins ->
                        val total = h * 60 + mins
                        val isCur = h == currentMinutes / 60 && mins == curMins
                        listBuilder.addItem(Row.Builder()
                            .setTitle("${if (isCur) "★ " else ""}$h h ${"%02d".format(mins)} min")
                            .setOnClickListener { onConfirm(total); screenManager.pop() }
                            .build())
                    }
                    return ListTemplate.Builder()
                        .setTitle("Stay time — $h hour${if (h != 1) "s" else ""}")
                        .setHeaderAction(Action.BACK)
                        .setSingleList(listBuilder.build())
                        .addAction(Action.Builder()
                            .setTitle("← Hours")
                            .setOnClickListener { selectedHours = null; invalidate() }
                            .build())
                        .build()
                }
            }
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
                                screenManager.push(paramPickerScreen(
                                    "Arrival SoC",
                                    listOf(5, 10, 15, 20, 25, 30).map { "$it%" to it },
                                    socIdx
                                ) { value -> saveStop(stop.copy(arrivalSocPercent = value)) })
                            }
                            .build()
                    )
                    .addItem(
                        Row.Builder()
                            .setTitle("Departure SoC (currently $depSoc%)")
                            .setOnClickListener {
                                screenManager.push(paramPickerScreen(
                                    "Departure SoC",
                                    listOf(60, 70, 75, 80, 85, 90).map { "$it%" to it },
                                    depSoc
                                ) { value -> saveStop(stop.copy(departureSocPercent = value)) })
                            }
                            .build()
                    )
                    .addItem(
                        Row.Builder()
                            .setTitle("Stay time (currently ${stay / 60}h ${"%02d".format(stay % 60)}min)")
                            .setOnClickListener {
                                screenManager.push(stayTimePickerScreen(stay) { value ->
                                    saveStop(stop.copy(stayMinutes = value))
                                })
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
