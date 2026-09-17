package fi.aalto.radio.catalog.quality

enum class AaltoCategory {
    POP,
    ROCK,
    DANCE_ELECTRONIC,
    HIPHOP_RNB,
    NEWS_TALK,
    CLASSICAL,
    JAZZ_SOUL,
    COUNTRY_FOLK,
    FAMILY,
    OTHER
}

data class CatalogFilter(
    val countryCodes: Set<String> = emptySet(),
    val categories: Set<AaltoCategory> = emptySet(),
    val languages: Set<String> = emptySet()
) {
    fun matches(countryCode: String?, stationCategories: Set<AaltoCategory>, stationLanguages: List<String>): Boolean {
        val normalizedCountries = countryCodes.map { it.trim().uppercase() }.toSet()
        val normalizedLanguages = languages.map { it.trim().lowercase() }.toSet()
        val countryMatches = normalizedCountries.isEmpty() || countryCode?.uppercase() in normalizedCountries
        val categoryMatches = categories.isEmpty() || stationCategories.any { it in categories }
        val languageMatches = normalizedLanguages.isEmpty() || stationLanguages.any { it in normalizedLanguages }
        return countryMatches && categoryMatches && languageMatches
    }
}
