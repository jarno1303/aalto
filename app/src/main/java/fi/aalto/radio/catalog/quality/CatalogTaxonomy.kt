package fi.aalto.radio.catalog.quality

private val categoryPriority = listOf(
    AaltoCategory.POP,
    AaltoCategory.ROCK,
    AaltoCategory.DANCE_ELECTRONIC,
    AaltoCategory.HIPHOP_RNB,
    AaltoCategory.NEWS_TALK,
    AaltoCategory.CLASSICAL,
    AaltoCategory.JAZZ_SOUL,
    AaltoCategory.COUNTRY_FOLK,
    AaltoCategory.FAMILY,
    AaltoCategory.OTHER
)

private val categoryAliases = mapOf(
    AaltoCategory.POP to setOf("pop", "pop_music", "popmusic", "top40", "top_40", "charts", "hits", "hot_ac", "adult_contemporary"),
    AaltoCategory.ROCK to setOf("rock", "classic_rock", "alternative_rock", "hard_rock", "metal"),
    AaltoCategory.DANCE_ELECTRONIC to setOf("dance", "edm", "electronic", "house", "techno", "trance"),
    AaltoCategory.HIPHOP_RNB to setOf("hiphop", "hip_hop", "rap", "rnb", "r_b", "urban"),
    AaltoCategory.NEWS_TALK to setOf("news", "talk", "news_talk", "spoken", "current_affairs", "politics"),
    AaltoCategory.CLASSICAL to setOf("classical", "classic", "opera", "symphony"),
    AaltoCategory.JAZZ_SOUL to setOf("jazz", "soul", "funk", "blues"),
    AaltoCategory.COUNTRY_FOLK to setOf("country", "folk", "americana", "bluegrass"),
    AaltoCategory.FAMILY to setOf("kids", "children", "family")
)

data class TaxonomyResult(
    val categories: Set<AaltoCategory>,
    val primaryCategory: AaltoCategory
)

object CatalogTaxonomy {
    fun classify(rawTags: List<String>): TaxonomyResult {
        val normalizedTags = rawTags.map(::normalizeTag).filter(String::isNotEmpty).distinct()
        val matchCounts = categoryAliases.mapValues { (_, aliases) ->
            normalizedTags.count { tag -> tag in aliases }
        }
        val matched = matchCounts.filterValues { it > 0 }.keys.toSet()
        if (matched.isEmpty()) return TaxonomyResult(setOf(AaltoCategory.OTHER), AaltoCategory.OTHER)

        val primary = categoryPriority.first { category ->
            category in matched && matchCounts[category] == matchCounts.values.maxOrNull()
        }
        return TaxonomyResult(matched, primary)
    }

    fun normalizeTag(value: String): String {
        return value.trim().lowercase()
            .replace('&', '_')
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
    }
}
