package com.richwatson.electrofind.model

enum class DataSet { CUSTOM_CHARGERS, FAVOURITES, EXCLUDED, TRIPS, TRIP_LOG }
enum class MergeMode { CLEAR_AND_REPLACE, ADD_NO_OVERWRITE, ADD_AND_OVERWRITE }

data class BackupFile(
    val version: Int = 1,
    val customChargers: List<CustomCharger>? = null,
    val favouritePks: List<Long>? = null,
    val excludedPks: List<Long>? = null,
    val trips: List<Trip>? = null,
    val tripLog: TripLogBackup? = null
)

// ── Trip tab (charge-session summary) ──────────────────────────────────────
// Mirror the Room entities but drop the auto-generated session id (sessions are keyed on
// receiptNumber for restore). Kept in this package so the existing ProGuard -keep covers them.
data class ReceiptSessionBackup(
    val receiptNumber: String,
    val sourceFileName: String,
    val sourceFileId: String,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val evse: String? = null,
    val provider: String? = null,
    val address: String? = null,
    val currency: String,
    val kwh: Double,
    val energyCostGross: Double,
    val idleCostGross: Double,
    val totalGross: Double,
    val co2SavedKg: Double? = null,
    val excluded: Boolean = false,
    val importedAtEpochMillis: Long
)

data class CustomChargeBackup(
    val id: String,
    val dateEpochMillis: Long,
    val name: String,
    val kwh: Double,
    val cost: Double,
    val idleCost: Double,
    val currency: String,
    val excluded: Boolean = false
)

data class TripLogSettingsBackup(
    val eurToGbpRate: Double,
    val iceMpg: Double,
    val petrolPricePerLitre: Double,
    val evMilesPerKwh: Double,
    val milesTravelled: Double = 0.0,
    val folderUri: String? = null
)

data class TripLogBackup(
    val sessions: List<ReceiptSessionBackup> = emptyList(),
    val customCharges: List<CustomChargeBackup> = emptyList(),
    val settings: TripLogSettingsBackup? = null
)
