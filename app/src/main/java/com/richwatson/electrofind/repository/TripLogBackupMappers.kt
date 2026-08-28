package com.richwatson.electrofind.repository

import com.richwatson.electrofind.db.CustomChargeEntity
import com.richwatson.electrofind.db.ReceiptSessionEntity
import com.richwatson.electrofind.model.CustomChargeBackup
import com.richwatson.electrofind.model.ReceiptSessionBackup

fun ReceiptSessionEntity.toBackup() = ReceiptSessionBackup(
    receiptNumber = receiptNumber,
    sourceFileName = sourceFileName,
    sourceFileId = sourceFileId,
    startEpochMillis = startEpochMillis,
    endEpochMillis = endEpochMillis,
    evse = evse,
    provider = provider,
    address = address,
    currency = currency,
    kwh = kwh,
    energyCostGross = energyCostGross,
    idleCostGross = idleCostGross,
    totalGross = totalGross,
    co2SavedKg = co2SavedKg,
    excluded = excluded,
    importedAtEpochMillis = importedAtEpochMillis
)

fun ReceiptSessionBackup.toEntity() = ReceiptSessionEntity(
    id = 0,
    receiptNumber = receiptNumber,
    sourceFileName = sourceFileName,
    sourceFileId = sourceFileId,
    startEpochMillis = startEpochMillis,
    endEpochMillis = endEpochMillis,
    evse = evse,
    provider = provider,
    address = address,
    currency = currency,
    kwh = kwh,
    energyCostGross = energyCostGross,
    idleCostGross = idleCostGross,
    totalGross = totalGross,
    co2SavedKg = co2SavedKg,
    excluded = excluded,
    importedAtEpochMillis = importedAtEpochMillis
)

fun CustomChargeEntity.toBackup() = CustomChargeBackup(
    id = id,
    dateEpochMillis = dateEpochMillis,
    name = name,
    kwh = kwh,
    cost = cost,
    idleCost = idleCost,
    currency = currency,
    excluded = excluded
)

fun CustomChargeBackup.toEntity() = CustomChargeEntity(
    id = id,
    dateEpochMillis = dateEpochMillis,
    name = name,
    kwh = kwh,
    cost = cost,
    idleCost = idleCost,
    currency = currency,
    excluded = excluded
)
