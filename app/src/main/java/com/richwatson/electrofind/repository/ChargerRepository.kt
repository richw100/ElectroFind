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
import com.richwatson.electrofind.api.models.LocationSuggestion
import com.richwatson.electrofind.api.models.TileLocation
import com.richwatson.electrofind.db.CachedChargerEntity
import com.richwatson.electrofind.db.ChargerDao
import com.richwatson.electrofind.util.TileCalculator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicInteger
import java.util.Locale
import java.util.concurrent.TimeUnit

class ChargerRepository(
    private val service: ElectroverseService,
    private val context: Context,
    private val dao: ChargerDao
) {
    private val gson = Gson()
    private val TAG = "ChargerRepository"

    private val defaultSocketGroups = listOf("CCS", "TYPE_2")

    private val nominatimClient = OkHttpClient.Builder()
        .callTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun fetchLocationSuggestions(query: String): List<LocationSuggestion> = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            // Photon uses Elasticsearch prefix matching, so partial words like "chelt" → Cheltenham
            val url = "https://photon.komoot.io/api/?q=$encoded&limit=5&lang=en"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "ElectroFind/1.0 (Android)")
                .build()
            val body = nominatimClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                response.body?.string() ?: return@withContext emptyList()
            }
            val response: PhotonResponse = gson.fromJson(body, PhotonResponse::class.java)
            response.features.mapNotNull { feature ->
                val props = feature.properties
                val name = props.name ?: return@mapNotNull null
                val coords = feature.geometry.coordinates
                if (coords.size < 2) return@mapNotNull null
                val lng = coords[0]
                val lat = coords[1]
                val secondary = listOfNotNull(
                    props.city?.takeIf { it != name },
                    props.county ?: props.state,
                    props.country
                ).joinToString(", ")
                val displayName = if (secondary.isEmpty()) name else "$name, $secondary"
                LocationSuggestion(displayName, lat, lng)
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchLocationSuggestions failed", e)
            emptyList()
        }
    }

    private data class PhotonResponse(val features: List<PhotonFeature>)
    private data class PhotonFeature(val geometry: PhotonGeometry, val properties: PhotonProperties)
    private data class PhotonGeometry(val coordinates: List<Double>)  // [lng, lat]
    private data class PhotonProperties(
        val name: String?,
        val city: String?,
        val county: String?,
        val state: String?,
        val country: String?
    )

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

    fun searchChargers(
        lat: Double,
        lng: Double,
        zoom: Int = 12,
        socketGroups: List<String> = defaultSocketGroups,
        radiusMiles: Int = 3,
        onStatus: (status: String, progress: Float) -> Unit = { _, _ -> }
    ): Flow<ChargingLocation> = channelFlow {
        val now = System.currentTimeMillis()
        val centre = TileCalculator.latLngToTile(lat, lng, zoom)
        // Each zoom-12 tile is ~6 km wide at UK latitudes; expand grid to cover the full radius
        val gridRadius = maxOf(1, kotlin.math.ceil(radiusMiles * 1609.344 / 6000.0).toInt())
        val tiles = TileCalculator.surroundingTiles(centre, gridRadius)

        onStatus("Searching ${tiles.size} tiles…", 0f)

        // PKs queued for API fetch. Workers drain this as tiles produce PKs.
        val pkChannel = Channel<Long>(Channel.UNLIMITED)
        val seenPks = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()

        val totalQueued = AtomicInteger(0)
        val done = AtomicInteger(0)
        val sentCount = AtomicInteger(0)
        val cachedCount = AtomicInteger(0)

        coroutineScope {
            // 10 workers start immediately — they pick up PKs as tiles feed them in
            val workers = List(10) {
                launch(Dispatchers.IO) {
                    for (pk in pkChannel) {
                        try {
                            val charger = withTimeout(8_000L) { fetchChargingLocation(pk.toString()) }
                            val n = done.incrementAndGet()
                            val t = totalQueued.get()
                            if (n == 1 || n % 5 == 0) {
                                onStatus("${sentCount.get()} found, checking $n/$t…", if (t > 0) n.toFloat() / t else 0f)
                            }
                            if (charger == null) continue
                            dao.upsert(CachedChargerEntity(
                                pk = charger.pk,
                                lat = charger.coordinates.latitude,
                                lng = charger.coordinates.longitude,
                                cachedAt = now,
                                json = gson.toJson(charger)
                            ))
                            val clat = charger.coordinates.latitude
                            val clng = charger.coordinates.longitude
                            if (clat == 0.0 && clng == 0.0) continue
                            if (distanceMiles(lat, lng, clat, clng) > radiusMiles) continue
                            sentCount.incrementAndGet()
                            send(charger.copy(cachedAt = now))
                        } catch (e: TimeoutCancellationException) {
                            done.incrementAndGet()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            done.incrementAndGet()
                            Log.e(TAG, "pk=$pk failed", e)
                        }
                    }
                }
            }

            // Tile producer — fetch tiles in parallel; for each tile emit cache hits immediately
            // and queue missing PKs so workers can start fetching without waiting for all tiles
            launch(Dispatchers.IO) {
                try {
                    coroutineScope {
                        tiles.map { tile ->
                            async(Dispatchers.IO) {
                                val pks = fetchTilePks(tile.zoom, tile.x, tile.y, socketGroups)
                                val newPks = pks.filter { seenPks.add(it) }
                                if (newPks.isEmpty()) return@async

                                val cachedEntities = dao.getByPks(newPks).associateBy { it.pk }

                                for (pk in newPks) {
                                    val entity = cachedEntities[pk]
                                    when {
                                        entity == null -> {
                                            // No cache — queue for API fetch
                                            totalQueued.incrementAndGet()
                                            pkChannel.send(pk)
                                        }
                                        now - entity.cachedAt > STALE_MS -> {
                                            // Stale — show old version now, refresh in background
                                            val charger = gson.fromJson(entity.json, ChargingLocation::class.java)
                                                .copy(cachedAt = entity.cachedAt)
                                            val clat = charger.coordinates.latitude
                                            val clng = charger.coordinates.longitude
                                            if ((clat != 0.0 || clng != 0.0) && distanceMiles(lat, lng, clat, clng) <= radiusMiles) {
                                                send(charger)
                                                cachedCount.incrementAndGet()
                                            }
                                            totalQueued.incrementAndGet()
                                            pkChannel.send(pk)
                                        }
                                        else -> {
                                            // Fresh cache — emit immediately, no API call needed
                                            val charger = gson.fromJson(entity.json, ChargingLocation::class.java)
                                                .copy(cachedAt = entity.cachedAt)
                                            val clat = charger.coordinates.latitude
                                            val clng = charger.coordinates.longitude
                                            if ((clat != 0.0 || clng != 0.0) && distanceMiles(lat, lng, clat, clng) <= radiusMiles) {
                                                send(charger)
                                                cachedCount.incrementAndGet()
                                            }
                                        }
                                    }
                                }
                            }
                        }.awaitAll()
                    }
                } finally {
                    pkChannel.close()
                }
            }

            workers.forEach { it.join() }
        }

        Log.d(TAG, "SUMMARY lat=$lat lng=$lng | sent=${sentCount.get()} cached=${cachedCount.get()} total_api=${done.get()}")
        onStatus("", 0f)
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
            if (len in 4..10 && i + len < bytes.size) {
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
        private const val STALE_MS = 7 * 24 * 3600_000L

        private val CHARGING_LOCATION_QUERY = """
            query chargingLocation(${'$'}pk: String!) {
              chargingLocation(pk: ${'$'}pk) {
                pk externalId name address city coordinates
                operator { name }
                evses {
                  edges {
                    node {
                      status
                      connectors {
                        edges {
                          node {
                            isChargingFree kilowatts
                            standard { humanName }
                            priceComponents {
                              __typename
                              ... on ConsumptionRate {
                                currencyDetails { symbol decimalDigits minorUnitConversion }
                                unitAmount perUnit
                              }
                              ... on ConnectionFee {
                                currencyDetails { symbol decimalDigits minorUnitConversion }
                                unitAmount
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
        """.trimIndent()
    }
}
