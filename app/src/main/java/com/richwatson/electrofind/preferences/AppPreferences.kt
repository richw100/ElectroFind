package com.richwatson.electrofind.preferences

import android.content.Context
import com.richwatson.electrofind.api.models.DataSource
import com.richwatson.electrofind.viewmodel.ThemeMode

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

    var themeMode: ThemeMode
        get() = try {
            ThemeMode.valueOf(prefs.getString("theme_mode", ThemeMode.SYSTEM.name)!!)
        } catch (e: Exception) {
            ThemeMode.SYSTEM
        }
        set(value) { prefs.edit().putString("theme_mode", value.name).apply() }

    var mapZoom: Double
        get() = java.lang.Double.longBitsToDouble(prefs.getLong("map_zoom", java.lang.Double.doubleToLongBits(14.0)))
        set(value) { prefs.edit().putLong("map_zoom", java.lang.Double.doubleToLongBits(value)).apply() }

    var mapCenterLat: Double
        get() = java.lang.Double.longBitsToDouble(prefs.getLong("map_center_lat", java.lang.Double.doubleToLongBits(0.0)))
        set(value) { prefs.edit().putLong("map_center_lat", java.lang.Double.doubleToLongBits(value)).apply() }

    var mapCenterLng: Double
        get() = java.lang.Double.longBitsToDouble(prefs.getLong("map_center_lng", java.lang.Double.doubleToLongBits(0.0)))
        set(value) { prefs.edit().putLong("map_center_lng", java.lang.Double.doubleToLongBits(value)).apply() }
}
