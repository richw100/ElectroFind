package com.richwatson.electrofind.api.models

data class LocationSuggestion(
    val displayName: String,
    val lat: Double,
    val lng: Double
) {
    val primaryName: String get() = displayName.substringBefore(",").trim()
    val secondaryName: String get() = displayName.substringAfter(",").substringBefore(",").trim()
}
