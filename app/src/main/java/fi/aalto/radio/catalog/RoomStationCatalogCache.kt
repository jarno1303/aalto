package fi.aalto.radio.catalog

class RoomStationCatalogCache(
    private val dao: CatalogStationDao,
    private val source: CatalogSource = CatalogSource.RADIO_BROWSER
) : StationCatalogCache {
    override suspend fun getCountry(countryCode: String): CatalogCacheEntry? {
        val normalizedCode = countryCode.uppercase()
        val metadata = dao.countryMetadata(source.name, normalizedCode) ?: return null
        val stations = dao.stationsByCountry(source.name, normalizedCode).mapNotNull { it.toDomain() }
        val fetchedAt = metadata.lastSuccessfulRefreshAt ?: return null
        return CatalogCacheEntry(stations, fetchedAt)
    }

    override suspend fun getCountryMetadata(countryCode: String): CatalogCacheMetadata? {
        return dao.countryMetadata(source.name, countryCode.uppercase())?.toDomain()
    }

    override suspend fun putCountry(countryCode: String, entry: CatalogCacheEntry) {
        val normalizedCode = countryCode.uppercase()
        val previous = dao.countryMetadata(source.name, normalizedCode)
        dao.replaceCountryCatalog(
            source = source.name,
            countryCode = normalizedCode,
            stations = entry.stations.map { it.toEntity(entry.fetchedAtEpochMs) },
            metadata = CatalogCountryCacheMetadataEntity(
                source = source.name,
                countryCode = normalizedCode,
                lastSuccessfulRefreshAt = entry.fetchedAtEpochMs,
                lastAttemptAt = maxOf(previous?.lastAttemptAt ?: 0L, entry.fetchedAtEpochMs),
                stationCount = entry.stations.size
            )
        )
    }

    override suspend fun markCountryFetchAttempt(countryCode: String, attemptedAtEpochMs: Long) {
        dao.markCountryFetchAttempt(source.name, countryCode.uppercase(), attemptedAtEpochMs)
    }

    override suspend fun getSearch(query: String, countryCode: String?): CatalogCacheEntry? = null

    override suspend fun putSearch(query: String, countryCode: String?, entry: CatalogCacheEntry) = Unit

    override suspend fun clear() {
        dao.deleteAllStations()
        dao.deleteAllMetadata()
    }
}
