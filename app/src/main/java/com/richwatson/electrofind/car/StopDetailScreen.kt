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
import androidx.car.app.constraints.ConstraintManager
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
                val fresh = entities.mapNotNull { entity ->
                    val cached = parsedCache[entity.pk]
                    if (cached != null && cached.first == entity.json) {
                        cached.second.copy(cachedAt = entity.cachedAt)
                    } else {
                        try {
                            val parsed = gson.fromJson(entity.json, ChargingLocation::class.java)
                            parsedCache[entity.pk] = entity.json to parsed
                            parsed.copy(cachedAt = entity.cachedAt)
                        } catch (_: Exception) { null }
                    }
                }
                dlog("db observe emit: entities=${entities.size} fresh=${fresh.size} pksBefore=${chargerMap.keys}")
                if (fresh.isNotEmpty()) {
                    chargerMap = chargerMap + fresh.associateBy { it.pk }
                    dlog("db observe -> invalidate() chargerMapSize=${chargerMap.size}")
                    invalidate()
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

        val editIcon = CarIcon.Builder(
            IconCompat.createWithResource(carContext, R.drawable.ic_car_edit)
        ).build()
        val navigateIcon = CarIcon.Builder(
            IconCompat.createWithResource(carContext, R.drawable.ic_car_navigate)
        ).build()
        val starIcon = CarIcon.Builder(
            IconCompat.createWithResource(carContext, R.drawable.ic_car_star)
        ).build()

        val editFab = Action.Builder()
            .setIcon(editIcon)
            .setBackgroundColor(CarColor.PRIMARY)
            .setOnClickListener { screenManager.push(editMenuScreen()) }
            .build()
        // Must stay identical across every background-triggered invalidate() — the Car App
        // host only treats a template rebuild as a free "refresh" (not counting against the
        // task flow's step quota) if the title is unchanged from the previous one.
        val baseTitle = stop.displayName(stopIndex)

        val maxItems = carContext.getCarService(ConstraintManager::class.java)
            .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)

        // Active charger always first so it's always on page 0
        val sortedPks = stop.chargerPks.sortedByDescending { if (it == stop.activePk) 1 else 0 }

        dlog("onGetTemplateInternal: maxItems=$maxItems sortedPks=${sortedPks.size} chargerMapKeys=${chargerMap.keys} chargerPage=$chargerPage branch=${if (sortedPks.size <= maxItems) "single" else "multi"}")

        if (sortedPks.size <= maxItems) {
            val listBuilder = ItemList.Builder()
            sortedPks.forEach { pk ->
                val charger = chargerMap[pk] ?: return@forEach
                val isActivePk = pk == stop.activePk
                val prefix = if (isActivePk) "★ " else ""
                val onSelect: () -> Unit = { screenManager.push(chargerDetailScreen(charger, stop)) }
                val onMakePrimary: () -> Unit = { saveStop(stop.copy(activeIndex = stop.chargerPks.indexOf(pk))) }
                listBuilder.addItem(chargerRow(charger, prefix, stop, navigateIcon, starIcon, isActivePk, onSelect, onMakePrimary))
            }
            val builtList = listBuilder.build()
            dlog("onGetTemplateInternal: single-page returning rowCount=${builtList.items.size} title=\"$baseTitle\"")
            return ListTemplate.Builder()
                .setTitle(baseTitle)
                .setHeaderAction(Action.BACK)
                .setSingleList(builtList)
                .addAction(editFab)
                .build()
        }

        // Multi-page: reserve 2 slots for ← Previous / Next → rows
        val chargersPerPage = maxOf(1, maxItems - 2)
        val totalPages = (sortedPks.size + chargersPerPage - 1) / chargersPerPage
        val page = chargerPage.coerceIn(0, totalPages - 1)
        if (chargerPage != page) chargerPage = page
        val pageChargers = sortedPks.drop(page * chargersPerPage).take(chargersPerPage)
        dlog("onGetTemplateInternal: multi-page chargersPerPage=$chargersPerPage totalPages=$totalPages page=$page pageChargers=${pageChargers.size}")

        val listBuilder = ItemList.Builder()
        if (page > 0) {
            listBuilder.addItem(Row.Builder()
                .setTitle("← Previous")
                .setOnClickListener { chargerPage--; invalidate() }
                .build())
        }
        pageChargers.forEach { pk ->
            val charger = chargerMap[pk] ?: return@forEach
            val isActivePk = pk == stop.activePk
            val prefix = if (isActivePk) "★ " else ""
            val onSelect: () -> Unit = { screenManager.push(chargerDetailScreen(charger, stop)) }
            val onMakePrimary: () -> Unit = { saveStop(stop.copy(activeIndex = stop.chargerPks.indexOf(pk))) }
            listBuilder.addItem(chargerRow(charger, prefix, stop, navigateIcon, starIcon, isActivePk, onSelect, onMakePrimary))
        }
        if (page < totalPages - 1) {
            listBuilder.addItem(Row.Builder()
                .setTitle("Next →")
                .setOnClickListener { chargerPage++; invalidate() }
                .build())
        }

        val builtList = listBuilder.build()
        dlog("onGetTemplateInternal: multi-page returning rowCount=${builtList.items.size} title=\"$baseTitle · ${page + 1}/$totalPages\"")
        return ListTemplate.Builder()
            .setTitle("$baseTitle · ${page + 1}/$totalPages")
            .setHeaderAction(Action.BACK)
            .setSingleList(builtList)
            .addAction(editFab)
            .build()
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
        val (line1, line2) = charger.chargerDetailLines(stop)

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
                    val costText = buildConnectorCostText(charger, stop, s)
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

    private fun arrivalTimePickerScreen(currentMinutes: Int, onConfirm: (Int) -> Unit): Screen =
        object : Screen(carContext) {
            override fun onGetTemplate(): Template {
                val curHour = currentMinutes / 60
                val listBuilder = ItemList.Builder()
                (0..23).forEach { hour ->
                    listBuilder.addItem(Row.Builder()
                        .setTitle("${if (hour == curHour) "★ " else ""}%02d:xx".format(hour))
                        .setOnClickListener {
                            screenManager.push(arrivalMinutePickerScreen(hour, currentMinutes, onConfirm))
                        }
                        .build())
                }
                return ListTemplate.Builder()
                    .setTitle("Arrival time — select hour")
                    .setHeaderAction(Action.BACK)
                    .setSingleList(listBuilder.build())
                    .build()
            }
        }

    private fun arrivalMinutePickerScreen(hour: Int, currentMinutes: Int, onConfirm: (Int) -> Unit): Screen =
        object : Screen(carContext) {
            override fun onGetTemplate(): Template {
                val listBuilder = ItemList.Builder()
                listOf(0, 30).forEach { mins ->
                    val total = hour * 60 + mins
                    listBuilder.addItem(Row.Builder()
                        .setTitle("${if (total == currentMinutes) "★ " else ""}%02d:%02d".format(hour, mins))
                        .setOnClickListener {
                            onConfirm(total)
                            screenManager.pop()
                            screenManager.pop()
                        }
                        .build())
                }
                return ListTemplate.Builder()
                    .setTitle("Arrival time — %02d:xx, select minutes".format(hour))
                    .setHeaderAction(Action.BACK)
                    .setSingleList(listBuilder.build())
                    .build()
            }
        }

    private fun stayTimePickerScreen(currentMinutes: Int, onConfirm: (Int) -> Unit): Screen =
        object : Screen(carContext) {
            override fun onGetTemplate(): Template {
                val curHours = currentMinutes / 60
                val listBuilder = ItemList.Builder()
                (0..5).forEach { hours ->
                    listBuilder.addItem(Row.Builder()
                        .setTitle("${if (hours == curHours) "★ " else ""}$hours hour${if (hours != 1) "s" else ""}")
                        .setOnClickListener {
                            screenManager.push(stayMinutePickerScreen(hours, currentMinutes, onConfirm))
                        }
                        .build())
                }
                return ListTemplate.Builder()
                    .setTitle("Stay time — select hours")
                    .setHeaderAction(Action.BACK)
                    .setSingleList(listBuilder.build())
                    .build()
            }
        }

    private fun stayMinutePickerScreen(hours: Int, currentMinutes: Int, onConfirm: (Int) -> Unit): Screen =
        object : Screen(carContext) {
            override fun onGetTemplate(): Template {
                val minuteOptions = if (hours == 0) (1..11).map { it * 5 }
                                    else (0..11).map { it * 5 }
                val listBuilder = ItemList.Builder()
                minuteOptions.forEach { mins ->
                    val total = hours * 60 + mins
                    listBuilder.addItem(Row.Builder()
                        .setTitle("${if (total == currentMinutes) "★ " else ""}$hours h ${"%02d".format(mins)} min")
                        .setOnClickListener {
                            onConfirm(total)
                            screenManager.pop() // minutes → hours
                            screenManager.pop() // hours → edit menu
                        }
                        .build())
                }
                return ListTemplate.Builder()
                    .setTitle("Stay time — $hours hour${if (hours != 1) "s" else ""}, select minutes")
                    .setHeaderAction(Action.BACK)
                    .setSingleList(listBuilder.build())
                    .build()
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
                    .addItem(
                        Row.Builder()
                            .setTitle("Arrival time (currently %02d:%02d)".format(stop.arrivalTimeMinutes / 60, stop.arrivalTimeMinutes % 60))
                            .setOnClickListener {
                                screenManager.push(arrivalTimePickerScreen(stop.arrivalTimeMinutes) { value ->
                                    saveStop(stop.copy(arrivalTimeMinutes = value))
                                })
                            }
                            .build()
                    )
                    .addItem(
                        Row.Builder()
                            .setTitle("Refresh period (currently ${prefs.refreshPeriodMs / 60_000L} min)")
                            .setOnClickListener {
                                screenManager.push(paramPickerScreen(
                                    "Refresh period",
                                    listOf(1, 2, 5, 10, 15, 30).map { "$it min" to it },
                                    (prefs.refreshPeriodMs / 60_000L).toInt()
                                ) { value -> prefs.refreshPeriodMs = value * 60_000L; invalidate() })
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
