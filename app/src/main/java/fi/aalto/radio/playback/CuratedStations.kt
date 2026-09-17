package fi.aalto.radio.playback

import fi.aalto.radio.RadioStation

/**
 * Hand-picked top stations per country, in the order people actually listen
 * to them (Finland: national listening research). Only names are listed, so
 * stream addresses always come from the live catalog and can never go stale.
 *
 * Everything else keeps the catalog's own order after these.
 */
internal object CuratedStations {

    private val byCountry: Map<String, List<String>> = mapOf(
        "FI" to listOf(
            "Yle Radio Suomi",
            "Radio Suomipop",
            "Radio Nova",
            "Iskelmä",
            "Radio Rock",
            "YleX",
            "Radio Aalto",
            "Yle Puhe",
            "Loop",
            "Voice",
            "Radio City",
            "Yle Radio 1",
            "Radio Helsinki",
            "Yle Klassinen",
            "Yle Vega"
        )
    )

    fun sort(countryCode: String, stations: List<RadioStation>): List<RadioStation> {
        val curated = byCountry[countryCode.uppercase()] ?: return stations
        val ranks = curated.withIndex().associate { (index, name) -> normalize(name) to index }
        if (ranks.isEmpty()) return stations

        // Rank per station: exact name first, then "Iskelmä Helsinki"-style prefixes.
        fun rank(station: RadioStation): Int {
            val name = normalize(station.name)
            ranks[name]?.let { return it }
            val prefix = ranks.entries
                .filter { (curatedName, _) -> name.startsWith(curatedName) }
                .minByOrNull { (curatedName, _) -> name.length - curatedName.length }
            return prefix?.value?.let { it + curated.size } ?: (curated.size * 2)
        }

        return stations
            .withIndex()
            .sortedWith(compareBy({ rank(it.value) }, { it.index }))
            .map { it.value }
    }

    private fun normalize(value: String): String =
        value.lowercase().filter { it.isLetterOrDigit() }
}
