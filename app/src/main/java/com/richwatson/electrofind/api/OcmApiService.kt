package com.richwatson.electrofind.api

import com.richwatson.electrofind.api.models.OcmPoi
import retrofit2.http.GET
import retrofit2.http.Query

interface OcmApiService {
    @GET("v3/poi/")
    suspend fun getNearby(
        @Query("output") output: String = "json",
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("distance") distance: Int = 3,
        @Query("distanceunit") distanceUnit: String = "Miles",
        @Query("maxresults") maxResults: Int = 200,
        @Query("compact") compact: Boolean = true,
        @Query("verbose") verbose: Boolean = false,
        @Query("key") apiKey: String? = null
    ): List<OcmPoi>
}
