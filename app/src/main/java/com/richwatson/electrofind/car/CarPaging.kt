package com.richwatson.electrofind.car

import androidx.car.app.CarContext
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import com.google.gson.Gson
import com.richwatson.electrofind.api.models.ChargingLocation
import com.richwatson.electrofind.api.models.isStaleForRefresh
import com.richwatson.electrofind.db.CachedChargerEntity

// Splits `rows` across pages sized to the host's current driving/parked row
// limit (ConstraintManager.CONTENT_LIMIT_TYPE_LIST) — while driving this is
// much smaller than while parked, and exceeding it makes the host refuse to
// render the list interactively.
internal fun CarContext.pagedListTemplate(
    title: String,
    rows: List<Row>,
    page: Int,
    headerAction: Action = Action.BACK,
    onPageChange: (Int) -> Unit
): ListTemplate {
    val maxItems = getCarService(ConstraintManager::class.java)
        .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)

    if (rows.size <= maxItems) {
        val listBuilder = ItemList.Builder()
        rows.forEach { listBuilder.addItem(it) }
        return ListTemplate.Builder()
            .setTitle(title)
            .setHeaderAction(headerAction)
            .setSingleList(listBuilder.build())
            .build()
    }

    val perPage = maxOf(1, maxItems - 2) // reserve Previous/Next rows
    val totalPages = (rows.size + perPage - 1) / perPage
    val p = page.coerceIn(0, totalPages - 1)
    val listBuilder = ItemList.Builder()
    if (p > 0) {
        listBuilder.addItem(Row.Builder().setTitle("← Previous")
            .setOnClickListener { onPageChange(p - 1) }.build())
    }
    rows.drop(p * perPage).take(perPage).forEach { listBuilder.addItem(it) }
    if (p < totalPages - 1) {
        listBuilder.addItem(Row.Builder().setTitle("Next →")
            .setOnClickListener { onPageChange(p + 1) }.build())
    }
    return ListTemplate.Builder()
        .setTitle("$title · ${p + 1}/$totalPages")
        .setHeaderAction(headerAction)
        .setSingleList(listBuilder.build())
        .build()
}

// Parses DB rows against a per-screen cache, returning the pk->charger map to merge into
// chargerMap (so cachedAt/staleness stays accurate for every row) plus whether an invalidate()
// is actually worth it. A no-op background refresh that just bumps cachedAt would otherwise
// silently burn a step of the Car App host's task template quota on every cycle for nothing —
// but we still want the "!" staleness prefix to update promptly once it's genuinely earned, so
// "worth it" is true when either the charger's JSON content changed, or the isStaleForRefresh()
// verdict for some pk flipped (i.e. the "!" prefix would actually appear/disappear on screen).
internal fun diffCachedChargers(
    entities: List<CachedChargerEntity>,
    parsedCache: MutableMap<Long, Pair<String, ChargingLocation>>,
    previousMap: Map<Long, ChargingLocation>,
    refreshPeriodMs: Long,
    gson: Gson
): Pair<Map<Long, ChargingLocation>, Boolean> {
    var changed = false
    val updated = entities.mapNotNull { entity ->
        val cached = parsedCache[entity.pk]
        val charger = if (cached != null && cached.first == entity.json) {
            cached.second.copy(cachedAt = entity.cachedAt)
        } else {
            try {
                val parsed = gson.fromJson(entity.json, ChargingLocation::class.java)
                parsedCache[entity.pk] = entity.json to parsed
                changed = true
                parsed.copy(cachedAt = entity.cachedAt)
            } catch (_: Exception) {
                return@mapNotNull null
            }
        }
        val wasStale = isStaleForRefresh(previousMap[entity.pk]?.cachedAt ?: 0L, refreshPeriodMs)
        val isStale = isStaleForRefresh(entity.cachedAt, refreshPeriodMs)
        if (wasStale != isStale) changed = true
        entity.pk to charger
    }.toMap()
    return updated to changed
}
