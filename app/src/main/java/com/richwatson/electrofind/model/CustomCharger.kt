package com.richwatson.electrofind.model

import com.richwatson.electrofind.api.models.ChargingLocation
import com.richwatson.electrofind.api.models.Connector
import com.richwatson.electrofind.api.models.ConnectorConnection
import com.richwatson.electrofind.api.models.ConnectorEdge
import com.richwatson.electrofind.api.models.ConnectorStandard
import com.richwatson.electrofind.api.models.Coordinates
import com.richwatson.electrofind.api.models.CurrencyDetails
import com.richwatson.electrofind.api.models.Evse
import com.richwatson.electrofind.api.models.EvseConnection
import com.richwatson.electrofind.api.models.EvseEdge
import com.richwatson.electrofind.api.models.Operator
import com.richwatson.electrofind.api.models.PriceComponentDto

data class CustomCharger(
    val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val address: String = "",
    val pricePerKwh: Double = 0.0,
    val connectionFeeGbp: Double = 0.0,
    val chargingRatePerMin: Double = 0.0,
    val idleRatePerMin: Double = 0.0,
    val maxKilowatts: Double = 50.0,
    val connectorType: String = "CCS",
)

private val GBP = CurrencyDetails(symbol = "£", decimalDigits = 2, minorUnitConversion = 100)

fun CustomCharger.toChargingLocation(): ChargingLocation {
    val priceComponents = buildList {
        add(PriceComponentDto("ConsumptionRate", GBP, (pricePerKwh * 100).toInt()))
        if (connectionFeeGbp > 0) add(PriceComponentDto("ConnectionFee", GBP, (connectionFeeGbp * 100).toInt()))
        if (chargingRatePerMin > 0) add(PriceComponentDto("TimeRate", GBP, (chargingRatePerMin * 100).toInt()))
        if (idleRatePerMin > 0) add(PriceComponentDto("ParkingTimeRate", GBP, (idleRatePerMin * 100).toInt()))
    }
    val speed = when {
        maxKilowatts < 7.4 -> "SLOW"
        maxKilowatts < 22.0 -> "FAST"
        else -> "RAPID"
    }
    val connector = Connector(
        pk = id,
        isChargingFree = pricePerKwh == 0.0 && connectionFeeGbp == 0.0,
        priceComponents = priceComponents,
        kilowatts = maxKilowatts,
        speed = speed,
        standard = ConnectorStandard(pk = -1, humanName = connectorType, name = connectorType)
    )
    val evse = Evse(
        pk = id,
        physicalReference = null,
        status = "AVAILABLE",
        connectors = ConnectorConnection(edges = listOf(ConnectorEdge(connector)))
    )
    return ChargingLocation(
        pk = id,
        chargingLocationPk = null,
        externalId = null,
        name = name,
        address = address,
        city = "",
        postalCode = null,
        country = null,
        coordinates = Coordinates(longitude = longitude, latitude = latitude),
        isEjnLocation = false,
        operator = Operator(pk = -1, name = "Custom", logoDark = null, hasPartneredLocations = false),
        openingHours = null,
        capabilities = null,
        evses = EvseConnection(totalCount = 1, edges = listOf(EvseEdge(evse))),
        cachedAt = 0L,
        pricingText = null
    )
}
