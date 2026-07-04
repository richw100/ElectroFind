package com.richwatson.electrofind.car

import com.richwatson.electrofind.api.models.ChargingLocation
import com.richwatson.electrofind.api.models.ConnectorPriceSummary
import com.richwatson.electrofind.model.CarProfile
import com.richwatson.electrofind.model.RouteStop
import com.richwatson.electrofind.util.CurrencyConversion
import com.richwatson.electrofind.util.KonaChargeCurve
import kotlin.math.roundToInt

// Shared row-content formatting so every Android Auto screen (trip stop list,
// charger alternatives list) describes a charger the same way.
internal fun ChargingLocation.chargerDetailLines(stop: RouteStop, convertToGbp: Boolean = false): Pair<String, String> {
    val availByKw = availabilityByKw

    val line1 = connectorPriceSummaries
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
                profile = CarProfile.KONA_LR
            ).chargeMinutes
            val avParts = listOfNotNull(
                if (avail > 0) "${avail}a" else null,
                if (inUse > 0) "${inUse}u" else null,
                if (fault > 0) "${fault}x" else null
            ).joinToString("")
            "${kw}kW${if (avParts.isNotEmpty()) " $avParts" else ""} ${formatChargeMins(mins)}"
        }

    val connectorTypes = connectorPriceSummaries
        .map { abbreviateConnectorType(it.type) }
        .distinct()
        .filter { it.isNotEmpty() }

    val costText = buildCostText(this, stop, convertToGbp)
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

internal fun buildCostText(charger: ChargingLocation, stop: RouteStop, convertToGbp: Boolean = false): String {
    val kw = charger.maxKilowatts ?: return ""
    val price = charger.pricePerKwh ?: return ""
    return buildCostTextFor(charger, stop, kw, price, convertToGbp)
}

// Per-connector-tier variant: a charger can have multiple connector speeds priced
// differently (e.g. 110kW vs 22kW), so the combined buildCostText() above doesn't tell
// you which price it used — this computes the estimate for one specific tier instead.
internal fun buildConnectorCostText(charger: ChargingLocation, stop: RouteStop, summary: ConnectorPriceSummary, convertToGbp: Boolean = false): String {
    if (summary.isFree) return "FREE"
    val kw = summary.kilowatts ?: return ""
    val price = summary.pricePerKwh ?: return ""
    return buildCostTextFor(charger, stop, kw, price, convertToGbp)
}

private fun buildCostTextFor(charger: ChargingLocation, stop: RouteStop, kw: Double, price: Double, convertToGbp: Boolean = false): String {
    val connectionFee = charger.connectionFeeMajor ?: 0.0
    val chargingRate = charger.chargingTimeRateMajor ?: 0.0
    val parkingRate = charger.parkingTimeRateMajor ?: 0.0
    val gracePeriod = charger.gracePeriodMinutes

    val optResult = KonaChargeCurve.simulate(
        stop.arrivalSocPercent.toFloat(),
        stop.departureSocPercent.toFloat(),
        kw,
        stayMinutes = null,
        profile = CarProfile.KONA_LR
    )
    val optCost = KonaChargeCurve.totalCost(optResult, price, connectionFee, chargingRate, parkingRate, gracePeriodMinutes = gracePeriod)

    val stayResult = KonaChargeCurve.simulate(
        stop.arrivalSocPercent.toFloat(),
        stop.departureSocPercent.toFloat(),
        kw,
        stayMinutes = stop.stayMinutes.toDouble(),
        profile = CarProfile.KONA_LR
    )
    val stayCost = KonaChargeCurve.totalCost(stayResult, price, connectionFee, chargingRate, parkingRate, stop.stayMinutes.toDouble(), gracePeriod)
    val nativeCur = charger.currencySymbol ?: "€"
    // "kr" (NOK/SEK/DKK) has no unambiguous rate — see CurrencyConversion — so it's left native.
    val gbpRate = if (convertToGbp) CurrencyConversion.rateToGbp(nativeCur) else null
    val cur = if (gbpRate != null) "£" else nativeCur
    fun conv(amount: Double): Double = if (gbpRate != null) amount * gbpRate else amount

    return buildString {
        append("Opt $cur${"%.2f".format(conv(optCost))} Stay $cur${"%.2f".format(conv(stayCost))}")
        if (connectionFee > 0) append(" +$cur${"%.2f".format(conv(connectionFee))}")
        if (chargingRate > 0) append(" +$cur${"%.2f".format(conv(chargingRate))}/m")
        if (parkingRate > 0) append(" +$cur${"%.2f".format(conv(parkingRate))}/m park")
    }
}
