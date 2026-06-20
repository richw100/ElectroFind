package com.richwatson.electrofind.preferences

import android.content.Context
import com.richwatson.electrofind.model.CarProfile
import com.richwatson.electrofind.viewmodel.ThemeMode

class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

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

    var startSocPercent: Int
        get() = prefs.getInt("start_soc_percent", 20)
        set(value) { prefs.edit().putInt("start_soc_percent", value).apply() }

    var targetSocPercent: Int
        get() = prefs.getInt("target_soc_percent", 80)
        set(value) { prefs.edit().putInt("target_soc_percent", value).apply() }

    var stayMinutes: Int
        get() = prefs.getInt("stay_minutes", 30)
        set(value) { prefs.edit().putInt("stay_minutes", value).apply() }

    var activeProfileId: String
        get() = prefs.getString("active_profile_id", CarProfile.KONA_LR_ID) ?: CarProfile.KONA_LR_ID
        set(value) { prefs.edit().putString("active_profile_id", value).apply() }

    var rawSearchHistory: String
        get() = prefs.getString("search_history", "") ?: ""
        set(value) { prefs.edit().putString("search_history", value).apply() }
}
