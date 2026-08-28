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

    var refreshPeriodMs: Long
        get() = prefs.getLong("refresh_period_ms", 60_000L)
        set(value) { prefs.edit().putLong("refresh_period_ms", value).apply() }

    var activeProfileId: String
        get() = prefs.getString("active_profile_id", CarProfile.KONA_LR_ID) ?: CarProfile.KONA_LR_ID
        set(value) { prefs.edit().putString("active_profile_id", value).apply() }

    // Uses commit() (synchronous) rather than apply(): these hold irreplaceable user data,
    // and apply()'s async write can be lost if the process is killed shortly after — e.g.
    // when the Play Store swaps the APK during an update — before it reaches disk.
    var favouritePks: Set<Long>
        get() = prefs.getString("favourite_pks", "")!!
            .split(",").mapNotNull { it.toLongOrNull() }.toSet()
        set(value) { prefs.edit().putString("favourite_pks", value.joinToString(",")).commit() }

    var excludedPks: Set<Long>
        get() = prefs.getString("excluded_pks", "")!!
            .split(",").mapNotNull { it.toLongOrNull() }.toSet()
        set(value) { prefs.edit().putString("excluded_pks", value.joinToString(",")).commit() }

    var rawSearchHistory: String
        get() = prefs.getString("search_history", "") ?: ""
        set(value) { prefs.edit().putString("search_history", value).apply() }

    var rawRoutePlan: String
        get() = prefs.getString("route_plan", "") ?: ""
        set(value) { prefs.edit().putString("route_plan", value).commit() }

    var rawCustomChargers: String
        get() = prefs.getString("custom_chargers", "[]") ?: "[]"
        set(value) { prefs.edit().putString("custom_chargers", value).commit() }

    var rawTrips: String
        get() = prefs.getString("trips", "[]") ?: "[]"
        set(value) { prefs.edit().putString("trips", value).commit() }

    var rawTieredRateOverrides: String
        get() = prefs.getString("tiered_rate_overrides", "[]") ?: "[]"
        set(value) { prefs.edit().putString("tiered_rate_overrides", value).commit() }

    // -1 sentinel for "unset" (SharedPreferences has no nullable Int): unset means "use the
    // live current time", distinct from a user-pinned time of 00:00.
    var sessionStartOverrideMinutes: Int?
        get() = prefs.getInt("session_start_override_minutes", -1).let { if (it < 0) null else it }
        set(value) { prefs.edit().putInt("session_start_override_minutes", value ?: -1).apply() }

    var convertToGbp: Boolean
        get() = prefs.getBoolean("convert_to_gbp", false)
        set(value) { prefs.edit().putBoolean("convert_to_gbp", value).apply() }

    // Manual export via Backup & restore's file picker
    var lastManualExportAt: Long
        get() = prefs.getLong("last_manual_export_at", 0L)
        set(value) { prefs.edit().putLong("last_manual_export_at", value).apply() }

    // Automatic background export to the public Downloads/ElectroFind folder — survives
    // an app uninstall/reinstall, unlike everything else in this file.
    var lastAutoBackupAt: Long
        get() = prefs.getLong("last_auto_backup_at", 0L)
        set(value) { prefs.edit().putLong("last_auto_backup_at", value).apply() }

    // ── Trip tab (charge-session receipt summary) ───────────────────────────
    // SAF tree URI for the folder holding Electroverse receipt PDFs (usually Downloads).
    // commit(): losing this after a process kill means the user has to re-grant folder access.
    var tripFolderUri: String?
        get() = prefs.getString("trip_folder_uri", null)
        set(value) { prefs.edit().putString("trip_folder_uri", value).commit() }

    var tripEurToGbpRate: Double
        get() = java.lang.Double.longBitsToDouble(prefs.getLong("trip_eur_to_gbp_rate", java.lang.Double.doubleToLongBits(0.86)))
        set(value) { prefs.edit().putLong("trip_eur_to_gbp_rate", java.lang.Double.doubleToLongBits(value)).apply() }

    var tripIceMpg: Double
        get() = java.lang.Double.longBitsToDouble(prefs.getLong("trip_ice_mpg", java.lang.Double.doubleToLongBits(40.0)))
        set(value) { prefs.edit().putLong("trip_ice_mpg", java.lang.Double.doubleToLongBits(value)).apply() }

    var tripPetrolPricePerLitre: Double
        get() = java.lang.Double.longBitsToDouble(prefs.getLong("trip_petrol_price_per_litre", java.lang.Double.doubleToLongBits(1.45)))
        set(value) { prefs.edit().putLong("trip_petrol_price_per_litre", java.lang.Double.doubleToLongBits(value)).apply() }

    var tripEvMilesPerKwh: Double
        get() = java.lang.Double.longBitsToDouble(prefs.getLong("trip_ev_miles_per_kwh", java.lang.Double.doubleToLongBits(3.5)))
        set(value) { prefs.edit().putLong("trip_ev_miles_per_kwh", java.lang.Double.doubleToLongBits(value)).apply() }

    // Actual distance driven over the selected range. 0.0 = not entered → miles are estimated
    // from energy × tripEvMilesPerKwh instead. When set, mi/kWh is derived from it.
    var tripMilesTravelled: Double
        get() = java.lang.Double.longBitsToDouble(prefs.getLong("trip_miles_travelled", java.lang.Double.doubleToLongBits(0.0)))
        set(value) { prefs.edit().putLong("trip_miles_travelled", java.lang.Double.doubleToLongBits(value)).apply() }

    // 0L = unset — the ViewModel then defaults the range to the last 30 days.
    var tripRangeStartEpochMillis: Long
        get() = prefs.getLong("trip_range_start", 0L)
        set(value) { prefs.edit().putLong("trip_range_start", value).apply() }

    var tripRangeEndEpochMillis: Long
        get() = prefs.getLong("trip_range_end", 0L)
        set(value) { prefs.edit().putLong("trip_range_end", value).apply() }
}
