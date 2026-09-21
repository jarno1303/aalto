package fi.aalto.radio.catalog

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import fi.aalto.radio.catalog.quality.CatalogQualityEngine

enum class CatalogFreshness {
    FRESH,
    STALE,
    NO_CACHE
}

data class CatalogSnapshot(
    val stations: List<CatalogStation>,
    val freshness: CatalogFreshness,
    val fetchedAtEpochMs: Long?,
    val needsRefresh: Boolean = freshness == CatalogFreshness.STALE
)

sealed interface CatalogReadResult {
    data class Success(val snapshot: CatalogSnapshot) : CatalogReadResult
    data class Failure(val error: CatalogError) : CatalogReadResult
}

class StationCatalogRepository(
    private val source: StationCatalogSource,
    private val cache: StationCatalogCache = InMemoryStationCatalogCache(),
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val cacheTtlMs: Long = DEFAULT_CATALOG_TTL_MS,
    private val refreshScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val qualityEngine = CatalogQualityEngine()
    // Coverage is not the number left after duplicate cleanup. A 40-row fetch
    // cannot satisfy a later 100-row request, even while its timestamp is fresh.
    // Persisted caches from an earlier process have unknown coverage: show them
    // immediately, then refill once if they are shorter than the requested list.
    private val countryCoverage = ConcurrentHashMap<String, Pair<Long, Int>>()

    suspend fun getStationsByCountry(
        countryCode: String,
        limit: Int = DEFAULT_CATALOG_RESULT_LIMIT
    ): CatalogReadResult {
        val normalizedCode = normalizeCountryCode(countryCode)
            ?: return CatalogReadResult.Failure(CatalogError(CatalogErrorKind.INVALID_REQUEST, "Invalid country code"))
        val boundedLimit = boundedLimit(limit)
        cache.getCountry(normalizedCode)?.let { entry ->
            val snapshot = CatalogSnapshot(
                stations = cleanStations(entry.stations).take(boundedLimit),
                freshness = if (isFresh(entry)) CatalogFreshness.FRESH else CatalogFreshness.STALE,
                fetchedAtEpochMs = entry.fetchedAtEpochMs,
                needsRefresh = !isFresh(entry) || !coversCountry(normalizedCode, entry, boundedLimit)
            )
            return CatalogReadResult.Success(snapshot)
        }

        return when (val result = refreshCountry(normalizedCode, boundedLimit)) {
            is CatalogResult.Success -> CatalogReadResult.Success(
                CatalogSnapshot(result.value, CatalogFreshness.FRESH, clock())
            )
            is CatalogResult.Failure -> CatalogReadResult.Failure(result.error)
        }
    }

    suspend fun searchStations(
        query: String,
        countryCode: String? = null,
        limit: Int = DEFAULT_CATALOG_RESULT_LIMIT
    ): CatalogReadResult {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            return CatalogReadResult.Failure(CatalogError(CatalogErrorKind.INVALID_REQUEST, "Search query is blank"))
        }
        val normalizedCode = countryCode?.let(::normalizeCountryCode)
            ?: if (countryCode == null) null else return CatalogReadResult.Failure(
                CatalogError(CatalogErrorKind.INVALID_REQUEST, "Invalid country code")
            )
        val boundedLimit = boundedLimit(limit)
        val cacheKey = "query:$boundedLimit:$normalizedQuery"
        cache.getSearch(cacheKey, normalizedCode)?.takeIf { isFresh(it) }?.let {
            return CatalogReadResult.Success(CatalogSnapshot(cleanStations(it.stations).take(boundedLimit), CatalogFreshness.FRESH, it.fetchedAtEpochMs))
        }

        val result = source.search(normalizedQuery, normalizedCode, boundedLimit)
        if (result is CatalogResult.Success) {
            val cleaned = cleanStations(result.value)
            cache.putSearch(cacheKey, normalizedCode, CatalogCacheEntry(cleaned, clock()))
            return CatalogReadResult.Success(CatalogSnapshot(cleaned, CatalogFreshness.FRESH, clock()))
        }
        val failure = result as CatalogResult.Failure
        return CatalogReadResult.Failure(failure.error)
    }

    suspend fun getStationsByTag(
        tag: String,
        countryCode: String?,
        limit: Int = DEFAULT_CATALOG_RESULT_LIMIT
    ): CatalogReadResult {
        val normalizedTag = tag.trim().lowercase()
        val normalizedCode = countryCode?.let(::normalizeCountryCode)
        if (normalizedTag.isBlank() || (countryCode != null && normalizedCode == null)) {
            return CatalogReadResult.Failure(CatalogError(CatalogErrorKind.INVALID_REQUEST, "Invalid tag search"))
        }
        val boundedLimit = boundedLimit(limit)
        val key = "tag:$boundedLimit:$normalizedTag"
        cache.getSearch(key, normalizedCode)?.takeIf { isFresh(it) }?.let {
            return CatalogReadResult.Success(CatalogSnapshot(it.stations, CatalogFreshness.FRESH, it.fetchedAtEpochMs))
        }
        return when (val result = source.stationsByTag(normalizedTag, normalizedCode, boundedLimit)) {
            is CatalogResult.Success -> {
                val cleaned = cleanStations(result.value)
                val fetchedAt = clock()
                cache.putSearch(key, normalizedCode, CatalogCacheEntry(cleaned, fetchedAt))
                CatalogReadResult.Success(CatalogSnapshot(cleaned, CatalogFreshness.FRESH, fetchedAt))
            }
            is CatalogResult.Failure -> CatalogReadResult.Failure(result.error)
        }
    }

    suspend fun refreshCountry(
        countryCode: String,
        limit: Int = DEFAULT_CATALOG_RESULT_LIMIT
    ): CatalogResult<List<CatalogStation>> {
        val normalizedCode = normalizeCountryCode(countryCode)
            ?: return CatalogResult.Failure(CatalogError(CatalogErrorKind.INVALID_REQUEST, "Invalid country code"))
        return refreshCountryInternal(normalizedCode, boundedLimit(limit), force = true)
    }

    private suspend fun refreshCountryInternal(
        normalizedCode: String,
        boundedLimit: Int,
        force: Boolean
    ): CatalogResult<List<CatalogStation>> {
        val mutex = refreshLocks.getOrPut(normalizedCode) { Mutex() }
        return mutex.withLock {
            if (!force) cache.getCountry(normalizedCode)?.takeIf { isFresh(it) }?.let {
                return@withLock CatalogResult.Success(cleanStations(it.stations).take(boundedLimit))
            }
            val attemptAt = clock()
            cache.markCountryFetchAttempt(normalizedCode, attemptAt)
            val fetchLimit = maxOf(
                boundedLimit,
                cache.getCountry(normalizedCode)?.stations?.size ?: 0,
                countryCoverage[normalizedCode]?.second ?: 0
            ).coerceAtMost(MAX_CATALOG_RESULT_LIMIT)
            when (val result = source.stationsByCountry(normalizedCode, fetchLimit)) {
                is CatalogResult.Success -> {
                    val cleaned = cleanStations(result.value)
                    val fetchedAt = clock()
                    cache.putCountry(normalizedCode, CatalogCacheEntry(cleaned, fetchedAt))
                    countryCoverage[normalizedCode] = fetchedAt to fetchLimit
                    CatalogResult.Success(cleaned.take(boundedLimit))
                }
                is CatalogResult.Failure -> result
            }
        }
    }

    private fun cleanStations(stations: List<CatalogStation>): List<CatalogStation> {
        return qualityEngine.curate(stations).stations.map { it.station }
    }

    private fun isFresh(entry: CatalogCacheEntry): Boolean {
        return clock() - entry.fetchedAtEpochMs in 0..cacheTtlMs
    }

    private fun coversCountry(code: String, entry: CatalogCacheEntry, limit: Int): Boolean {
        if (entry.stations.size >= limit) return true
        val coverage = countryCoverage[code] ?: return false
        return coverage.first == entry.fetchedAtEpochMs && coverage.second >= limit
    }

    fun close() {
        refreshScope.cancel()
    }

    private companion object {
        const val DEFAULT_CATALOG_TTL_MS = 12 * 60 * 60 * 1000L
    }

    private val refreshLocks = ConcurrentHashMap<String, Mutex>()
}

internal fun normalizeCountryCode(value: String): String? {
    return value.trim().uppercase().takeIf { it.matches(Regex("[A-Z]{2}")) }
}

internal fun boundedLimit(value: Int): Int {
    return value.coerceIn(1, MAX_CATALOG_RESULT_LIMIT)
}
