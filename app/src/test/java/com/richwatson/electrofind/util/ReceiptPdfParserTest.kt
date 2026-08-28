package com.richwatson.electrofind.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptPdfParserTest {

    private val now = 1_700_000_000_000L

    private fun success(text: String, fileName: String): ReceiptPdfParser.Result.Success {
        val r = ReceiptPdfParser.parse(text, fileName, now)
        assertTrue("expected Success but got $r", r is ReceiptPdfParser.Result.Success)
        return r as ReceiptPdfParser.Result.Success
    }

    private fun epochOf(y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int): Long =
        LocalDateTime.of(y, mo, d, h, mi, s).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    // ── Real sample: GBP, single Consumption row ────────────────────────────
    private val gbpSingle = """
        Octopus Electroverse Ltd
        Registered Address
        UK House, 5th Floor, 164-182 Oxford Street,
        London, W1D 1NN, U.K.
        Charge Session Receipt
        Receipt Date: 7th Aug. 2026
        User
        richwatson1003@googlemail.com
        EVSE
        3ti-RBH1-06
        Receipt
        8846915
        Provider
        Fuuse
        Session start
        7th Aug. 2026 19:48:20
        Address
        Riverbridge House, Guildford Road
        Session end
        7th Aug. 2026 23:05:07
        Leatherhead
        Est. CO2 saved
        9.71kg
        KT22 9AD, GBR
        19:48 07/08/26
        23:05 07/08/26
        Consumption [kWh]
        £0.50
        37.07
        £15.45
        £3.09
        £18.54
        19:48 07/08/26
        23:05 07/08/26
        Charging time [min]
        £0.00
        196.00
        £0.00
        £0.00
        £0.00
        19:48 07/08/26
        23:05 07/08/26
        Parking time [min]
        £0.00
        0.00
        £0.00
        £0.00
        £0.00
        19:48 07/08/26
        23:05 07/08/26
        Fees [-]
        £0.00
        1.00
        £0.00
        £0.00
        £0.00
        Total
        £15.45
        £3.09
        £18.54
        Amount payable
        £ 18.54
        Exchange rate
        GBP = 1.000 GBP
    """.trimIndent()

    @Test
    fun parsesGbpSingleConsumptionRow() {
        val e = success(gbpSingle, "Electroverse-2026-08-07-15901091.pdf").entity
        assertEquals("8846915", e.receiptNumber)
        assertEquals("15901091", e.sourceFileId)
        assertEquals("Electroverse-2026-08-07-15901091.pdf", e.sourceFileName)
        assertEquals("GBP", e.currency)
        assertEquals("Fuuse", e.provider)
        assertEquals("3ti-RBH1-06", e.evse)
        assertEquals("Riverbridge House, Guildford Road", e.address)
        assertEquals(37.07, e.kwh, 0.001)
        assertEquals(18.54, e.energyCostGross, 0.001)
        assertEquals(0.0, e.idleCostGross, 0.001)
        assertEquals(18.54, e.totalGross, 0.001)
        assertEquals(9.71, e.co2SavedKg!!, 0.001)
        assertEquals(epochOf(2026, 8, 7, 19, 48, 20), e.startEpochMillis)
        assertEquals(epochOf(2026, 8, 7, 23, 5, 7), e.endEpochMillis)
        assertEquals(now, e.importedAtEpochMillis)
    }

    // ── Real sample: EUR, tiered pricing (two Consumption rows) ─────────────
    private val eurTiered = """
        Charge Session Receipt
        Receipt Date: 16th Aug. 2026
        User
        richwatson1003@googlemail.com
        EVSE
        MAT-060945
        Receipt
        9066577
        Provider
        Izivia
        Session start
        16th Aug. 2026 16:53:40
        Address
        7 Rue de Comboire
        Session end
        16th Aug. 2026 17:35:06
        Échirolles
        Est. CO2 saved
        11.29kg
        38130, FRA
        16:53 16/08/26
        17:00 16/08/26
        Consumption [kWh]
        €0.30
        6.00
        €1.50
        €0.30
        €1.80
        17:00 16/08/26
        17:35 16/08/26
        Consumption [kWh]
        €0.35
        37.10
        €10.82
        €2.17
        €12.99
        16:53 16/08/26
        17:00 16/08/26
        Charging time [min]
        €0.00
        6.00
        €0.00
        €0.00
        €0.00
        17:00 16/08/26
        17:35 16/08/26
        Charging time [min]
        €0.00
        35.00
        €0.00
        €0.00
        €0.00
        16:53 16/08/26
        17:35 16/08/26
        Parking time [min]
        €0.00
        0.00
        €0.00
        €0.00
        €0.00
        16:53 16/08/26
        17:35 16/08/26
        Fees [-]
        €0.00
        1.00
        €0.00
        €0.00
        €0.00
        Total
        €12.32
        €2.47
        €14.79
        Amount payable
        € 14.79
        Exchange rate
        EUR = 1.000 EUR
    """.trimIndent()

    @Test
    fun parsesEurTieredConsumptionRows() {
        val e = success(eurTiered, "Electroverse-2026-08-16-16316406.pdf").entity
        assertEquals("9066577", e.receiptNumber)
        assertEquals("16316406", e.sourceFileId)
        assertEquals("EUR", e.currency)
        assertEquals("Izivia", e.provider)
        assertEquals(43.10, e.kwh, 0.001)
        assertEquals(14.79, e.energyCostGross, 0.001)
        assertEquals(0.0, e.idleCostGross, 0.001)
        assertEquals(14.79, e.totalGross, 0.001)
    }

    // ── Real sample: EUR, non-zero Fee, session spans midnight ─────────────
    private val eurFeeMidnight = """
        Charge Session Receipt
        Receipt Date: 24th Aug. 2026
        User
        richwatson1003@googlemail.com
        EVSE
        92022*001*2*1
        Receipt
        9240234
        Provider
        INDIGO Parkings
        Session start
        23rd Aug. 2026 22:44:54
        Address
        965 Avenue Roger Salengro
        Session end
        24th Aug. 2026 08:35:37
        Chaville
        Est. CO2 saved
        12.42kg
        92370, FRA
        22:44 23/08/26
        08:35 24/08/26
        Consumption [kWh]
        €0.55
        47.42
        €21.73
        €4.35
        €26.08
        22:44 23/08/26
        08:35 24/08/26
        Charging time [min]
        €0.00
        590.00
        €0.00
        €0.00
        €0.00
        22:44 23/08/26
        08:35 24/08/26
        Parking time [min]
        €0.00
        0.00
        €0.00
        €0.00
        €0.00
        22:44 23/08/26
        08:35 24/08/26
        Fees [-]
        €0.99
        1.00
        €0.82
        €0.17
        €0.99
        Total
        €22.55
        €4.52
        €27.07
        Amount payable
        € 27.07
        Exchange rate
        EUR = 1.000 EUR
    """.trimIndent()

    @Test
    fun parsesEurFeeAndMidnightSpanningSession() {
        val e = success(eurFeeMidnight, "Electroverse-2026-08-23-16644693.pdf").entity
        assertEquals("9240234", e.receiptNumber)
        assertEquals("EUR", e.currency)
        assertEquals("INDIGO Parkings", e.provider)
        assertEquals("92022*001*2*1", e.evse)
        assertEquals(47.42, e.kwh, 0.001)
        assertEquals(26.08, e.energyCostGross, 0.001)
        assertEquals(0.99, e.idleCostGross, 0.001)
        assertEquals(27.07, e.totalGross, 0.001)
        // Start date is the 23rd even though the receipt date / end is the 24th.
        val startDate = LocalDate.ofEpochDay(
            java.time.Instant.ofEpochMilli(e.startEpochMillis).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
        )
        assertEquals(LocalDate.of(2026, 8, 23), startDate)
        assertTrue(e.endEpochMillis > e.startEpochMillis)
    }

    // ── Same receipt, one-row-per-line extractor output ────────────────────
    private val gbpInterleaved = """
        Charge Session Receipt
        Receipt Date: 7th Aug. 2026
        User richwatson1003@googlemail.com EVSE 3ti-RBH1-06
        Receipt 8846915 Provider Fuuse
        Session start 7th Aug. 2026 19:48:20 Address Riverbridge House, Guildford Road
        Session end 7th Aug. 2026 23:05:07 Leatherhead
        KT22 9AD, GBR Est. CO2 saved 9.71kg
        19:48 07/08/26 23:05 07/08/26 Consumption [kWh] £0.50 37.07 £15.45 £3.09 £18.54
        19:48 07/08/26 23:05 07/08/26 Charging time [min] £0.00 196.00 £0.00 £0.00 £0.00
        19:48 07/08/26 23:05 07/08/26 Parking time [min] £0.00 0.00 £0.00 £0.00 £0.00
        19:48 07/08/26 23:05 07/08/26 Fees [-] £0.00 1.00 £0.00 £0.00 £0.00
        Total £15.45 £3.09 £18.54
        Amount payable £ 18.54
    """.trimIndent()

    @Test
    fun parsesInterleavedSingleLineFormat() {
        val e = success(gbpInterleaved, "Electroverse-2026-08-07-15901091.pdf").entity
        assertEquals("8846915", e.receiptNumber)
        assertEquals("Fuuse", e.provider)
        assertEquals("3ti-RBH1-06", e.evse)
        assertEquals("GBP", e.currency)
        assertEquals(37.07, e.kwh, 0.001)
        assertEquals(18.54, e.energyCostGross, 0.001)
        assertEquals(0.0, e.idleCostGross, 0.001)
        assertEquals(18.54, e.totalGross, 0.001)
        assertEquals(9.71, e.co2SavedKg!!, 0.001)
        assertEquals(epochOf(2026, 8, 7, 19, 48, 20), e.startEpochMillis)
    }

    @Test
    fun addressIsNullWhenOnlyRegisteredAddressPresent() {
        val text = gbpSingle.replace("Address\nRiverbridge House, Guildford Road\n", "")
        val e = success(text, "Electroverse-2026-08-07-15901091.pdf").entity
        assertNull(e.address)
    }

    @Test
    fun failsOnUnexpectedFileName() {
        val r = ReceiptPdfParser.parse(gbpSingle, "statement-2026.pdf", now)
        assertTrue(r is ReceiptPdfParser.Result.Failure)
    }

    @Test
    fun failsWhenNoReceiptNumber() {
        val text = gbpSingle.replace("Receipt\n8846915\nProvider\nFuuse", "Provider\nFuuse")
        val r = ReceiptPdfParser.parse(text, "Electroverse-2026-08-07-15901091.pdf", now)
        assertTrue(r is ReceiptPdfParser.Result.Failure)
    }

    @Test
    fun failsWhenNoConsumptionRows() {
        val text = gbpSingle
            .replace(Regex("""19:48 07/08/26\n23:05 07/08/26\nConsumption \[kWh]\n£0\.50\n37\.07\n£15\.45\n£3\.09\n£18\.54\n"""), "")
        val r = ReceiptPdfParser.parse(text, "Electroverse-2026-08-07-15901091.pdf", now)
        assertTrue(r is ReceiptPdfParser.Result.Failure)
    }
}
