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
            Log.d(TAG, "Retrieved details for ${chargers.size} chargers")
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
    // the Elasticsearch _id field which is a plain UTF-8 string in the binary.
    // We extract all runs of 5–8 ASCII digits from the raw bytes to find them.
    private fun extractPksFromBytes(bytes: ByteArray): List<Long> {
        val pks = mutableListOf<Long>()
        var run = StringBuilder()
        for (b in bytes) {
            val c = b.toInt() and 0xFF
            if (c in 0x30..0x39) { // ASCII '0'..'9'
                run.append(c.toChar())
            } else {
                if (run.length in 5..8) {
                    run.toString().toLongOrNull()?.let { pks.add(it) }
                }
                run.clear()
            }
        }
        if (run.length in 5..8) run.toString().toLongOrNull()?.let { pks.add(it) }
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
