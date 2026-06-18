package com.richwatson.electrofind.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_ocm_chargers")
data class CachedOcmEntity(
    @PrimaryKey val id: Long,  // positive OCM ID
    val lat: Double,
    val lng: Double,
    val cachedAt: Long,
    val json: String
)
