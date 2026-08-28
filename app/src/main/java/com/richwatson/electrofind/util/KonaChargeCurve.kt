package com.richwatson.electrofind.util

import com.richwatson.electrofind.api.models.RateTier
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

    // Three-phase AC tops out at 22 kW; 43 kW AC is effectively extinct and those units are
    // DC-capable anyway, so anything above this ceiling is treated as DC.
    private const val AC_KW_CEILING = 25.0

    // Whether a connector delivers AC (so the car's on-board charger limit applies) rather than
    // DC. Decided from the connector's human-readable type where possible, falling back to the
    // power rating when the type is unknown.
    fun isAcConnector(connectorType: String?, chargerKw: Double): Boolean {
        val t = connectorType?.uppercase()?.replace("_", " ")?.replace("-", " ")
            ?: return chargerKw in 0.0..AC_KW_CEILING
        return when {
            "CCS" in t || "COMBO" in t || "CHADEMO" in t || "CHA DE MO" in t -> false
            "TESLA" in t || "NACS" in t || "SUPERCHARGER" in t -> false
            "TYPE 2" in t || "TYPE 1" in t || "TYPE 3" in t ||
                "DOMESTIC" in t || "HOUSEHOLD" in t || "SCHUKO" in t ||
                "COMMANDO" in t || "3 PIN" in t || "BS1363" in t || "CEE" in t -> true
            else -> chargerKw in 0.0..AC_KW_CEILING
        }
    }

    data class SimResult(
        val endSocPercent: Float,
        val energyKwh: Double,
        val billedEnergyKwh: Double,
        val chargeMinutes: Double,
        val reachedTarget: Boolean
    )

    private data class SimKey(
        val startSoc: Float,
        val targetSoc: Float,
        val chargerMaxKw: Double,
        val stayMinutes: Double?,
        val profileId: String,
        val connectorType: String?
    )

    // simulate() is a pure function of its inputs but runs a ~600-iteration loop; it's
    // called repeatedly with identical inputs across car + phone UI screens (charge cost
    // estimates, map badges/popups) with no caller-side memoization, so cache it here once.
    // Bounded LRU: real cardinality is small (picker-constrained SoC/stay values, a handful
    // of connector speeds), so this comfortably covers a session with headroom.
    private const val SIM_CACHE_MAX = 2000
    private val simCache = object : LinkedHashMap<SimKey, SimResult>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<SimKey, SimResult>?) = size > SIM_CACHE_MAX
    }

    fun simulate(
        startSoc: Float,
        targetSoc: Float,
        chargerMaxKw: Double,
        stayMinutes: Double? = null,
        profile: CarProfile = CarProfile.KONA_LR,
        connectorType: String? = null
    ): SimResult {
        // Key on profile.id, not the CarProfile object itself — its equals() would walk
        // a 101-entry point list on every cache lookup, defeating the point of caching.
        val key = SimKey(startSoc, targetSoc, chargerMaxKw, stayMinutes, profile.id, connectorType)
        synchronized(simCache) { simCache[key] }?.let { return it }
        val result = computeSimulate(startSoc, targetSoc, chargerMaxKw, stayMinutes, profile, connectorType)
        synchronized(simCache) { simCache[key] = result }
        return result
    }

    private fun computeSimulate(
        startSoc: Float,
        targetSoc: Float,
        chargerMaxKw: Double,
        stayMinutes: Double?,
        profile: CarProfile,
        connectorType: String?
    ): SimResult {
        val isAc = isAcConnector(connectorType, chargerMaxKw)
        // On an AC connector the car draws no more than its on-board charger can accept,
        // regardless of what the connector is rated for (the DC curve doesn't apply).
        val acCap = profile.maxAcKw
        val effectiveMaxKw = if (isAc && acCap != null && acCap > 0.0)
            minOf(chargerMaxKw, acCap) else chargerMaxKw
        if (effectiveMaxKw <= 0.0 || startSoc >= targetSoc) {
            return SimResult(startSoc, 0.0, 0.0, 0.0, startSoc >= targetSoc)
        }
        // AC charging loses more to the on-board rectifier/thermal management than DC does.
        val efficiency = if (isAc) 0.88 else 0.95
        val step = 0.1f
        val energyPerStep = profile.batteryKwh * (step / 100.0)
        var soc = startSoc
        var totalEnergy = 0.0
        var totalMinutes = 0.0

        while (soc < targetSoc) {
            val effectiveKw = minOf(effectiveMaxKw, profile.powerAtSoc(soc).toDouble())
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
        stayMinutes: Double = result.chargeMinutes,
        gracePeriodMinutes: Double = 0.0,
        chargingRateTiers: List<RateTier> = emptyList(),
        sessionStartMinuteOfDay: Int? = null
    ): Double {
        val chargeMin = result.chargeMinutes
        val idleMin = (stayMinutes - chargeMin).coerceAtLeast(0.0)
        val billableChargeMin = (chargeMin - gracePeriodMinutes).coerceAtLeast(0.0)
        val remainingGrace = (gracePeriodMinutes - chargeMin).coerceAtLeast(0.0)
        val billableIdleMin = (idleMin - remainingGrace).coerceAtLeast(0.0)
        // Tiered rates are conditional on total time connected (charging or not — this is how
        // overstay/time-based tariffs like SAINT GILLES's "€0.10/min after 2h" actually work), so
        // they're billed across the whole stay, replacing both the flat charging and idle rates
        // rather than only the active-charging portion.
        val (chargingCost, idleCost) = if (chargingRateTiers.isEmpty()) {
            (chargingRatePerMin * billableChargeMin) to (parkingRatePerMin * billableIdleMin)
        } else {
            tieredChargingCost(stayMinutes, gracePeriodMinutes, chargingRateTiers, sessionStartMinuteOfDay ?: currentMinuteOfDay()) to 0.0
        }
        return pricePerKwh * result.billedEnergyKwh +
                connectionFee +
                chargingCost +
                idleCost
    }

    fun currentMinuteOfDay(): Int {
        val cal = java.util.Calendar.getInstance()
        return cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
    }

    // Minute-resolution billing pass so a conditional rate (duration threshold + time-of-day
    // window) is evaluated independently for every minute of the session — this is what lets a
    // session correctly span midnight, or cross in/out of the window more than once, without any
    // special-casing: a minute simply bills at whichever tier (if any) currently applies to it.
    private fun tieredChargingCost(totalMinutes: Double, gracePeriodMinutes: Double, tiers: List<RateTier>, sessionStartMinuteOfDay: Int): Double {
        if (totalMinutes <= 0.0) return 0.0
        val sortedTiers = tiers.sortedByDescending { it.afterMinutes }
        fun rateAt(elapsedMinutes: Double): Double {
            if (elapsedMinutes < gracePeriodMinutes) return 0.0
            val tier = sortedTiers.firstOrNull { elapsedMinutes >= it.afterMinutes } ?: return 0.0
            val clockMinute = ((sessionStartMinuteOfDay + elapsedMinutes.toInt()) % 1440 + 1440) % 1440
            if (tier.window != null && !tier.window.contains(clockMinute)) return 0.0
            return tier.ratePerMinMajor
        }
        val wholeMinutes = totalMinutes.toInt()
        val fraction = totalMinutes - wholeMinutes
        var cost = 0.0
        for (m in 0 until wholeMinutes) cost += rateAt(m.toDouble())
        if (fraction > 0.0) cost += rateAt(wholeMinutes.toDouble()) * fraction
        return cost
    }
}
