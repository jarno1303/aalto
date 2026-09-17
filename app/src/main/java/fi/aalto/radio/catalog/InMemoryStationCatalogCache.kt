package fi.aalto.radio.catalog

import java.util.concurrent.ConcurrentHashMap

class InMemoryStationCatalogCache : StationCatalogCache {
    private val countryEntries = ConcurrentHashMap<String, CatalogCacheEntry>()
    private val countryMetadata = ConcurrentHashMap<String, CatalogCacheMetadata>()
    private val searchEntries = ConcurrentHashMap<String, CatalogCacheEntry>()

    override suspend fun getCountry(countryCode: String): CatalogCacheEntry? {
        return countryEntries[countryCode.uppercase()]
    }

    override suspend fun getCountryMetadata(countryCode: String): CatalogCacheMetadata? {
        return countryMetadata[countryCode.uppercase()]
    }

    override suspend fun putCountry(countryCode: String, entry: CatalogCacheEntry) {
        val key = countryCode.uppercase()
        countryEntries[key] = entry
        val previous = countryMetadata[key]
        countryMetadata[key] = CatalogCacheMetadata(
            source = CatalogSource.RADIO_BROWSER,
            countryCode = key,
            lastSuccessfulRefreshAt = entry.fetchedAtEpochMs,
            lastAttemptAt = entry.fetchedAtEpochMs,
            stationCount = entry.stations.size
        ).let { current ->
            current.copy(lastAttemptAt = maxOf(previous?.lastAttemptAt ?: 0L, current.lastAttemptAt ?: 0L))
        }
    }

    override suspend fun markCountryFetchAttempt(countryCode: String, attemptedAtEpochMs: Long) {
        val key = countryCode.uppercase()
        val previous = countryMetadata[key]
        countryMetadata[key] = CatalogCacheMetadata(
            source = previous?.source ?: CatalogSource.RADIO_BROWSER,
            countryCode = key,
            lastSuccessfulRefreshAt = previous?.lastSuccessfulRefreshAt,
            lastAttemptAt = attemptedAtEpochMs,
            stationCount = previous?.stationCount ?: 0
        )
    }

    override suspend fun getSearch(query: String, countryCode: String?): CatalogCacheEntry? {
        return searchEntries[searchKey(query, countryCode)]
    }

    override suspend fun putSearch(query: String, countryCode: String?, entry: CatalogCacheEntry) {
        searchEntries[searchKey(query, countryCode)] = entry
    }

    override suspend fun clear() {
        countryEntries.clear()
        countryMetadata.clear()
        searchEntries.clear()
    }

    private fun searchKey(query: String, countryCode: String?): String {
        return "${countryCode?.uppercase().orEmpty()}:${query.trim().lowercase()}"
    }
}
