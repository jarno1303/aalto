package fi.aalto.radio

import fi.aalto.radio.catalog.CatalogStation
import kotlin.math.ln

/**
 * Deterministic discovery ranking.
 *
 * The catalog quality engine decides which stations are safe enough to expose.
 * This layer decides which of those useful stations should be shown first for
 * this user and this query. No network work happens here and playback is never
 * involved.
 */
internal object DiscoveryRanking {

    fun rank(
        stations: List<RadioStation>,
        query: String,
        favoriteIds: Set<String>,
        recentStations: List<RadioStation>,
        catalogStations: List<CatalogStation>
    ): List<RadioStation> {
        if (stations.size < 2) return stations

        val catalog = CatalogLookup(catalogStations)
        val recentIndexes = recentStations
            .mapIndexed { index, station -> station.stableId to index }
            .toMap()
        val affinity = affinityWeights(recentStations)

        return stations.withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<RadioStation>> { indexed ->
                    score(
                        station = indexed.value,
                        query = query,
                        favoriteIds = favoriteIds,
                        recentIndexes = recentIndexes,
                        affinity = affinity,
                        catalogStation = catalog[indexed.value]
                    )
                }.thenBy { it.index }
            )
            .map { it.value }
    }

    /**
     * Stations related to what the listener is currently hearing. Suggestions
     * need at least one real semantic connection (genre/category/language);
     * merely sharing a country is not enough.
     */
    fun similarStations(
        candidates: List<RadioStation>,
        selectedStation: RadioStation,
        favoriteIds: Set<String>,
        recentStations: List<RadioStation>,
        catalogStations: List<CatalogStation>,
        limit: Int = 4
    ): List<RadioStation> {
        if (limit <= 0) return emptyList()

        val selectedTags = stationTags(selectedStation)
        val selectedLanguages = selectedStation.languages.normalizedWords()
        val selectedCategory = selectedStation.category.normalized()
        val catalog = CatalogLookup(catalogStations)
        val recentIndexes = recentStations
            .mapIndexed { index, station -> station.stableId to index }
            .toMap()
        val affinity = affinityWeights(recentStations)

        return candidates
            .asSequence()
            .filterNot { it.stableId == selectedStation.stableId }
            .map { station ->
                val tags = stationTags(station)
                val sharedTags = tags.intersect(selectedTags).size
                val sharedLanguages = station.languages.normalizedWords().intersect(selectedLanguages).size
                val sameCategory = selectedCategory.isNotBlank() && station.category.normalized() == selectedCategory
                val meaningfulMatch = sharedTags > 0 || sharedLanguages > 0 || sameCategory

                val similarity = sharedTags.coerceAtMost(3) * 14.0 +
                    sharedLanguages.coerceAtMost(2) * 8.0 +
                    (if (sameCategory) 14.0 else 0.0) +
                    (if (station.countryCode.equals(selectedStation.countryCode, ignoreCase = true)) 5.0 else 0.0)

                Triple(station, meaningfulMatch, similarity)
            }
            .filter { (_, meaningfulMatch, _) -> meaningfulMatch }
            .sortedWith(
                compareByDescending<Triple<RadioStation, Boolean, Double>> { (_, _, similarity) -> similarity }
                    .thenByDescending { (station, _, _) ->
                        score(
                            station = station,
                            query = "",
                            favoriteIds = favoriteIds,
                            recentIndexes = recentIndexes,
                            affinity = affinity,
                            catalogStation = catalog[station]
                        )
                    }
                    .thenBy { (station, _, _) -> station.name.lowercase() }
            )
            .map { it.first }
            .distinctBy { it.stableId }
            .take(limit)
            .toList()
    }

    private fun score(
        station: RadioStation,
        query: String,
        favoriteIds: Set<String>,
        recentIndexes: Map<String, Int>,
        affinity: Map<String, Double>,
        catalogStation: CatalogStation?
    ): Double {
        var score = queryScore(station, query)

        if (station.stableId in favoriteIds || station.id in favoriteIds) score += 12.0

        recentIndexes[station.stableId]?.let { index ->
            score += (18.0 - index * 2.0).coerceAtLeast(2.0)
        }

        score += stationTags(station)
            .sumOf { affinity[it] ?: 0.0 }
            .coerceAtMost(18.0)

        score += qualityScore(catalogStation, station)
        return score
    }

    private fun queryScore(station: RadioStation, query: String): Double {
        val normalizedQuery = query.normalized()
        if (normalizedQuery.isBlank()) return 0.0

        val name = station.name.normalized()
        val tokens = normalizedQuery.split(' ').filter(String::isNotBlank)
        val tagText = (station.tags + station.category).joinToString(" ").normalized()
        val secondary = listOf(
            station.description,
            station.location.orEmpty(),
            station.countryCode,
            station.languages.joinToString(" ")
        ).joinToString(" ").normalized()

        return when {
            name == normalizedQuery -> 140.0
            name.startsWith(normalizedQuery) -> 110.0
            name.contains(normalizedQuery) -> 90.0
            tokens.all(name::contains) -> 70.0
            tokens.all(tagText::contains) -> 48.0
            tokens.all { token -> name.contains(token) || tagText.contains(token) } -> 40.0
            tokens.all(secondary::contains) -> 25.0
            else -> 10.0
        }
    }

    private fun qualityScore(catalog: CatalogStation?, station: RadioStation): Double {
        var score = when (catalog?.lastCheckOk) {
            true -> 18.0
            false -> -30.0
            null -> 0.0
        }

        catalog?.clickCount?.takeIf { it > 0 }?.let {
            score += 12.0 * boundedLog(it.toDouble(), 1_000_000.0)
        }
        catalog?.votes?.takeIf { it > 0 }?.let {
            score += 6.0 * boundedLog(it.toDouble(), 10_000.0)
        }
        catalog?.clickTrend?.let {
            score += 4.0 * ((it.coerceIn(-1_000, 1_000) + 1_000) / 2_000.0)
        }

        val bitrate = catalog?.bitrateKbps ?: station.declaredBitrateKbps
        score += when (bitrate) {
            null -> 0.0
            in 1..47 -> 0.5
            in 48..95 -> 2.0
            in 96..191 -> 4.0
            in 192..512 -> 6.0
            else -> 0.0
        }
        return score
    }

    /**
     * A few recent stations are enough to learn "rock listener" or "news
     * listener" without building a profile or persisting another user model.
     */
    private fun affinityWeights(recentStations: List<RadioStation>): Map<String, Double> {
        val weights = mutableMapOf<String, Double>()
        recentStations.take(12).forEachIndexed { index, station ->
            val recency = (6.0 - index * 0.35).coerceAtLeast(1.5)
            stationTags(station).forEach { tag ->
                weights[tag] = (weights[tag] ?: 0.0) + recency
            }
        }
        return weights
    }

    private fun boundedLog(value: Double, cap: Double): Double =
        (ln(1.0 + value.coerceAtLeast(0.0)) / ln(1.0 + cap)).coerceIn(0.0, 1.0)

    private fun String.normalized(): String =
        lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

    private fun List<String>.normalizedWords(): Set<String> =
        map { it.normalized() }.filter(String::isNotBlank).toSet()

    private class CatalogLookup(stations: List<CatalogStation>) {
        private val byStableId = stations.associateBy { it.stableId }
        private val bySourceId = stations.associateBy { it.sourceStationId }

        operator fun get(station: RadioStation): CatalogStation? =
            byStableId[station.stableId]
                ?: station.radioBrowserStationUuid?.let(bySourceId::get)
    }
}
