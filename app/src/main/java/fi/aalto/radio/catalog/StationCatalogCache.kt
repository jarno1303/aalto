package fi.aalto.radio.catalog

data class CatalogCacheEntry(
    val stations: List<CatalogStation>,
    val fetchedAtEpochMs: Long
)

data class CatalogCacheMetadata(
    val source: CatalogSource,
    val countryCode: String,
    val lastSuccessfulRefreshAt: Long?,
    val lastAttemptAt: Long?,
    val stationCount: Int
)

interface StationCatalogCache {
    suspend fun getCountry(countryCode: String): CatalogCacheEntry?

    suspend fun getCountryMetadata(countryCode: String): CatalogCacheMetadata?

    suspend fun putCountry(countryCode: String, entry: CatalogCacheEntry)

    suspend fun markCountryFetchAttempt(countryCode: String, attemptedAtEpochMs: Long)

    suspend fun getSearch(query: String, countryCode: String?): CatalogCacheEntry?

    suspend fun putSearch(query: String, countryCode: String?, entry: CatalogCacheEntry)

    suspend fun clear()
}
