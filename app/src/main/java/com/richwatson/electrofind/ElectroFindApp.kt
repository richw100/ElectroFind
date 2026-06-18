package com.richwatson.electrofind

import android.app.Application
import com.richwatson.electrofind.api.ApiClient
import com.richwatson.electrofind.api.OcmApiClient
import com.richwatson.electrofind.auth.TokenManager
import com.richwatson.electrofind.db.AppDatabase
import com.richwatson.electrofind.preferences.AppPreferences
import com.richwatson.electrofind.repository.ChargerRepository
import com.richwatson.electrofind.repository.OcmRepository
import org.osmdroid.config.Configuration
import java.io.File

class ElectroFindApp : Application() {
    lateinit var tokenManager: TokenManager
    lateinit var repository: ChargerRepository
    lateinit var ocmRepository: OcmRepository
    lateinit var appPreferences: AppPreferences

    override fun onCreate() {
        super.onCreate()
        tokenManager = TokenManager(this)
        val service = ApiClient.buildService(tokenManager)
        val db = AppDatabase.getInstance(this)
        repository = ChargerRepository(service, this, db.chargerDao())
        appPreferences = AppPreferences(this)
        ocmRepository = OcmRepository(OcmApiClient.service, db.ocmDao())

        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = cacheDir
            osmdroidTileCache = File(cacheDir, "osm-tiles")
        }
    }
}
