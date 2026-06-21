package com.richwatson.electrofind.model

data class RouteStop(
    val id: String,
    val chargerPks: List<Long>,
    val activeIndex: Int = 0,
    val arrivalSocPercent: Int = 20,
    val departureSocPercent: Int = 80,
    val stayMinutes: Int = 30,
    val customName: String? = null
) {
    val activePk: Long get() = chargerPks[activeIndex]
    fun displayName(position: Int) = customName?.takeIf { it.isNotBlank() } ?: "Stop ${position + 1}"
}
