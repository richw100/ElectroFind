package com.richwatson.electrofind.util

import com.richwatson.electrofind.db.ReceiptSessionEntity
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.InputStream
import java.time.LocalDateTime
import java.time.ZoneId

// Parses an Electroverse "Charge Session Receipt" PDF into a ReceiptSessionEntity.
//
// The regexes run against the whole extracted-text blob (not line by line) with `\s+`
// between fields, so they match whether the PDF text extractor emits each table row on one
// line ("19:48 07/08/26 23:05 07/08/26 Consumption [kWh] £0.50 37.07 …") or one value per
// line. Only `parsePdf` touches PDFBox; `parse` is pure and unit-tested.
object ReceiptPdfParser {

    val FILENAME_REGEX = Regex("""^Electroverse-(\d{4}-\d{2}-\d{2})-([^.]+)\.pdf$""")

    sealed class Result {
        data class Success(val entity: ReceiptSessionEntity) : Result()
        data class Failure(val fileName: String, val reason: String) : Result()
    }

    private val MONTHS = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "sept" to 9, "oct" to 10, "nov" to 11, "dec" to 12
    )

    // "Receipt Date:" can't match — the digits must come straight after "Receipt".
    private val RECEIPT_PROVIDER = Regex("""Receipt\s+(\d+)\s+Provider\s+([^\n\r]+)""")
    private val EVSE = Regex("""\bEVSE\s+(\S+)""")
    private val CO2 = Regex("""Est\.\s*CO2 saved\s+([\d.]+)\s*kg""", RegexOption.IGNORE_CASE)
    // Lookbehind skips the company's "Registered Address" block near the top of the page.
    private val ADDRESS = Regex("""(?<!Registered )Address\s+([^\n\r]+)""")
    private val SESSION = Regex(
        """Session\s+(start|end)\s+(\d{1,2})(?:st|nd|rd|th)\s+([A-Za-z]{3,4})\.?\s+(\d{4})\s+(\d{2}:\d{2}:\d{2})"""
    )
    private val LINE_ITEM = Regex(
        """(\d{2}:\d{2})\s+(\d{2}/\d{2}/\d{2})\s+(\d{2}:\d{2})\s+(\d{2}/\d{2}/\d{2})\s+""" +
            """(Consumption \[kWh]|Charging time \[min]|Parking time \[min]|Fees \[-])\s+""" +
            """([£€])\s*([\d.]+)\s+([\d.]+)\s+[£€]\s*([\d.]+)\s+[£€]\s*([\d.]+)\s+[£€]\s*([\d.]+)"""
    )
    private val TOTAL = Regex("""Total\s+([£€])\s*([\d.]+)\s+[£€]\s*([\d.]+)\s+[£€]\s*([\d.]+)""")
    private val AMOUNT_PAYABLE = Regex("""Amount payable\s+([£€])\s*([\d.]+)""")

    fun parsePdf(
        inputStream: InputStream,
        fileName: String,
        now: Long = System.currentTimeMillis()
    ): Result {
        val text = try {
            PDDocument.load(inputStream).use { doc -> PDFTextStripper().getText(doc) }
        } catch (e: Exception) {
            return Result.Failure(fileName, "Could not read PDF: ${e.message}")
        }
        return parse(text, fileName, now)
    }

    fun parse(text: String, fileName: String, now: Long): Result {
        val fnMatch = FILENAME_REGEX.find(fileName)
            ?: return Result.Failure(fileName, "Unexpected file name")
        val sourceFileId = fnMatch.groupValues[2]

        val receiptMatch = RECEIPT_PROVIDER.find(text)
            ?: return Result.Failure(fileName, "No receipt number found")
        val receiptNumber = receiptMatch.groupValues[1]
        val provider = receiptMatch.groupValues[2].trim().ifEmpty { null }

        val sessions = SESSION.findAll(text)
            .mapNotNull { m -> parseSessionEpoch(m)?.let { m.groupValues[1] to it } }
            .toMap()
        val startEpoch = sessions["start"]
            ?: return Result.Failure(fileName, "No session start found")
        val endEpoch = sessions["end"] ?: startEpoch

        var kwh = 0.0
        var energyCostGross = 0.0
        var idleCostGross = 0.0
        var consumptionRows = 0
        var firstSymbol: String? = null
        for (m in LINE_ITEM.findAll(text)) {
            val symbol = m.groupValues[6]
            if (firstSymbol == null) firstSymbol = symbol
            val units = m.groupValues[8].toDoubleOrNull() ?: 0.0
            val gross = m.groupValues[11].toDoubleOrNull() ?: 0.0
            when (m.groupValues[5]) {
                "Consumption [kWh]" -> { kwh += units; energyCostGross += gross; consumptionRows++ }
                "Charging time [min]" -> energyCostGross += gross
                "Parking time [min]" -> idleCostGross += gross
                "Fees [-]" -> idleCostGross += gross
            }
        }
        if (consumptionRows == 0) return Result.Failure(fileName, "No consumption line items found")

        val totalMatch = TOTAL.find(text)
        val totalGross = totalMatch?.groupValues?.get(4)?.toDoubleOrNull()
            ?: AMOUNT_PAYABLE.find(text)?.groupValues?.get(2)?.toDoubleOrNull()
            ?: (energyCostGross + idleCostGross)

        val currency = if ((totalMatch?.groupValues?.get(1) ?: firstSymbol) == "€") "EUR" else "GBP"

        return Result.Success(
            ReceiptSessionEntity(
                receiptNumber = receiptNumber,
                sourceFileName = fileName,
                sourceFileId = sourceFileId,
                startEpochMillis = startEpoch,
                endEpochMillis = endEpoch,
                evse = EVSE.find(text)?.groupValues?.get(1),
                provider = provider,
                address = ADDRESS.find(text)?.groupValues?.get(1)?.trim()?.ifEmpty { null },
                currency = currency,
                kwh = kwh,
                energyCostGross = energyCostGross,
                idleCostGross = idleCostGross,
                totalGross = totalGross,
                co2SavedKg = CO2.find(text)?.groupValues?.get(1)?.toDoubleOrNull(),
                excluded = false,
                importedAtEpochMillis = now
            )
        )
    }

    private fun parseSessionEpoch(m: MatchResult): Long? {
        val day = m.groupValues[2].toIntOrNull() ?: return null
        val month = MONTHS[m.groupValues[3].lowercase()] ?: return null
        val year = m.groupValues[4].toIntOrNull() ?: return null
        val time = m.groupValues[5].split(":")
        if (time.size != 3) return null
        return try {
            LocalDateTime.of(year, month, day, time[0].toInt(), time[1].toInt(), time[2].toInt())
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (e: Exception) {
            null
        }
    }
}
