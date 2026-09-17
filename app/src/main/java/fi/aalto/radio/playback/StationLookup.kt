package fi.aalto.radio.playback

import android.content.Context
import fi.aalto.radio.AaltoAppContainer
import fi.aalto.radio.AaltoDatabase
import fi.aalto.radio.RadioStation
import fi.aalto.radio.catalog.CatalogReadResult
import fi.aalto.radio.catalog.toDomain
import fi.aalto.radio.defaultRadioCountryCode
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

    suspend fun favorites(): List<RadioStation> =
        byIds(runCatching { database.localRadioDao().activeFavoriteIdsInOrder() }.getOrDefault(emptyList()))

    suspend fun recents(limit: Int = 30): List<RadioStation> =
        byIds(runCatching { database.localRadioDao().recentStationIds(limit) }.getOrDefault(emptyList()))

    suspend fun popular(limit: Int = 40): List<RadioStation> {
        val catalog = AaltoAppContainer.stationCatalogRepository(appContext)
        val fromCatalog = when (val result = runCatching {
            catalog.getStationsByCountry(defaultRadioCountryCode(), limit)
        }.getOrNull()) {
            is CatalogReadResult.Success -> result.snapshot.stations.mapNotNull { it.toPlayableRadioStationOrNull() }
            else -> emptyList()
        }
        return (repository.stations + fromCatalog).distinctBy { it.id }.take(limit)
    }

    suspend fun search(query: String, limit: Int = 40): List<RadioStation> {
        val terms = query.trim().lowercase()
        if (terms.isBlank()) return emptyList()
        val local = (favorites() + repository.stations)
            .filter { it.name.lowercase().contains(terms) }
        val catalog = AaltoAppContainer.stationCatalogRepository(appContext)
        val remote = when (val result = runCatching {
            catalog.searchStations(query, defaultRadioCountryCode(), limit)
        }.getOrNull()) {
            is CatalogReadResult.Success -> result.snapshot.stations.mapNotNull { it.toPlayableRadioStationOrNull() }
            else -> emptyList()
        }
        val merged = (local + remote).distinctBy { it.id }
        // If the catalog search found nothing in the home country, try everywhere.
        if (merged.isNotEmpty()) return merged.take(limit)
        return when (val result = runCatching { catalog.searchStations(query, null, limit) }.getOrNull()) {
            is CatalogReadResult.Success -> result.snapshot.stations.mapNotNull { it.toPlayableRadioStationOrNull() }
            else -> emptyList()
        }
    }
}
