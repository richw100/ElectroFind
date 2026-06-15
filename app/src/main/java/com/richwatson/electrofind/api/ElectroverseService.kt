package com.richwatson.electrofind.api

import com.richwatson.electrofind.api.models.GraphQLRequest
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap

interface ElectroverseService {

    @POST("api/proxy/graphql")
    suspend fun graphQL(
        @Body request: GraphQLRequest
    ): Response<ResponseBody>

    // Tile URL without double slash — the website sent // which caused 308 redirects.
    // We construct the correct single-slash URL directly.
    @GET("api/proxy/rest/locations/tiles/elastic/{zoom}/{x}/{y}")
    suspend fun getLocationTile(
        @Path("zoom") zoom: Int,
        @Path("x") x: Int,
        @Path("y") y: Int,
        @Query("exclude_non_ejn_locations") excludeNonEjn: Boolean = true,
        @Query("socket_groups_mr[]") socketGroups: List<String>
    ): Response<ResponseBody>
}
