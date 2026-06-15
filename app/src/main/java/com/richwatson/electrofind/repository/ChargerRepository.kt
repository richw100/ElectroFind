package com.richwatson.electrofind.repository

import android.content.Context
import android.location.Geocoder
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.richwatson.electrofind.api.ElectroverseService
import com.richwatson.electrofind.api.models.ChargingLocation
import com.richwatson.electrofind.api.models.ChargingLocationWrapper
import com.richwatson.electrofind.api.models.GraphQLRequest
import com.richwatson.electrofind.api.models.GraphQLResponse
import com.richwatson.electrofind.api.models.TileLocation
import com.richwatson.electrofind.util.TileCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.Locale

class ChargerRepository(
    private val service: ElectroverseService,
    private val context: Context
) {
    private val gson = Gson()
    private val TAG = "ChargerRepository"

    // Default connector types to search for — CCS and Type 2 cover most EVs
    private val defaultSocketGroups = listOf("CCS", "TYPE_2")

    suspend fun geocode(locationName: String): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        try {
            @Suppress("DEPRECATION")
            val results = Geocoder(context, Locale.getDefault()).getFromLocationName(locationName, 1)
            results?.firstOrNull()?.let { Pair(it.latitude, it.longitude) }
        } catch (e: Exception) {
            Log.e(TAG, "Geocoding failed for '$locationName'", e)
            null
        }
    }

    suspend fun searchChargers(
        lat: Double,
        lng: Double,
        zoom: Int = 12,
        gridRadius: Int = 1,
        socketGroups: List<String> = defaultSocketGroups
    ): Result<List<ChargingLocation>> = coroutineScope {
        try {
            val centre = TileCalculator.latLngToTile(lat, lng, zoom)
            val tiles = TileCalculator.surroundingTiles(centre, gridRadius)
            Log.d(TAG, "Fetching ${tiles.size} tiles at zoom $zoom around ($lat, $lng)")

            // Fetch all tiles in parallel
            val tileResults = tiles.map { tile ->
                async {
                    fetchTilePks(tile.zoom, tile.x, tile.y, socketGroups)
                }
            }.awaitAll()

            val pks = tileResults.flatten().distinct()
            Log.d(TAG, "Found ${pks.size} unique location PKs from tiles")

            if (pks.isEmpty()) {
                return@coroutineScope Result.success(emptyList())
            }

            // Fetch charger details in parallel (cap at 50 to avoid hammering the API)
            val limitedPks = pks.take(50)
            val chargerResults = limitedPks.map { pk ->
                async { fetchChargingLocation(pk.toString()) }
            }.awaitAll()

            val chargers = chargerResults.filterNotNull()
                .filter { c ->
                    val clat = c.coordinates.latitude
                    val clng = c.coordinates.longitude
                    clat != 0.0 && clng != 0.0 &&
                    distanceMiles(lat, lng, clat, clng) <= 3.0
                }
            Log.d(TAG, "Retrieved details for ${chargers.size} chargers within 3 miles")
            Result.success(chargers)
        } catch (e: Exception) {
            Log.e(TAG, "searchChargers failed", e)
            Result.failure(e)
        }
    }

    private suspend fun fetchTilePks(
        zoom: Int, x: Int, y: Int,
        socketGroups: List<String>
    ): List<Long> = withContext(Dispatchers.IO) {
        try {
            val response = service.getLocationTile(zoom, x, y, true, socketGroups)
            if (!response.isSuccessful) {
                Log.w(TAG, "Tile $zoom/$x/$y returned HTTP ${response.code()}")
                return@withContext emptyList()
            }
            val bytes = response.body()?.bytes() ?: return@withContext emptyList()
            val pks = extractPksFromBytes(bytes)
            Log.d(TAG, "Tile $zoom/$x/$y: ${bytes.size} bytes → ${pks.size} PKs: $pks")
            pks
        } catch (e: Exception) {
            Log.e(TAG, "Tile fetch failed for $zoom/$x/$y", e)
            emptyList()
        }
    }

    // Tile API always returns application/x-protobuf. Charger PKs are stored as
    // the Elasticsearch _id field — a plain UTF-8 string. In protobuf wire format,
    // a string is encoded as [length varint][bytes]. For 5–8 digit PKs the length
    // fits in one byte (< 128), so we look for a byte whose value equals the count
    // of ASCII digit bytes that immediately follow it. This is far more precise than
    // scanning for any digit run, avoiding false positives from other numeric fields.
    private fun extractPksFromBytes(bytes: ByteArray): List<Long> {
        val pks = mutableListOf<Long>()
        var i = 0
        while (i < bytes.size) {
            val len = bytes[i].toInt() and 0xFF
            if (len in 5..8 && i + len < bytes.size) {
                var allDigits = true
                for (j in 1..len) {
                    val c = bytes[i + j].toInt() and 0xFF
                    if (c !in 0x30..0x39) { allDigits = false; break }
                }
                if (allDigits) {
                    val numStr = (1..len).map { (bytes[i + it].toInt() and 0xFF).toChar() }.joinToString("")
                    numStr.toLongOrNull()?.let { pks.add(it) }
                    i += len + 1
                    continue
                }
            }
            i++
        }
        return pks.distinct()
    }

    // The tile response format isn't captured in the Burp file (all responses were 308 redirects
    // that weren't followed). We try multiple common JSON shapes here and log the raw body
    // on the first call so you can adapt this if the actual format differs.
    private fun parseTilePks(body: String): List<Long> {
        return try {
            val root = JsonParser.parseString(body)
            when {
                root.isJsonArray -> {
                    val type = object : TypeToken<List<TileLocation>>() {}.type
                    val locations: List<TileLocation> = gson.fromJson(root, type)
                    locations.mapNotNull { it.resolvedPk }
                }
                root.isJsonObject -> {
                    val obj = root.asJsonObject
                    // Elasticsearch shape: { hits: { hits: [ { _id: "123" }, ... ] } }
                    val hitsOuter = obj.getAsJsonObject("hits")
                    if (hitsOuter != null) {
                        val hitsInner = hitsOuter.getAsJsonArray("hits")
                        if (hitsInner != null) {
                            return hitsInner.mapNotNull { el ->
                                el.asJsonObject.get("_id")?.asString?.toLongOrNull()
                            }
                        }
                    }
                    // Fallback: common wrapper keys
                    val arrayEl = obj.get("results")
                        ?: obj.get("features")
                        ?: obj.get("locations")
                        ?: obj.get("data")
                    if (arrayEl != null && arrayEl.isJsonArray) {
                        val type = object : TypeToken<List<TileLocation>>() {}.type
                        val locations: List<TileLocation> = gson.fromJson(arrayEl, type)
                        locations.mapNotNull { it.resolvedPk }
                    } else {
                        Log.w(TAG, "Tile JSON object shape unrecognised; raw: ${body.take(300)}")
                        emptyList()
                    }
                }
                else -> {
                    Log.w(TAG, "Tile response is not JSON; raw: ${body.take(300)}")
                    emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse tile response: ${body.take(300)}", e)
            emptyList()
        }
    }

    suspend fun fetchChargingLocation(pk: String): ChargingLocation? = withContext(Dispatchers.IO) {
        try {
            val request = GraphQLRequest(
                operationName = "chargingLocation",
                variables = mapOf("pk" to pk),
                query = CHARGING_LOCATION_QUERY
            )
            val response = service.graphQL(request)
            if (!response.isSuccessful) {
                Log.w(TAG, "GraphQL chargingLocation $pk returned HTTP ${response.code()}")
                return@withContext null
            }
            val body = response.body()?.string() ?: return@withContext null
            val type = object : TypeToken<GraphQLResponse<ChargingLocationWrapper>>() {}.type
            val parsed: GraphQLResponse<ChargingLocationWrapper> = gson.fromJson(body, type)
            parsed.errors?.firstOrNull()?.let { Log.w(TAG, "GraphQL error for pk $pk: ${it.message}") }
            parsed.data?.chargingLocation
        } catch (e: Exception) {
            Log.e(TAG, "fetchChargingLocation($pk) failed", e)
            null
        }
    }

    private fun distanceMiles(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val R = 3958.8 // Earth radius in miles
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = Math.sin(dLat / 2).let { it * it } +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLng / 2).let { it * it }
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }

    companion object {
        private val CHARGING_LOCATION_QUERY = """
            query chargingLocation(${'$'}pk: String!) {
              chargingLocation(pk: ${'$'}pk) {
                pk chargingLocationPk externalId name address city postalCode country
                coordinates isEjnLocation
                operator { pk name logoDark hasPartneredLocations __typename }
                openingHours { twentyFourSeven __typename }
                capabilities { __typename ... on EJNApp { name } ... on Card { name } ... on Contactless { name } ... on Free { name } }
                evses {
                  totalCount
                  edges {
                    node {
                      pk physicalReference status
                      connectors {
                        edges {
                          node {
                            pk isChargingFree kilowatts speed
                            standard { pk humanName name __typename }
                            priceComponents {
                              __typename
                              ... on ConsumptionRate {
                                currencyDetails { symbol decimalDigits minorUnitConversion __typename }
                                unitAmount perUnit
                              }
                              ... on TimeRate {
                                currencyDetails { symbol decimalDigits minorUnitConversion __typename }
                                unitAmount perUnit
                              }
                              ... on ParkingTimeRate {
                                currencyDetails { symbol decimalDigits minorUnitConversion __typename }
                                unitAmount perUnit
                              }
                              ... on ConnectionFee {
                                currencyDetails { symbol decimalDigits minorUnitConversion __typename }
                                unitAmount
                              }
                            }
                            __typename
                          }
                          __typename
                        }
                        __typename
                      }
                      __typename
                    }
                    __typename
                  }
                  __typename
                }
                __typename
              }
            }
        """.trimIndent()
    }
}
