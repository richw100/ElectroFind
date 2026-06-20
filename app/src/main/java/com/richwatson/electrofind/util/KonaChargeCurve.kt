package com.richwatson.electrofind.util

import com.richwatson.electrofind.model.CarProfile

object KonaChargeCurve {
    const val BATTERY_KWH = 65.4

    // (SoC%, kW) pairs extracted from kona-charge-curve.svg
    private val curve = floatArrayOf(
        0f, 50.0f,
        1f, 54.9f,
        2f, 60.0f,
        3f, 65.0f,
        4f, 69.9f,
        5f, 75.0f,
        6f, 80.0f,
        7f, 82.7f,
        8f, 85.5f,
        9f, 88.2f,
        10f, 90.9f,
        11f, 92.0f,
        12f, 92.0f,
        13f, 92.0f,
        14f, 92.0f,
        15f, 92.0f,
        16f, 92.0f,
        17f, 92.0f,
        18f, 92.0f,
        19f, 93.0f,
        20f, 93.0f,
        21f, 93.0f,
        22f, 93.0f,
        23f, 93.0f,
        24f, 93.0f,
        25f, 93.0f,
        26f, 93.0f,
        27f, 93.9f,
        28f, 93.9f,
        29f, 93.9f,
        30f, 93.9f,
        31f, 93.9f,
        32f, 93.9f,
        33f, 93.9f,
        34f, 95.0f,
        35f, 95.0f,
        36f, 95.0f,
        37f, 95.0f,
        38f, 95.0f,
        39f, 95.0f,
        40f, 96.0f,
        41f, 96.0f,
        42f, 96.0f,
        43f, 96.0f,
        44f, 96.0f,
        45f, 96.0f,
        46f, 96.0f,
        47f, 96.9f,
        48f, 96.9f,
        49f, 96.9f,
        50f, 96.9f,
        51f, 98.0f,
        52f, 98.0f,
        53f, 98.0f,
        54f, 98.0f,
        55f, 99.0f,
        56f, 99.0f,
        57f, 99.0f,
        58f, 99.9f,
        59f, 99.9f,
        60f, 99.9f,
        61f, 99.9f,
        62f, 75.0f,
        63f, 75.0f,
        64f, 75.0f,
        65f, 75.0f,
        66f, 75.9f,
        67f, 75.9f,
        68f, 45.0f,
        69f, 45.0f,
        70f, 45.0f,
        71f, 45.0f,
        72f, 45.0f,
        73f, 45.0f,
        74f, 45.9f,
        75f, 42.9f,
        76f, 36.9f,
        77f, 36.9f,
        78f, 38.0f,
        79f, 39.0f,
        80f, 38.0f,
        81f, 30.9f,
        82f, 24.9f,
        83f, 24.9f,
        84f, 24.9f,
        85f, 24.9f,
        86f, 24.9f,
        87f, 24.9f,
        88f, 24.9f,
        89f, 24.9f,
        90f, 24.9f,
        91f, 24.9f,
        92f, 24.9f,
        93f, 24.0f,
        94f, 21.0f,
        95f, 18.9f,
        96f, 17.0f,
        97f, 15.9f,
        98f, 12.9f,
        99f, 11.0f,
        100f, 8.0f
    )

    fun powerAtSoc(soc: Float): Float {
        val clamped = soc.coerceIn(0f, 100f)
        val idx = clamped.toInt().coerceIn(0, 99)
        val lo = curve[idx * 2 + 1]
        val hi = curve[(idx + 1) * 2 + 1]
        val frac = clamped - idx
        return lo + (hi - lo) * frac
    }

    data class SimResult(
        val endSocPercent: Float,
        val energyKwh: Double,
        val billedEnergyKwh: Double,
        val chargeMinutes: Double,
        val reachedTarget: Boolean
    )

    fun simulate(
        startSoc: Float,
        targetSoc: Float,
        chargerMaxKw: Double,
        stayMinutes: Double? = null,
        profile: CarProfile = CarProfile.KONA_LR
    ): SimResult {
        if (chargerMaxKw <= 0.0 || startSoc >= targetSoc) {
            return SimResult(startSoc, 0.0, 0.0, 0.0, startSoc >= targetSoc)
        }
        val efficiency = if (chargerMaxKw >= 22.0) 0.95 else 0.88
        val step = 0.1f
        val energyPerStep = profile.batteryKwh * (step / 100.0)
        var soc = startSoc
        var totalEnergy = 0.0
        var totalMinutes = 0.0

        while (soc < targetSoc) {
            val effectiveKw = minOf(chargerMaxKw, profile.powerAtSoc(soc).toDouble())
            if (effectiveKw <= 0.0) break
            val timeStep = (energyPerStep / (effectiveKw * efficiency)) * 60.0
            if (stayMinutes != null && totalMinutes + timeStep > stayMinutes) {
                val remaining = stayMinutes - totalMinutes
                val fraction = remaining / timeStep
                totalEnergy += energyPerStep * fraction
                totalMinutes = stayMinutes
                soc += step * fraction.toFloat()
                return SimResult(soc, totalEnergy, totalEnergy / efficiency, totalMinutes, false)
            }
            totalEnergy += energyPerStep
            totalMinutes += timeStep
            soc += step
        }
        return SimResult(targetSoc, totalEnergy, totalEnergy / efficiency, totalMinutes, true)
    }

    fun totalCost(
        result: SimResult,
        pricePerKwh: Double,
        connectionFee: Double = 0.0,
        chargingRatePerMin: Double = 0.0,
        parkingRatePerMin: Double = 0.0,
        stayMinutes: Double = result.chargeMinutes
    ): Double {
        val idleMinutes = (stayMinutes - result.chargeMinutes).coerceAtLeast(0.0)
        return pricePerKwh * result.billedEnergyKwh +
                connectionFee +
                chargingRatePerMin * result.chargeMinutes +
                parkingRatePerMin * idleMinutes
    }
}
