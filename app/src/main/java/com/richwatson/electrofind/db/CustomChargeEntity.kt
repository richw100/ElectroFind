package com.richwatson.electrofind.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// A manually-entered charge the user adds to their trip log (no receipt PDF). UUID primary
// key so restore/merge can match rows across devices.
@Entity(
    tableName = "custom_charges",
    indices = [Index(value = ["dateEpochMillis"])]
)
data class CustomChargeEntity(
    @PrimaryKey val id: String,
    val dateEpochMillis: Long,
    val name: String,
    val kwh: Double,
    val cost: Double,
    val idleCost: Double,
    val currency: String,
    val excluded: Boolean = false
)
