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
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.richwatson.electrofind.ElectroFindApp
import com.richwatson.electrofind.R
import com.richwatson.electrofind.api.models.ChargingLocation
import com.richwatson.electrofind.model.CarProfile
import com.richwatson.electrofind.model.RouteStop
import com.richwatson.electrofind.model.Trip
import com.richwatson.electrofind.util.KonaChargeCurve
import com.richwatson.electrofind.preferences.AppPreferences
import com.richwatson.electrofind.api.models.isStaleForRefresh
import com.richwatson.electrofind.db.CachedChargerEntity
import com.richwatson.electrofind.work.RefreshChargersWorker
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.FlowPreview::class)
class StopDetailScreen(
    carContext: CarContext,
    private var stop: RouteStop,
    initialChargerMap: Map<Long, ChargingLocation>,
    private val stopIndex: Int
) : Screen(carContext) {

    private val app = carContext.applicationContext as ElectroFindApp
    private val prefs = AppPreferences(carContext.applicationContext)
    private val dao = app.database.chargerDao()
    private val gson = Gson()

    private var chargerMap: Map<Long, ChargingLocation> = initialChargerMap
    private var chargerPage: Int = 0
    // Room's table-level invalidation re-emits observeByPks() for ANY write to
    // cached_chargers, not just ones affecting our PKs — skip re-parsing JSON that hasn't changed.
    private val parsedCache = mutableMapOf<Long, Pair<String, ChargingLocation>>()

    private fun dlog(msg: String) = CarDebugLog.log(carContext, "[StopDetail] $msg")

    init {
        dlog("init pks=${stop.chargerPks} activePk=${stop.activePk} initialMapSize=${initialChargerMap.size}")

        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) = dlog("lifecycle onCreate")
            override fun onStart(owner: LifecycleOwner) = dlog("lifecycle onStart")
            override fun onResume(owner: LifecycleOwner) = dlog("lifecycle onResume")
            override fun onPause(owner: LifecycleOwner) = dlog("lifecycle onPause")
            override fun onStop(owner: LifecycleOwner) = dlog("lifecycle onStop")
            override fun onDestroy(owner: LifecycleOwner) = dlog("lifecycle onDestroy")
        })

        // Kick off an immediate refresh via WorkManager — runs outside Car App lifecycle scope
        // so it isn't blocked by driving UX restrictions
        RefreshChargersWorker.enqueue(carContext, stop.chargerPks.toSet())

        // Observe Room DB: when the Worker saves fresh data, chargerMap updates automatically.
        // debounce collapses bursts of near-simultaneous emissions (e.g. concurrent upserts)
        // into a single invalidate() — rapid consecutive template refreshes while driving
        // can trip the host's UX-restriction throttling.
        lifecycleScope.launch {
            dao.observeByPks(stop.chargerPks).debounce(300).collect { entities ->
                val (updated, changed) = diffCachedChargers(entities, parsedCache, chargerMap, prefs.refreshPeriodMs, gson)
                dlog("db observe emit: entities=${entities.size} updated=${updated.size} changed=$changed pksBefore=${chargerMap.keys}")
                if (updated.isNotEmpty()) {
                    chargerMap = chargerMap + updated
                    if (changed) {
                        dlog("db observe -> invalidate() chargerMapSize=${chargerMap.size}")
                        invalidate()
                    }
                }
            }
        }

        // Re-enqueue periodically so availability stays current while the screen is open.
        // Reads prefs.refreshPeriodMs fresh each iteration (not hoisted to a val) so a change
        // made via the edit menu's "Refresh period" picker takes effect on the next cycle.
        lifecycleScope.launch {
            while (isActive) {
                delay(prefs.refreshPeriodMs)
                dlog("periodic re-enqueue")
                RefreshChargersWorker.enqueue(carContext, stop.chargerPks.toSet())
            }
        }
    }

    override fun onGetTemplate(): Template = try {
        dlog("onGetTemplate() called")
        onGetTemplateInternal().also { dlog("onGetTemplate() returning template OK") }
    } catch (e: Throwable) {
        Log.e("StopDetailScreen", "onGetTemplate crash", e)
        dlog("onGetTemplate CRASH: ${e::class.simpleName}: ${e.message}\n${e.stackTraceToString()}")
        MessageTemplate.Builder("Error: ${e.message}")
            .setTitle(stop.displayName(stopIndex))
            .setHeaderAction(Action.BACK)
            .build()
    }

    private fun onGetTemplateInternal(): Template {

        val activeCharger = chargerMap[stop.activePk]
            ?: return ListTemplate.Builder()
                .setTitle(stop.displayName(stopIndex))
                .setHeaderAction(Action.BACK)
                .setLoading(true)
                .build().also { dlog("onGetTemplateInternal: activeCharger missing, returning loading template") }

        val navigateIcon = CarIcon.Builder(
            IconCompat.createWithResource(carContext, R.drawable.ic_car_navigate)
        ).build()
        val starIcon = CarIcon.Builder(
            IconCompat.createWithResource(carContext, R.drawable.ic_car_star)
        ).build()

        // Must stay identical across every background-triggered invalidate() — the Car App
        // host only treats a template rebuild as a free "refresh" (not counting against the
        // task flow's step quota) if the title is unchanged from the previous one.
        val baseTitle = stop.displayName(stopIndex)

        // Active charger always first so it's always on page 0
        val sortedPks = stop.chargerPks.sortedByDescending { if (it == stop.activePk) 1 else 0 }
        val chargerRows = sortedPks.mapNotNull { pk ->
            val charger = chargerMap[pk] ?: return@mapNotNull null
            val isActivePk = pk == stop.activePk
            val prefix = if (isActivePk) "★ " else ""
            val onSelect: () -> Unit = { screenManager.push(chargerDetailScreen(charger, stop)) }
            val onMakePrimary: () -> Unit = { saveStop(stop.copy(activeIndex = stop.chargerPks.indexOf(pk))) }
            chargerRow(charger, prefix, stop, navigateIcon, starIcon, isActivePk, onSelect, onMakePrimary)
        }

        // Settings rows are appended directly here (rather than behind a separate "Edit stop"
        // screen) so opening one only costs one push beyond StopDetailScreen, not two — see the
        // task-quota writeup in the plan this replaced editMenuScreen() from.
        val rows = chargerRows + settingsRows()
        dlog("onGetTemplateInternal: chargerRows=${chargerRows.size} totalRows=${rows.size} chargerPage=$chargerPage")
        return carContext.pagedListTemplate(baseTitle, rows, chargerPage) { chargerPage = it; invalidate() }
    }

    private fun settingsRows(): List<Row> {
        val socIdx = stop.arrivalSocPercent
        val depSoc = stop.departureSocPercent
        val stay = stop.stayMinutes
        return listOf(
            Row.Builder()
                .setTitle("Arrival SoC (currently $socIdx%)")
                .setOnClickListener {
                    screenManager.push(paramPickerScreen(
                        "Arrival SoC",
                        listOf(5, 10, 15, 20, 25, 30).map { "$it%" to it },
                        socIdx
                    ) { value -> saveStop(stop.copy(arrivalSocPercent = value)) })
                }
                .build(),
            Row.Builder()
                .setTitle("Departure SoC (currently $depSoc%)")
                .setOnClickListener {
                    screenManager.push(paramPickerScreen(
                        "Departure SoC",
                        listOf(60, 70, 75, 80, 85, 90).map { "$it%" to it },
                        depSoc
                    ) { value -> saveStop(stop.copy(departureSocPercent = value)) })
                }
                .build(),
            Row.Builder()
                .setTitle("Stay time (currently ${stay / 60}h ${"%02d".format(stay % 60)}min)")
                .setOnClickListener {
                    screenManager.push(stayTimePickerScreen(stay) { value ->
                        saveStop(stop.copy(stayMinutes = value))
                    })
                }
                .build(),
            Row.Builder()
                .setTitle("Arrival time (currently %02d:%02d)".format(stop.arrivalTimeMinutes / 60, stop.arrivalTimeMinutes % 60))
                .setOnClickListener {
                    screenManager.push(arrivalTimePickerScreen(stop.arrivalTimeMinutes) { value ->
                        saveStop(stop.copy(arrivalTimeMinutes = value))
                    })
                }
                .build(),
            Row.Builder()
                .setTitle("Refresh period (currently ${prefs.refreshPeriodMs / 60_000L} min)")
                .setOnClickListener {
                    screenManager.push(paramPickerScreen(
                        "Refresh period",
                        listOf(1, 2, 5, 10, 15, 30).map { "$it min" to it },
                        (prefs.refreshPeriodMs / 60_000L).toInt()
                    ) { value -> prefs.refreshPeriodMs = value * 60_000L; invalidate() })
                }
                .build(),
            // A plain toggle-on-tap row, not a picker sub-screen — a boolean doesn't need one,
            // and invalidate() on the same screen doesn't cost a step of the task flow's quota.
            Row.Builder()
                .setTitle("Show costs in GBP (currently ${if (prefs.convertToGbp) "on" else "off"})")
                .setOnClickListener { prefs.convertToGbp = !prefs.convertToGbp; invalidate() }
                .build()
        )
    }

    private fun chargerRow(
        charger: ChargingLocation,
        prefix: String,
        stop: RouteStop,
        navigateIcon: CarIcon,
        starIcon: CarIcon,
        isActive: Boolean,
        onSelect: () -> Unit,
        onMakePrimary: () -> Unit
    ): Row {
        val (line1, line2) = charger.chargerDetailLines(stop, prefs.convertToGbp)

        val lat = charger.coordinates.latitude
        val lng = charger.coordinates.longitude
        val mapsUri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(${charger.name})")
        val warn = if (isStaleForRefresh(charger.cachedAt, prefs.refreshPeriodMs)) "! " else ""

        return Row.Builder()
            .setTitle("$warn$prefix${charger.name}")
            .apply {
                if (line1.isNotEmpty()) addText(line1)
                if (line2.isNotEmpty()) addText(line2)
                setOnClickListener(onSelect)
            }
            .addAction(
                Action.Builder()
                    .setIcon(navigateIcon)
                    .setOnClickListener {
                        carContext.startCarApp(Intent(CarContext.ACTION_NAVIGATE, mapsUri))
                    }
                    .build()
            )
            .apply {
                // Omitted on the already-active row — navigate is still useful there,
                // but "make primary" is meaningless when it's already primary.
                if (!isActive) {
                    addAction(
                        Action.Builder()
                            .setIcon(starIcon)
                            .setOnClickListener(onMakePrimary)
                            .build()
                    )
                }
            }
            .build()
    }

    // Per-connector-tier breakdown for one charger — a charger can have multiple connector
    // speeds priced differently (e.g. 110kW vs 22kW), which the combined cost text on the
    // main list row can't distinguish since a Row is capped at 2 text lines.
    private fun chargerDetailScreen(charger: ChargingLocation, stop: RouteStop): Screen =
        object : Screen(carContext) {
            override fun onGetTemplate(): Template {
                val availByKw = charger.availabilityByKw
                val listBuilder = ItemList.Builder()
                charger.connectorPriceSummaries.forEach { s ->
                    val kwInt = s.kilowatts?.toInt()
                    val (avail, inUse, fault) = kwInt?.let { availByKw[it] } ?: Triple(0, 0, 0)
                    val total = avail + inUse + fault
                    val typeLabel = abbreviateConnectorType(s.type)

                    val title = buildString {
                        if (s.kilowatts != null) append("${s.kilowatts.toInt()} kW")
                        if (typeLabel.isNotEmpty()) { if (isNotEmpty()) append(" · "); append(typeLabel) }
                    }.ifEmpty { "Unknown connector" }

                    val line1 = if (total > 0) "$avail avail · $inUse in use · $fault fault (of $total)"
                                else "Availability unknown"

                    val mins = s.kilowatts?.let {
                        KonaChargeCurve.simulate(
                            stop.arrivalSocPercent.toFloat(), stop.departureSocPercent.toFloat(),
                            it, null, profile = CarProfile.KONA_LR
                        ).chargeMinutes
                    }
                    val costText = buildConnectorCostText(charger, stop, s, prefs.convertToGbp)
                    val line2 = listOfNotNull(
                        costText.takeIf { it.isNotEmpty() },
                        mins?.let { formatChargeMins(it) }
                    ).joinToString(" · ")

                    listBuilder.addItem(
                        Row.Builder()
                            .setTitle(title)
                            .apply {
                                if (line1.isNotEmpty()) addText(line1)
                                if (line2.isNotEmpty()) addText(line2)
                            }
                            .build()
                    )
                }

                val builder = ListTemplate.Builder()
                    .setTitle(charger.name)
                    .setHeaderAction(Action.BACK)
                    .setSingleList(listBuilder.build())

                if (charger.pk != stop.activePk) {
                    val starIcon = CarIcon.Builder(
                        IconCompat.createWithResource(carContext, R.drawable.ic_car_star)
                    ).build()
                    builder.addAction(
                        Action.Builder()
                            .setIcon(starIcon)
                            .setBackgroundColor(CarColor.PRIMARY)
                            .setOnClickListener {
                                saveStop(stop.copy(activeIndex = stop.chargerPks.indexOf(charger.pk)))
                                screenManager.pop()
                            }
                            .build()
                    )
                }

                return builder.build()
            }
        }

    private fun paramPickerScreen(
        title: String,
        options: List<Pair<String, Int>>,
        current: Int,
        onConfirm: (Int) -> Unit
    ): Screen = object : Screen(carContext) {
        var page = 0
        override fun onGetTemplate(): Template {
            val rows = options.map { (label, value) ->
                Row.Builder()
                    .setTitle(if (value == current) "★ $label" else label)
                    .setOnClickListener { onConfirm(value); screenManager.pop() }
                    .build()
            }
            return carContext.pagedListTemplate(title, rows, page) { page = it; invalidate() }
        }
    }

    // Flattened into one screen (rather than a pick-hour-then-pick-minute drill-down) so opening
    // it only costs one step of the Car App host's task template quota, not two.
    private fun arrivalTimePickerScreen(currentMinutes: Int, onConfirm: (Int) -> Unit): Screen =
        object : Screen(carContext) {
            var page = 0
            override fun onGetTemplate(): Template {
                val rows = (0..23).flatMap { hour -> listOf(0, 30).map { mins -> hour to mins } }.map { (hour, mins) ->
                    val total = hour * 60 + mins
                    Row.Builder()
                        .setTitle("${if (total == currentMinutes) "★ " else ""}%02d:%02d".format(hour, mins))
                        .setOnClickListener { onConfirm(total); screenManager.pop() }
                        .build()
                }
                return carContext.pagedListTemplate("Arrival time", rows, page) { page = it; invalidate() }
            }
        }

    // Flattened into one screen for the same reason as arrivalTimePickerScreen above.
    private fun stayTimePickerScreen(currentMinutes: Int, onConfirm: (Int) -> Unit): Screen =
        object : Screen(carContext) {
            var page = 0
            override fun onGetTemplate(): Template {
                val rows = (0..5).flatMap { hours ->
                    val minuteOptions = if (hours == 0) (1..11).map { it * 5 } else (0..11).map { it * 5 }
                    minuteOptions.map { mins -> hours to mins }
                }.map { (hours, mins) ->
                    val total = hours * 60 + mins
                    Row.Builder()
                        .setTitle("${if (total == currentMinutes) "★ " else ""}$hours h ${"%02d".format(mins)} min")
                        .setOnClickListener { onConfirm(total); screenManager.pop() }
                        .build()
                }
                return carContext.pagedListTemplate("Stay time", rows, page) { page = it; invalidate() }
            }
        }

    private fun saveStop(updated: RouteStop) {
        chargerPage = 0
        stop = updated

        // rawTrips is the primary multi-trip storage RouteListScreen.loadTrips() actually reads
        // (rawRoutePlan below is only its legacy single-trip fallback) — without updating it here,
        // an edit made in the car appears to silently revert when navigating back to the stop list.
        val tripsRaw = prefs.rawTrips
        val tripsType = object : TypeToken<List<Trip>>() {}.type
        val trips: List<Trip> = try {
            if (tripsRaw.isEmpty() || tripsRaw == "[]") emptyList() else gson.fromJson(tripsRaw, tripsType) ?: emptyList()
        } catch (e: Exception) { emptyList() }
        if (trips.isNotEmpty()) {
            val newTrips = trips.map { trip ->
                if (trip.stops.any { it.id == updated.id }) {
                    trip.copy(stops = trip.stops.map { if (it.id == updated.id) updated else it })
                } else trip
            }
            prefs.rawTrips = gson.toJson(newTrips)
        }

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
