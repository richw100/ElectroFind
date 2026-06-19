package com.richwatson.electrofind.preferences

import android.content.Context
import com.richwatson.electrofind.api.models.DataSource

class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    var dataSource: DataSource
        get() = try {
            DataSource.valueOf(prefs.getString("data_source", DataSource.ELECTROVERSE.name)!!)
        } catch (e: Exception) {
            DataSource.ELECTROVERSE
        }
        set(value) {
            prefs.edit().putString("data_source", value.name).apply()
        }

    var ocmApiKey: String
        get() = prefs.getString("ocm_api_key", "") ?: ""
        set(value) { prefs.edit().putString("ocm_api_key", value.trim()).apply() }

    var searchRadiusMiles: Int
        get() = prefs.getInt("search_radius_miles", 3)
        set(value) { prefs.edit().putInt("search_radius_miles", value).apply() }
}
