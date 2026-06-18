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
}
