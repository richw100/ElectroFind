package com.richwatson.electrofind.repository

import com.google.gson.Gson
import com.richwatson.electrofind.api.OcmApiService
import com.richwatson.electrofind.api.models.ChargingLocation
import com.richwatson.electrofind.api.models.Connector
import com.richwatson.electrofind.api.models.ConnectorConnection
import com.richwatson.electrofind.api.models.ConnectorEdge
import com.richwatson.electrofind.api.models.ConnectorStandard
import com.richwatson.electrofind.api.models.Coordinates
import com.richwatson.electrofind.api.models.DataSource
import com.richwatson.electrofind.api.models.Evse
import com.richwatson.electrofind.api.models.EvseConnection
import com.richwatson.electrofind.api.models.EvseEdge
import com.richwatson.electrofind.api.models.OcmPoi
import com.richwatson.electrofind.api.models.Operator
import com.richwatson.electrofind.db.CachedOcmEntity
import com.richwatson.electrofind.db.OcmDao
import kotlin.math.abs

class OcmApiKeyMissingException : Exception("No OCM API key set — add one in Settings")

class OcmRepository(private val service: OcmApiService, private val dao: OcmDao) {
    private val gson = Gson()

    suspend fun searchNearby(lat: Double, lng: Double, apiKey: String): List<ChargingLocation> {
        if (apiKey.isBlank()) throw OcmApiKeyMissingException()
        return try {
            val pois = service.getNearby(latitude = lat, longitude = lng, apiKey = apiKey)
            pois.forEach { poi ->
                dao.upsert(
                    CachedOcmEntity(
                        id = poi.id,
                        lat = poi.addressInfo?.latitude ?: lat,
                        lng = poi.addressInfo?.longitude ?: lng,
                        cachedAt = System.currentTimeMillis(),
                        json = gson.toJson(poi)
                    )
                )
            }
            pois.map { it.toChargingLocation() }
        } catch (e: OcmApiKeyMissingException) {
            throw e
        } catch (e: Exception) {
            // Network failure — fall back to cached entries within ~50km (0.45 degrees)
            val cached = dao.getAll()
                .filter { abs(it.lat - lat) < 0.45 && abs(it.lng - lng) < 0.45 }
                .mapNotNull {
                    runCatching { gson.fromJson(it.json, OcmPoi::class.java).toChargingLocation() }.getOrNull()
                }
            if (cached.isEmpty()) throw e
            cached
        }
    }
}

private fun OcmPoi.toChargingLocation(): ChargingLocation {
    val evseEdges = connections?.mapIndexed { index, conn ->
        val powerKW = conn.powerKW
        val status = if (conn.statusType?.isOperational != false) "AVAILABLE" else "UNAVAILABLE"
        val speed = when {
            powerKW != null && powerKW >= 100 -> "ULTRA"
            powerKW != null && powerKW >= 22 -> "FAST"
            else -> "SLOW"
        }
        val connectorTitle = conn.connectionType?.title ?: "Unknown"
        val connector = Connector(
            pk = index.toLong(),
            isChargingFree = false,
            priceComponents = emptyList(),
            kilowatts = powerKW,
            speed = speed,
            standard = ConnectorStandard(pk = 0L, humanName = connectorTitle, name = connectorTitle)
        )
        EvseEdge(
            node = Evse(
                pk = index.toLong(),
                physicalReference = null,
                status = status,
                connectors = ConnectorConnection(edges = listOf(ConnectorEdge(node = connector)))
            )
        )
    } ?: emptyList()

    return ChargingLocation(
        pk = -id,
        chargingLocationPk = null,
        externalId = uuid,
        name = addressInfo?.title ?: "Unknown",
        address = addressInfo?.addressLine1 ?: "",
        city = addressInfo?.town ?: "",
        postalCode = addressInfo?.postcode,
        country = null,
        coordinates = Coordinates(
            longitude = addressInfo?.longitude ?: 0.0,
            latitude = addressInfo?.latitude ?: 0.0
        ),
        isEjnLocation = false,
        operator = Operator(
            pk = 0L,
            name = operatorInfo?.title ?: "Unknown",
            logoDark = null,
            hasPartneredLocations = false
        ),
        openingHours = null,
        capabilities = null,
        evses = EvseConnection(totalCount = evseEdges.size, edges = evseEdges),
        cachedAt = System.currentTimeMillis(),
        source = DataSource.OCM,
        pricingText = usageCost
    )
}
