package com.richwatson.electrofind.model

enum class DataSet { CUSTOM_CHARGERS, FAVOURITES, EXCLUDED, TRIPS }
enum class MergeMode { CLEAR_AND_REPLACE, ADD_NO_OVERWRITE, ADD_AND_OVERWRITE }

data class BackupFile(
    val version: Int = 1,
    val customChargers: List<CustomCharger>? = null,
    val favouritePks: List<Long>? = null,
    val excludedPks: List<Long>? = null,
    val trips: List<Trip>? = null
)
