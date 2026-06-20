package com.richwatson.electrofind.api.models

import com.google.gson.annotations.SerializedName

// ---- GraphQL transport ----

data class GraphQLRequest(
    val operationName: String,
    val variables: Map<String, Any>,
    val query: String
)

data class GraphQLResponse<T>(
    val data: T?,
    val errors: List<GraphQLError>?
)

data class GraphQLError(val message: String)

// ---- Auth ----

data class ThirdPartyAuthWrapper(
    val thirdPartyAuthentication: ThirdPartyAuthResult?
)

data class ThirdPartyAuthResult(
    val login: LoginResult?,
    val signup: SignupResult?
)

data class LoginResult(
    val token: String?,
    val refreshToken: String?,
    val refreshExpiresIn: Long?
)

data class SignupResult(val token: String?)

// ---- Charging location (detail) ----

data class ChargingLocationWrapper(
    val chargingLocation: ChargingLocation?
)

data class ChargingLocation(
    val pk: Long,
    val chargingLocationPk: String?,
    val externalId: String?,
    val name: String,
    val address: String,
    val city: String,
    val postalCode: String?,
    val country: String?,
    val coordinates: Coordinates,
    val isEjnLocation: Boolean,
    val operator: Operator,
    val openingHours: OpeningHours?,
    val capabilities: List<Capability>?,
    val evses: EvseConnection,
    val cachedAt: Long = 0L,
    val pricingText: String? = null
) {
    // True when loaded from cache and data is older than one week
    val isStale: Boolean get() = cachedAt > 0 && System.currentTimeMillis() - cachedAt > STALE_MS

    // Convenience: primary price per kWh (ConsumptionRate of first connector, in major units)
    val pricePerKwh: Double? get() {
        return evses.edges
            .flatMap { it.node.connectors.edges }
            .mapNotNull { edge ->
                val rate = edge.node.priceComponents.firstOrNull { it.type == "ConsumptionRate" }
                rate?.let { it.unitAmount?.toDouble()?.div(it.currencyDetails?.minorUnitConversion ?: 100) }
            }
            .firstOrNull()
    }

    val connectionFeeMajor: Double? get() {
        return evses.edges
            .flatMap { it.node.connectors.edges }
            .mapNotNull { edge ->
                val fee = edge.node.priceComponents.firstOrNull { it.type == "ConnectionFee" }
                fee?.let {
                    val amount = it.unitAmount ?: 0
                    if (amount == 0) null
                    else amount.toDouble() / (it.currencyDetails?.minorUnitConversion ?: 100)
                }
            }
            .firstOrNull()
    }

    private fun timeRateForType(typeName: String): Double? = evses.edges
        .flatMap { it.node.connectors.edges }
        .mapNotNull { edge ->
            val rate = edge.node.priceComponents.firstOrNull { it.type == typeName }
            rate?.let {
                val amount = it.unitAmount ?: 0
                if (amount == 0) null
                else amount.toDouble() / (it.currencyDetails?.minorUnitConversion ?: 100)
            }
        }
        .firstOrNull()

    val chargingTimeRateMajor: Double? get() = timeRateForType("TimeRate")
    val parkingTimeRateMajor: Double? get() = timeRateForType("ParkingTimeRate")

    val maxKilowatts: Double? get() {
        return evses.edges
            .flatMap { it.node.connectors.edges }
            .mapNotNull { it.node.kilowatts }
            .maxOrNull()
    }

    val connectorTypes: List<String> get() {
        return evses.edges
            .flatMap { it.node.connectors.edges }
            .map { it.node.standard?.humanName ?: "Unknown" }
            .distinct()
    }

    val hasAvailableEvse: Boolean get() {
        return evses.edges.any { it.node.status == "AVAILABLE" }
    }

    companion object {
        const val STALE_MS = 7 * 24 * 3600_000L
    }
}

data class Coordinates(val longitude: Double, val latitude: Double)

data class Operator(
    val pk: Long,
    val name: String,
    val logoDark: String?,
    val hasPartneredLocations: Boolean
)

data class OpeningHours(val twentyFourSeven: Boolean)

data class Capability(
    @SerializedName("__typename") val type: String,
    val name: String?
)

data class EvseConnection(val totalCount: Int, val edges: List<EvseEdge>)
data class EvseEdge(val node: Evse)

data class Evse(
    val pk: Long,
    val physicalReference: String?,
    val status: String,
    val connectors: ConnectorConnection
)

data class ConnectorConnection(val edges: List<ConnectorEdge>)
data class ConnectorEdge(val node: Connector)

data class Connector(
    val pk: Long,
    val isChargingFree: Boolean,
    val priceComponents: List<PriceComponentDto>,
    val kilowatts: Double?,
    val speed: String,
    val standard: ConnectorStandard?
)

data class PriceComponentDto(
    @SerializedName("__typename") val type: String = "",
    val currencyDetails: CurrencyDetails? = null,
    val unitAmount: Int? = null,
    val perUnit: String? = null
)

data class CurrencyDetails(
    val symbol: String,
    val decimalDigits: Int,
    val minorUnitConversion: Int
)

data class ConnectorStandard(
    val pk: Long,
    val humanName: String,
    val name: String
)

// ---- Tile response (format to be confirmed via testing) ----
// The tile API returns charger location summaries. Format unknown from capture
// (all captured responses were 308 redirects). We handle both array and object responses.

data class TileLocation(
    val pk: Long?,
    @SerializedName("location_pk") val locationPk: Long?,
    @SerializedName("charging_location_pk") val chargingLocationPk: Long?,
    val id: Long?,
    val latitude: Double?,
    val longitude: Double?,
    val lat: Double?,
    val lng: Double?,
    val status: String?,
    val name: String?,
    @SerializedName("external_id") val externalId: String?
) {
    val resolvedPk: Long? get() = pk ?: locationPk ?: chargingLocationPk ?: id
    val resolvedLat: Double? get() = latitude ?: lat
    val resolvedLng: Double? get() = longitude ?: lng
}
