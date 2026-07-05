package com.richwatson.electrofind.util

import com.richwatson.electrofind.api.models.ChargingLocation
import java.text.Normalizer

// A brand is matched against the charger's name + address + city, case- and accent-insensitively.
// Patterns are substring matches unless wholeWord is set — needed where the stem is short enough
// to hide inside unrelated words ("moto" in "motorway", "aldi" in "Vivaldi").
data class Brand(
    val label: String,
    val patterns: List<String>,
    val wholeWord: Boolean = false
)

data class BrandGroup(val label: String, val brands: List<Brand>)

object LocationBrands {

    val groups: List<BrandGroup> = listOf(
        BrandGroup("Fast food", listOf(
            Brand("McDonald's", listOf("mcdonald")),
            Brand("Burger King", listOf("burger king")),
            Brand("KFC", listOf("kfc", "kentucky fried"))
        )),
        BrandGroup("Supermarkets", listOf(
            Brand("Aldi", listOf("aldi"), wholeWord = true),
            Brand("Lidl", listOf("lidl")),
            Brand("Intermarché", listOf("intermarch")),
            Brand("Carrefour", listOf("carrefour")),
            Brand("Tesco", listOf("tesco")),
            Brand("Sainsbury's", listOf("sainsbury")),
            Brand("E.Leclerc", listOf("leclerc")),
            Brand("Auchan", listOf("auchan")),
            Brand("Super U", listOf("super u", "hyper u"), wholeWord = true),
            Brand("Casino", listOf("casino"), wholeWord = true),
            Brand("Monoprix", listOf("monoprix")),
            Brand("Asda", listOf("asda"), wholeWord = true),
            Brand("Morrisons", listOf("morrison")),
            Brand("Waitrose", listOf("waitrose")),
            Brand("M&S", listOf("m&s", "marks & spencer", "marks and spencer")),
            Brand("Co-op", listOf("co-op"))
        )),
        BrandGroup("Hotels", listOf(
            Brand("Ibis", listOf("ibis"), wholeWord = true),
            Brand("Novotel", listOf("novotel")),
            Brand("Mercure", listOf("mercure")),
            Brand("Campanile", listOf("campanile")),
            Brand("Kyriad", listOf("kyriad")),
            Brand("B&B Hotel", listOf("b&b")),
            Brand("Premier Inn", listOf("premier inn")),
            Brand("Holiday Inn", listOf("holiday inn")),
            Brand("Travelodge", listOf("travelodge"))
        )),
        BrandGroup("Motorway services", listOf(
            Brand("Welcome Break", listOf("welcome break")),
            Brand("Moto", listOf("moto"), wholeWord = true),
            Brand("Roadchef", listOf("roadchef")),
            Brand("Extra", listOf("extra"), wholeWord = true),
            Brand("Autogrill", listOf("autogrill")),
            Brand("Aire (FR)", listOf("aire de", "aire du", "aire des", "aire d'"))
        )),
        BrandGroup("Cafés & restaurants", listOf(
            Brand("Starbucks", listOf("starbucks")),
            Brand("Costa", listOf("costa"), wholeWord = true),
            Brand("Greggs", listOf("greggs")),
            Brand("Buffalo Grill", listOf("buffalo grill")),
            Brand("Courtepaille", listOf("courtepaille"))
        )),
        BrandGroup("Retail & DIY", listOf(
            Brand("IKEA", listOf("ikea")),
            Brand("Leroy Merlin", listOf("leroy merlin")),
            Brand("B&Q", listOf("b&q")),
            Brand("Castorama", listOf("castorama")),
            Brand("Decathlon", listOf("decathlon")),
            Brand("Brico (Dépôt/marché)", listOf("brico"))
        ))
    )

    private val brandsByLabel: Map<String, Brand> =
        groups.flatMap { it.brands }.associateBy { it.label }

    private val wholeWordRegexCache = HashMap<String, Regex>()

    private fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")

    private fun patternMatches(haystack: String, pattern: String, wholeWord: Boolean): Boolean =
        if (wholeWord) {
            val regex = wholeWordRegexCache.getOrPut(pattern) {
                Regex("(?<![\\p{L}\\p{N}])${Regex.escape(pattern)}(?![\\p{L}\\p{N}])")
            }
            regex.containsMatchIn(haystack)
        } else {
            haystack.contains(pattern)
        }

    fun matches(charger: ChargingLocation, selectedBrandLabels: Set<String>): Boolean {
        val haystack = normalize("${charger.name} ${charger.address} ${charger.city}")
        return selectedBrandLabels.any { label ->
            brandsByLabel[label]?.let { brand ->
                brand.patterns.any { patternMatches(haystack, it, brand.wholeWord) }
            } == true
        }
    }
}
