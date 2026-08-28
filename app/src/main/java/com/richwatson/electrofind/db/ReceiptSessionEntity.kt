package com.richwatson.electrofind.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// One parsed Electroverse "Charge Session Receipt" PDF. Costs are stored in the receipt's
// native currency ("GBP" or "EUR"); GBP conversion happens in the ViewModel using the
// user-set EUR->GBP rate. Both receiptNumber and sourceFileName are unique — the first
// dedupes re-imports of the same receipt, the second lets the scanner skip files it has
// already seen without opening them.
@Entity(
    tableName = "receipt_sessions",
    indices = [
        Index(value = ["receiptNumber"], unique = true),
        Index(value = ["sourceFileName"], unique = true),
        Index(value = ["startEpochMillis"])
    ]
)
data class ReceiptSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val receiptNumber: String,
    val sourceFileName: String,
    val sourceFileId: String,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val evse: String?,
    val provider: String?,
    val address: String?,
    val currency: String,
    val kwh: Double,
    val energyCostGross: Double,
    val idleCostGross: Double,
    val totalGross: Double,
    val co2SavedKg: Double?,
    val excluded: Boolean = false,
    val importedAtEpochMillis: Long
)
