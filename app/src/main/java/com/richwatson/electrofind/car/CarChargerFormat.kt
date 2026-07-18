package com.richwatson.electrofind.car

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.richwatson.electrofind.api.models.ChargingLocation
import com.richwatson.electrofind.api.models.ConnectorPriceSummary
import com.richwatson.electrofind.api.models.TieredRateOverride
import com.richwatson.electrofind.api.models.connectorPriceSummariesWithOverride
import com.richwatson.electrofind.model.CarProfile
import com.richwatson.electrofind.model.RouteStop
import com.richwatson.electrofind.preferences.AppPreferences
import com.richwatson.electrofind.repository.CarProfileRepository
import com.richwatson.electrofind.util.CurrencyConversion
import com.richwatson.electrofind.util.KonaChargeCurve
import kotlin.math.roundToInt

// The same profile the phone app simulates with (SearchState.activeProfile) — the car
// screens must resolve it themselves since they don't go through ChargerViewModel.
internal fun activeCarProfile(context: Context): CarProfile {
    val all = listOf(CarProfile.KONA_LR) + CarProfileRepository(context).loadAll()
    return all.find { it.id == AppPreferences(context).activeProfileId } ?: CarProfile.KONA_LR
}

private val carFormatGson = Gson()

// Manual conditional-rate overrides (see ResultsScreen's "Add time-based rate" entry point) are
// stored as a single Gson blob in AppPreferences — the car screens don't go through
// ChargerViewModel, so they read the same prefs directly.
internal fun tieredRateOverrideFor(prefs: AppPreferences, pk: Long): TieredRateOverride? = try {
    val type = object : TypeToken<List<TieredRateOverride>>() {}.type
    val list: List<TieredRateOverride>? = carFormatGson.fromJson(prefs.rawTieredRateOverrides, type)
    list?.firstOrNull { it.chargingLocationPk == pk }
} catch (e: Exception) {
    null
}

// Shared row-content formatting so every Android Auto screen (trip stop list,
// charger alternatives list) describes a charger the same way.
internal fun ChargingLocation.chargerDetailLines(stop: RouteStop, convertToGbp: Boolean, profile: CarProfile, tieredRateOverride: TieredRateOverride? = null): Pair<String, String> {
    val availByKw = availabilityByKw
    val summaries = connectorPriceSummariesWithOverride(tieredRateOverride)

    val line1 = summaries
        .mapNotNull { s -> s.kilowatts?.toInt()?.let { kw -> kw to s } }
        .distinctBy { it.first }
        .sortedByDescending { it.first }
        .take(3)
        .joinToString(" | ") { (kw, s) ->
            val (avail, inUse, fault) = availByKw[kw] ?: Triple(0, 0, 0)
            val mins = KonaChargeCurve.simulate(
                stop.arrivalSocPercent.toFloat(),
                stop.departureSocPercent.toFloat(),
                s.kilowatts!!, null,
                profile = profile
            ).chargeMinutes
            val avParts = listOfNotNull(
                if (avail > 0) "${avail}a" else null,
                if (inUse > 0) "${inUse}u" else null,
                if (fault > 0) "${fault}x" else null
            ).joinToString("")
            "${kw}kW${if (avParts.isNotEmpty()) " $avParts" else ""} ${formatChargeMins(mins)}"
        }

    val connectorTypes = summaries
        .map { abbreviateConnectorType(it.type) }
        .distinct()
        .filter { it.isNotEmpty() }

    val costText = buildCostText(this, stop, convertToGbp, profile, tieredRateOverride)
    val line2Parts = mutableListOf<String>()
    if (costText.isNotEmpty()) line2Parts.add(costText)
    if (connectorTypes.isNotEmpty()) line2Parts.add(connectorTypes.joinToString("/"))
    val line2 = line2Parts.joinToString(" · ")

    return line1 to line2
}

internal fun formatChargeMins(minutes: Double): String {
    val m = minutes.roundToInt()
    return if (m < 60) "~${m}m" else "~${m / 60}h${"%02d".format(m % 60)}m"
}

internal fun abbreviateConnectorType(type: String): String = when {
    type.contains("COMBO", ignoreCase = true) || type.contains("CCS", ignoreCase = true) -> "CCS"
    type.contains("CHADEMO", ignoreCase = true) -> "CHAdeMO"
    type.contains("TYPE_2", ignoreCase = true) || type.contains("TYPE 2", ignoreCase = true) -> "T2"
    type.contains("TYPE_1", ignoreCase = true) || type.contains("TYPE 1", ignoreCase = true) -> "T1"
    type.contains("TESLA", ignoreCase = true) || type.contains("NACS", ignoreCase = true) -> "Tesla"
    type.isBlank() -> ""
    else -> type.take(6)
}

// Combined single-line summary: uses the fastest connector tier's own price/fees so the
// kW, €/kWh and per-minute rates are always from the same connector, not a mismatched mix.
internal fun buildCostText(charger: ChargingLocation, stop: RouteStop, convertToGbp: Boolean, profile: CarProfile, tieredRateOverride: TieredRateOverride? = null): String {
    val fastest = charger.connectorPriceSummariesWithOverride(tieredRateOverride).maxByOrNull { it.kilowatts ?: -1.0 } ?: return ""
    return buildConnectorCostText(charger, stop, fastest, convertToGbp, profile)
}

// Per-connector-tier variant: a charger can have multiple connector speeds priced
// differently (e.g. 110kW vs 22kW, or different per-minute rates) — this computes the
// estimate for one specific tier using that tier's own price/fees throughout.
internal fun buildConnectorCostText(charger: ChargingLocation, stop: RouteStop, summary: ConnectorPriceSummary, convertToGbp: Boolean, profile: CarProfile): String {
    if (summary.isFree) return "FREE"
    val kw = summary.kilowatts ?: return ""
    val price = summary.pricePerKwh ?: return ""
    return buildCostTextFor(
        charger, stop, kw, price,
        summary.connectionFeeMajor ?: 0.0,
        summary.chargingTimeRateMajor ?: 0.0,
        summary.parkingTimeRateMajor ?: 0.0,
        convertToGbp, profile,
        summary.chargingTimeRateTiers
    )
}

private fun buildCostTextFor(
    charger: ChargingLocation, stop: RouteStop, kw: Double, price: Double,
    connectionFee: Double, chargingRate: Double, parkingRate: Double,
    convertToGbp: Boolean, profile: CarProfile,
    chargingRateTiers: List<com.richwatson.electrofind.api.models.RateTier> = emptyList()
): String {
    val gracePeriod = charger.gracePeriodMinutes

    val optResult = KonaChargeCurve.simulate(
        stop.arrivalSocPercent.toFloat(),
        stop.departureSocPercent.toFloat(),
        kw,
        stayMinutes = null,
        profile = profile
    )
    val optCost = KonaChargeCurve.totalCost(optResult, price, connectionFee, chargingRate, parkingRate, gracePeriodMinutes = gracePeriod, chargingRateTiers = chargingRateTiers, sessionStartMinuteOfDay = stop.arrivalTimeMinutes)

    val stayResult = KonaChargeCurve.simulate(
        stop.arrivalSocPercent.toFloat(),
        stop.departureSocPercent.toFloat(),
        kw,
        stayMinutes = stop.stayMinutes.toDouble(),
        profile = profile
    )
    val stayCost = KonaChargeCurve.totalCost(stayResult, price, connectionFee, chargingRate, parkingRate, stop.stayMinutes.toDouble(), gracePeriod, chargingRateTiers, stop.arrivalTimeMinutes)
    val nativeCur = charger.currencySymbol ?: "€"
    // "kr" (NOK/SEK/DKK) has no unambiguous rate — see CurrencyConversion — so it's left native.
    val gbpRate = if (convertToGbp) CurrencyConversion.rateToGbp(nativeCur) else null
    val cur = if (gbpRate != null) "£" else nativeCur
    fun conv(amount: Double): Double = if (gbpRate != null) amount * gbpRate else amount

    val staySoc = stayResult.endSocPercent.toInt()

    return buildString {
        append("Opt $cur${"%.2f".format(conv(optCost))} Stay $cur${"%.2f".format(conv(stayCost))}→${staySoc}%")
        if (connectionFee > 0) append(" +$cur${"%.2f".format(conv(connectionFee))}")
        if (chargingRate > 0) append(" +$cur${"%.2f".format(conv(chargingRate))}/m")
        if (parkingRate > 0) append(" +$cur${"%.2f".format(conv(parkingRate))}/m park")
        // Abbreviated for Android Auto's tight per-line text limits — omits the time window.
        chargingRateTiers.forEach { tier ->
            append(" +$cur${"%.2f".format(conv(tier.ratePerMinMajor))}/m")
            if (tier.afterMinutes > 0) append(" after ${(tier.afterMinutes / 60.0).let { if (it == it.toInt().toDouble()) "${it.toInt()}h" else "%.1fh".format(it) }}")
        }
    }
}
