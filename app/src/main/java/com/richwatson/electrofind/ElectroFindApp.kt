package com.richwatson.electrofind

import android.app.Application
import com.richwatson.electrofind.api.ApiClient
import com.richwatson.electrofind.auth.TokenManager
import com.richwatson.electrofind.repository.ChargerRepository
import org.osmdroid.config.Configuration
import java.io.File

class ElectroFindApp : Application() {
    lateinit var tokenManager: TokenManager
    lateinit var repository: ChargerRepository

    override fun onCreate() {
        super.onCreate()
        tokenManager = TokenManager(this)
        val service = ApiClient.buildService(tokenManager)
        repository = ChargerRepository(service, this)

        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = cacheDir
            osmdroidTileCache = File(cacheDir, "osm-tiles")
        }
    }
}
