package com.richwatson.electrofind.model

data class CarProfile(
    val id: String,
    val name: String,
    val batteryKwh: Double,
    val rawPoints: List<Pair<Float, Float>>  // (SoC% 0-100, kW)
) {
    // Pre-compute a 101-element lookup array (one per integer SoC) for fast simulation
    val socKwArray: FloatArray by lazy {
        FloatArray(101) { soc -> interpolate(soc.toFloat()) }
    }

    fun powerAtSoc(soc: Float): Float {
        val clamped = soc.coerceIn(0f, 100f)
        val idx = clamped.toInt().coerceIn(0, 99)
        val lo = socKwArray[idx]
        val hi = socKwArray[idx + 1]
        return lo + (hi - lo) * (clamped - idx)
    }

    private fun interpolate(soc: Float): Float {
        if (rawPoints.isEmpty()) return 0f
        if (soc <= rawPoints.first().first) return rawPoints.first().second
        if (soc >= rawPoints.last().first) return rawPoints.last().second
        val idx = rawPoints.indexOfFirst { it.first >= soc }.takeIf { it > 0 } ?: return rawPoints.last().second
        val lo = rawPoints[idx - 1]
        val hi = rawPoints[idx]
        val frac = (soc - lo.first) / (hi.first - lo.first)
        return lo.second + (hi.second - lo.second) * frac
    }

    companion object {
        const val KONA_LR_ID = "kona_lr_builtin"

        val KONA_LR = CarProfile(
            id = KONA_LR_ID,
            name = "Hyundai Kona EV Long Range",
            batteryKwh = 65.4,
            rawPoints = listOf(
                0f to 50.0f, 1f to 54.9f, 2f to 60.0f, 3f to 65.0f, 4f to 69.9f,
                5f to 75.0f, 6f to 80.0f, 7f to 82.7f, 8f to 85.5f, 9f to 88.2f,
                10f to 90.9f, 11f to 92.0f, 12f to 92.0f, 13f to 92.0f, 14f to 92.0f,
                15f to 92.0f, 16f to 92.0f, 17f to 92.0f, 18f to 92.0f, 19f to 93.0f,
                20f to 93.0f, 21f to 93.0f, 22f to 93.0f, 23f to 93.0f, 24f to 93.0f,
                25f to 93.0f, 26f to 93.0f, 27f to 93.9f, 28f to 93.9f, 29f to 93.9f,
                30f to 93.9f, 31f to 93.9f, 32f to 93.9f, 33f to 93.9f, 34f to 95.0f,
                35f to 95.0f, 36f to 95.0f, 37f to 95.0f, 38f to 95.0f, 39f to 95.0f,
                40f to 96.0f, 41f to 96.0f, 42f to 96.0f, 43f to 96.0f, 44f to 96.0f,
                45f to 96.0f, 46f to 96.0f, 47f to 96.9f, 48f to 96.9f, 49f to 96.9f,
                50f to 96.9f, 51f to 98.0f, 52f to 98.0f, 53f to 98.0f, 54f to 98.0f,
                55f to 99.0f, 56f to 99.0f, 57f to 99.0f, 58f to 99.9f, 59f to 99.9f,
                60f to 99.9f, 61f to 99.9f, 62f to 75.0f, 63f to 75.0f, 64f to 75.0f,
                65f to 75.0f, 66f to 75.9f, 67f to 75.9f, 68f to 45.0f, 69f to 45.0f,
                70f to 45.0f, 71f to 45.0f, 72f to 45.0f, 73f to 45.0f, 74f to 45.9f,
                75f to 42.9f, 76f to 36.9f, 77f to 36.9f, 78f to 38.0f, 79f to 39.0f,
                80f to 38.0f, 81f to 30.9f, 82f to 24.9f, 83f to 24.9f, 84f to 24.9f,
                85f to 24.9f, 86f to 24.9f, 87f to 24.9f, 88f to 24.9f, 89f to 24.9f,
                90f to 24.9f, 91f to 24.9f, 92f to 24.9f, 93f to 24.0f, 94f to 21.0f,
                95f to 18.9f, 96f to 17.0f, 97f to 15.9f, 98f to 12.9f, 99f to 11.0f,
                100f to 8.0f
            )
        )
    }
}
