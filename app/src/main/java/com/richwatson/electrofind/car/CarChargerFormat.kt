package com.richwatson.electrofind.car

import com.richwatson.electrofind.api.models.ChargingLocation
import com.richwatson.electrofind.model.CarProfile
import com.richwatson.electrofind.model.RouteStop
import com.richwatson.electrofind.util.KonaChargeCurve
import kotlin.math.roundToInt

// Shared row-content formatting so every Android Auto screen (trip stop list,
// charger alternatives list) describes a charger the same way.
internal fun ChargingLocation.chargerDetailLines(stop: RouteStop): Pair<String, String> {
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

    val costText = buildCostText(this, stop)
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

internal fun buildCostText(charger: ChargingLocation, stop: RouteStop): String {
    val kw = charger.maxKilowatts ?: return ""
    val price = charger.pricePerKwh ?: return ""
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

    return buildString {
        append("Opt £${"%.2f".format(optCost)} Stay £${"%.2f".format(stayCost)}")
        if (connectionFee > 0) append(" +£${"%.2f".format(connectionFee)}")
        if (chargingRate > 0) append(" +£${"%.2f".format(chargingRate)}/m")
        if (parkingRate > 0) append(" +£${"%.2f".format(parkingRate)}/m park")
    }
}
