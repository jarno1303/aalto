package fi.aalto.radio.playback

import android.content.Context
import fi.aalto.radio.AaltoAppContainer
import fi.aalto.radio.AaltoDatabase
import fi.aalto.radio.RadioStation
import fi.aalto.radio.catalog.CatalogReadResult
import fi.aalto.radio.catalog.toDomain
import fi.aalto.radio.toDomain
import fi.aalto.radio.toPlayableRadioStationOrNull

/**
 * Finds stations without the phone UI running (car, widget, resume after
 * reboot): built-in list and memory first, then the local database, then the
 * cached catalog.
 */
internal class StationLookup(context: Context) {
    private val appContext = context.applicationContext
    private val repository = AaltoAppContainer.stationRepository(appContext)
    private val database = AaltoDatabase.getInstance(appContext)

    suspend fun byId(id: String): RadioStation? = byIds(listOf(id)).firstOrNull()

    suspend fun byIds(ids: List<String>): List<RadioStation> {
        if (ids.isEmpty()) return emptyList()
        val found = LinkedHashMap<String, RadioStation>()
        ids.forEach { id -> repository.stationById(id)?.let { found[id] = it } }

        val missing = ids.filter { it !in found }
        if (missing.isNotEmpty()) {
            runCatching { database.localRadioDao().stationsByIds(missing) }
                .getOrDefault(emptyList())
                .forEach { entity -> found[entity.id] = entity.toDomain() }
        }

        ids.filter { it !in found }.forEach { id ->
            runCatching { database.catalogStationDao().stationBySourceQualifiedId(id) }
                .getOrNull()
                ?.toDomain()
                ?.toPlayableRadioStationOrNull()
                ?.let { found[id] = it }
        }
        return ids.mapNotNull { found[it] }
    }

    /**
     * Catalog stations shown in the car are registered in the shared
     * repository, so a later tap (which sends only the id) finds them again.
     * Catalog ids are canonical ids, not always the raw Radio Browser id.
     */
    private fun remember(stations: List<RadioStation>) {
        if (stations.isNotEmpty()) repository.registerCatalogStations(stations)
    }

    suspend fun favorites(): List<RadioStation> =
        byIds(runCatching { database.localRadioDao().activeFavoriteIdsInOrder() }.getOrDefault(emptyList()))

    suspend fun recents(limit: Int = 30): List<RadioStation> =
        byIds(runCatching { database.localRadioDao().recentStationIds(limit) }.getOrDefault(emptyList()))

    suspend fun popular(limit: Int = 40, countryCode: String = homeCountry()): List<RadioStation> {
        val catalog = AaltoAppContainer.stationCatalogRepository(appContext)
        val fromCatalog = when (val result = runCatching {
            catalog.getStationsByCountry(countryCode, limit)
        }.getOrNull()) {
            is CatalogReadResult.Success -> result.snapshot.stations.mapNotNull { it.toPlayableRadioStationOrNull() }
            else -> emptyList()
        }
        remember(fromCatalog)
        // Built-in (Finnish) stations only belong to the Finnish list.
        val builtIn = if (countryCode == "FI") repository.stations else emptyList()
        val all = (fromCatalog + builtIn).distinctBy { it.id }
        // Best-known stations of the country first, catalog order after them.
        return CuratedStations.sort(countryCode, all).take(limit)
    }

    /**
     * The user's home country (phone region). The car's "Suositut" tab always
     * shows it; a temporary country filter in the phone app does not change it.
     */
    /** The car's Popular tab follows the same country as the phone. */
    fun homeCountry(): String = fi.aalto.radio.RadioCountryPreference.get(appContext)

    suspend fun search(query: String, limit: Int = 40): List<RadioStation> {
        val terms = query.trim().lowercase()
        if (terms.isBlank()) return emptyList()
        val local = (favorites() + repository.stations)
            .filter { it.name.lowercase().contains(terms) }
        val catalog = AaltoAppContainer.stationCatalogRepository(appContext)
        suspend fun remote(countryCode: String?): List<RadioStation> =
            when (val result = runCatching { catalog.searchStations(query, countryCode, limit) }.getOrNull()) {
                is CatalogReadResult.Success -> result.snapshot.stations.mapNotNull { it.toPlayableRadioStationOrNull() }
                else -> emptyList()
            }
        // Home country first, then the whole world (e.g. "Radio Bob" from Finland).
        val inCountry = remote(homeCountry())
        val worldwide = if (inCountry.size < limit / 2) remote(null) else emptyList()
        return (local + inCountry + worldwide).distinctBy { it.id }.take(limit)
            .also { remember(it) }
    }
}
