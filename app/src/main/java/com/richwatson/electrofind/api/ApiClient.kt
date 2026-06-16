package com.richwatson.electrofind.api

import com.richwatson.electrofind.auth.TokenManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.UUID
import java.util.concurrent.TimeUnit

object ApiClient {
    private const val BASE_URL = "https://electroverse.com/"
    // Stable device ID persisted via TokenManager (set on first build)
    private var deviceUid: String = UUID.randomUUID().toString()

    fun buildService(tokenManager: TokenManager): ElectroverseService {
        val authInterceptor = Interceptor { chain ->
            val original = chain.request()
            val builder = original.newBuilder()
                .header("Accept", "application/json")
                .header("Origin", "https://electroverse.com")
                .header("Referer", "https://electroverse.com/map")
                .header("X-Dui", deviceUid)
                .header("User-Agent",
                    "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36")

            // Build Cookie header from saved tokens
            val cookieParts = mutableListOf<String>()
            tokenManager.csrfToken?.let { cookieParts.add("csrftoken=$it") }
            tokenManager.jwtToken?.let { cookieParts.add("token=$it") }
            tokenManager.refreshToken?.let { cookieParts.add("refreshToken=$it") }
            if (cookieParts.isNotEmpty()) {
                builder.header("Cookie", cookieParts.joinToString("; "))
            }

            // GraphQL-specific headers
            if (original.url.encodedPath.contains("graphql")) {
                builder
                    .header("X-Method", "POST")
                    .header("X-Path", "/api/proxy/graphql")
                    .header("Content-Type", "application/json")
            }

            chain.proceed(builder.build())
        }

        val dispatcher = okhttp3.Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 20
        }

        val client = OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .addInterceptor(authInterceptor)
            .callTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ElectroverseService::class.java)
    }
}
