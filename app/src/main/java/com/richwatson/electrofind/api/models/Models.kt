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

    // Some locations (e.g. certain supermarket chargers) come back with a blank name from the
    // API — the operator (e.g. "Lidl") is the best stand-in, falling back further to the
    // address if even that's blank, so there's always a usable title.
    val displayName: String get() = name.ifBlank {
        operator.name.ifBlank {
            address.lineSequence().firstOrNull { it.isNotBlank() } ?: address
        }
    }

    // Convenience: primary price per kWh, taken from an arbitrary connector (first in
    // iteration order) at the location — correct-by-construction for CustomChargers (always
    // exactly one connector), but a coarse fallback for multi-connector API locations where
    // tiers can be priced differently. Prefer connectorPriceSummaries for tier-correct data.
    val pricePerKwh: Double? get() {
        return evses.edges
            .flatMap { it.node.connectors.edges }
            .mapNotNull { edge -> edge.node.priceComponents.amountMajorFor("ConsumptionRate", treatZeroAsNull = false) }
            .firstOrNull()
    }

    // The charger's own currency, as reported by the API — chargers can be priced in a
    // different currency than the search location's detected default (e.g. near a border),
    // so this should be preferred over the app-wide default wherever a price is shown.
    val currencySymbol: String? get() {
        return evses.edges
            .flatMap { it.node.connectors.edges }
            .flatMap { it.node.priceComponents }
            .firstOrNull { it.currencyDetails != null }
            ?.currencyDetails?.symbol
    }

    // Coarse, charger-wide fallback — see pricePerKwh's note above. Prefer
    // connectorPriceSummaries for tier-correct data on multi-connector locations.
    val connectionFeeMajor: Double? get() = evses.edges
        .flatMap { it.node.connectors.edges }
        .mapNotNull { edge -> edge.node.priceComponents.amountMajorFor("ConnectionFee") }
        .firstOrNull()

    private fun timeRateForType(typeName: String): Double? = evses.edges
        .flatMap { it.node.connectors.edges }
        .mapNotNull { edge -> edge.node.priceComponents.amountMajorFor(typeName) }
        .firstOrNull()

    // Coarse, charger-wide fallbacks — see pricePerKwh's note above.
    val chargingTimeRateMajor: Double? get() = timeRateForType("TimeRate")
    val parkingTimeRateMajor: Double? get() = timeRateForType("ParkingTimeRate")

    val gracePeriodMinutes: Double get() = evses.edges
        .flatMap { it.node.connectors.edges }
        .flatMap { it.node.priceComponents }
        .firstOrNull { it.type == "GracePeriod" }
        ?.unitAmount?.toDouble() ?: 0.0

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

    val hasOutOfOrderEvse: Boolean get() = evses.edges.any {
        it.node.status in setOf("INOPERATIVE", "FAULTED", "UNAVAILABLE", "OUT_OF_ORDER", "OUTOFORDER")
    }

    val availabilityByKw: Map<Int, Triple<Int, Int, Int>> get() {
        val faultStatuses = setOf("INOPERATIVE", "FAULTED", "UNAVAILABLE", "OUT_OF_ORDER", "OUTOFORDER")
        return evses.edges
            .map { it.node }
            .groupBy { evse ->
                evse.connectors.edges.mapNotNull { it.node.kilowatts }.maxOrNull()?.toInt() ?: 0
            }
            .filterKeys { it > 0 }
            .mapValues { (_, evseList) ->
                val avail = evseList.count { it.status == "AVAILABLE" }
                val fault = evseList.count { it.status in faultStatuses }
                Triple(avail, evseList.size - avail - fault, fault)
            }
    }

    val connectorPriceSummaries: List<ConnectorPriceSummary> get() {
        return evses.edges
            .flatMap { it.node.connectors.edges.map { e -> e.node } }
            .groupBy { connector ->
                ConnectorPricingKey(
                    type = connector.standard?.humanName ?: "Unknown",
                    kilowatts = connector.kilowatts,
                    pricePerKwh = connector.priceComponents.amountMajorFor("ConsumptionRate", treatZeroAsNull = false),
                    connectionFeeMajor = connector.priceComponents.amountMajorFor("ConnectionFee"),
                    chargingTimeRateMajor = connector.priceComponents.amountMajorFor("TimeRate"),
                    parkingTimeRateMajor = connector.priceComponents.amountMajorFor("ParkingTimeRate")
                )
            }
            .map { (key, connectors) ->
                ConnectorPriceSummary(
                    type = key.type,
                    kilowatts = key.kilowatts,
                    pricePerKwh = key.pricePerKwh,
                    isFree = connectors.first().isChargingFree,
                    count = connectors.size,
                    connectionFeeMajor = key.connectionFeeMajor,
                    chargingTimeRateMajor = key.chargingTimeRateMajor,
                    parkingTimeRateMajor = key.parkingTimeRateMajor
                )
            }
            .sortedByDescending { it.kilowatts ?: 0.0 }
    }

    companion object {
        const val STALE_MS = 7 * 24 * 3600_000L
    }
}

fun timeAgo(cachedAt: Long): String? {
    if (cachedAt <= 0L) return null
    val ago = System.currentTimeMillis() - cachedAt
    return when {
        ago < 60_000L          -> "just now"
        ago < 3_600_000L       -> "${ago / 60_000} min ago"
        ago < 86_400_000L      -> "${ago / 3_600_000}h ago"
        else                   -> "${ago / 86_400_000}d ago"
    }
}

// Distinct from ChargingLocation.isStale (a long-horizon "is this cache entry
// ancient" check) — this flags data that hasn't been refreshed within the last
// two expected refresh cycles, a much tighter, refresh-cadence-relative signal.
fun isStaleForRefresh(cachedAt: Long, refreshPeriodMs: Long): Boolean =
    cachedAt > 0L && System.currentTimeMillis() - cachedAt > 2 * refreshPeriodMs

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

data class ConnectorPriceSummary(
    val type: String,
    val kilowatts: Double?,
    val pricePerKwh: Double?,
    val isFree: Boolean,
    val count: Int,
    val connectionFeeMajor: Double? = null,
    val chargingTimeRateMajor: Double? = null,
    val parkingTimeRateMajor: Double? = null
)

// Grouping key for connectorPriceSummaries: connectors merge into one summary row only when
// ALL of these match, so tiers that differ in any price dimension (e.g. two connectors with
// the same kW/kWh-price but different per-minute rates) are kept as separate rows.
private data class ConnectorPricingKey(
    val type: String,
    val kilowatts: Double?,
    val pricePerKwh: Double?,
    val connectionFeeMajor: Double?,
    val chargingTimeRateMajor: Double?,
    val parkingTimeRateMajor: Double?
)

// Extracts one price component's amount in major currency units (e.g. cents -> euros).
// For fee-style components (ConnectionFee/TimeRate/ParkingTimeRate) a 0 amount means "this
// fee doesn't apply here" so it's treated as absent (null). ConsumptionRate is different: a
// genuine €0.00/kWh price is common for locations billed purely by time, and 0.0 is a valid
// price there, not "no price" — callers for that type must pass treatZeroAsNull = false.
private fun List<PriceComponentDto>.amountMajorFor(typeName: String, treatZeroAsNull: Boolean = true): Double? {
    val comp = firstOrNull { it.type == typeName } ?: return null
    val amount = comp.unitAmount ?: 0
    if (treatZeroAsNull && amount == 0) return null
    return amount.toDouble() / (comp.currencyDetails?.minorUnitConversion ?: 100)
}

data class ConnectorStandard(
    val pk: Long,
    val humanName: String,
    val name: String
)
