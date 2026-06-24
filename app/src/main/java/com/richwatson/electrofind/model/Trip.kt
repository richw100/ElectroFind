package com.richwatson.electrofind.model

data class Trip(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val stops: List<RouteStop> = emptyList()
)
