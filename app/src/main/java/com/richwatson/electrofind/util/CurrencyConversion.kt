package com.richwatson.electrofind.util

// Static, approximate mid-market rates to GBP. The app has no live forex feed, and chargers only
// report a display symbol (not an ISO currency code, see ChargingLocation.currencySymbol) — so
// this is a manually-updated approximation for rough cross-currency route-cost comparison, not
// an accurate real-time conversion.
//
// "kr" (NOK/SEK/DKK) is deliberately omitted: the symbol alone can't tell those three currencies
// apart and their rates diverge too much (DKK ~0.113, NOK/SEK ~0.07-0.075) to guess safely — a
// charger priced in "kr" is left unconverted rather than risk a misleading figure.
private val RATES_TO_GBP = mapOf(
    "£" to 1.0,
    "€" to 0.84,
    "$" to 0.79,
    "CHF" to 0.90,
    "zł" to 0.20,
    "Kč" to 0.034,
    "Ft" to 0.0021,
    "lei" to 0.17
)

object CurrencyConversion {
    fun rateToGbp(nativeSymbol: String): Double? = RATES_TO_GBP[nativeSymbol]
}
