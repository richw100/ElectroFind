package com.richwatson.electrofind.repository

import android.content.Context
import com.richwatson.electrofind.model.CarProfile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class CarProfileRepository(context: Context) {

    private val dir = File(context.filesDir, "car_profiles").also { it.mkdirs() }

    fun loadAll(): List<CarProfile> {
        return dir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { readFile(it) }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    fun save(profile: CarProfile) {
        val json = JSONObject().apply {
            put("id", profile.id)
            put("name", profile.name)
            put("batteryKwh", profile.batteryKwh)
            put("points", JSONArray().apply {
                profile.rawPoints.forEach { (soc, kw) ->
                    put(JSONArray().put(soc.toDouble()).put(kw.toDouble()))
                }
            })
        }
        File(dir, "${profile.id}.json").writeText(json.toString())
    }

    fun delete(id: String) {
        File(dir, "$id.json").delete()
    }

    private fun readFile(file: File): CarProfile? = try {
        val json = JSONObject(file.readText())
        val pts = json.getJSONArray("points")
        val points = (0 until pts.length()).map { i ->
            val pair = pts.getJSONArray(i)
            pair.getDouble(0).toFloat() to pair.getDouble(1).toFloat()
        }
        CarProfile(
            id = json.getString("id"),
            name = json.getString("name"),
            batteryKwh = json.getDouble("batteryKwh"),
            rawPoints = points
        )
    } catch (_: Exception) { null }
}
