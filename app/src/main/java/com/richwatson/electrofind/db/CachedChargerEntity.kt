package com.richwatson.electrofind.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_chargers")
data class CachedChargerEntity(
    @PrimaryKey val pk: Long,
    val lat: Double,
    val lng: Double,
    val cachedAt: Long,
    val json: String
)
