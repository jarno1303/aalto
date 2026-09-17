package fi.aalto.radio

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class StationRepository(
    private val dao: LocalRadioDao,
    private val legacyFavoriteStore: LegacyFavoriteStationStore,
    private val deviceIdProvider: () -> String,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val mutationIdFactory: () -> String = SyncMutationIds::newId,
    private val recentLimit: Int = DEFAULT_RECENT_LIMIT
) {
    private val catalogStationsById = ConcurrentHashMap<String, RadioStation>()

    val stations: List<RadioStation>
        get() = (StationCatalog.allStations + catalogStationsById.values)
            .distinctBy { it.id }

    fun stationById(stationId: String): RadioStation? {
        return StationCatalog.stationById(stationId) ?: catalogStationsById[stationId]
    }

    fun registerCatalogStations(stations: Iterable<RadioStation>) {
        stations.forEach { station -> catalogStationsById[station.id] = station }
    }

    fun searchStations(query: String): List<RadioStation> {
        return StationCatalog.search(query)
    }

    fun initialFavoriteIds(): Set<String> {
        return if (legacyFavoriteStore.isRoomMigrationComplete()) {
            FavoriteIds.defaultFavorites
        } else {
            legacyFavoriteStore.favoriteIdsForMigration()
        }
    }

    suspend fun prepareLocalData() {
        withContext(ioDispatcher) {
            runCatching {
                dao.upsertStations(stations.map { it.toEntity(updatedAt = clock()) })
            }.onFailure { error ->
                Log.w(TAG, "Station seed failed", error)
            }

            runCatching {
                migrateFavoritesIfNeeded()
            }.onFailure { error ->
                Log.w(TAG, "Favorite migration failed", error)
            }
        }
    }

    fun observeFavoriteIds(): Flow<Set<String>> {
        return dao.observeFavoriteIds()
            .map { it.toSet() }
            .catch { error ->
                Log.w(TAG, "Favorite observation failed", error)
                emit(initialFavoriteIds())
            }
    }

    fun observeFavoriteOrder(): Flow<List<String>> {
        return dao.observeFavoriteIds()
            .onEach { order ->
                if (BuildConfig.DEBUG) Log.d("AALTO_SYNC", "favorite_reorder_loaded order=$order")
            }
            .catch { error ->
                Log.w(TAG, "Favorite ordering observation failed", error)
                emit(emptyList())
            }
    }

    suspend fun favoriteIdsSnapshot(): Set<String> {
        return withContext(ioDispatcher) {
            dao.favoriteIds().toSet()
        }
    }

    suspend fun addFavorite(stationId: String): Boolean {
        return withContext(ioDispatcher) {
            ensureStationPersisted(stationId)
            val deviceId = deviceIdProvider()
            dao.addFavoriteAndEnqueueMutation(
                stationId = stationId,
                deviceId = deviceId,
                mutationId = mutationIdFactory(),
                now = clock(),
                payload = StationCatalog.stationById(stationId)?.let(StationSnapshotPayload::encode)
            )
        }
    }

    suspend fun removeFavorite(stationId: String): Boolean {
        return withContext(ioDispatcher) {
            ensureStationPersisted(stationId)
            val deviceId = deviceIdProvider()
            dao.removeFavoriteAndEnqueueMutation(
                stationId = stationId,
                deviceId = deviceId,
                mutationId = mutationIdFactory(),
                now = clock(),
                payload = StationCatalog.stationById(stationId)?.let(StationSnapshotPayload::encode)
            )
        }
    }

    suspend fun reorderFavorites(orderedStationIds: List<String>): Boolean {
        return withContext(ioDispatcher) {
            val deviceId = deviceIdProvider()
            val committed = dao.reorderFavoritesAndEnqueueMutation(
                orderedStationIds = orderedStationIds,
                deviceId = deviceId,
                mutationId = mutationIdFactory(),
                now = clock()
            )
            if (committed && BuildConfig.DEBUG) {
                Log.d("AALTO_SYNC", "favorite_reorder_room_committed order=${dao.favoriteIds()}")
            }
            committed
        }
    }

    suspend fun recordRecentlyPlayed(stationId: String) {
        withContext(ioDispatcher) {
            ensureStationPersisted(stationId)
            dao.recordRecentStation(
                stationId = stationId,
                lastPlayedAt = clock(),
                limit = recentLimit
            )
        }
    }

    suspend fun recentStations(limit: Int = recentLimit): List<RadioStation> {
        return withContext(ioDispatcher) {
            dao.recentStationIds(limit)
                .mapNotNull(::stationById)
        }
    }

    private suspend fun migrateFavoritesIfNeeded() {
        if (legacyFavoriteStore.isRoomMigrationComplete()) {
            return
        }

        legacyFavoriteStore.favoriteIdsForMigration()
            .filter { stationById(it) != null }
            .forEach { stationId ->
                ensureStationPersisted(stationId)
                dao.addFavoriteForMigration(
                    stationId = stationId,
                    now = clock()
                )
            }

        legacyFavoriteStore.markRoomMigrationComplete()
    }

    private suspend fun ensureStationPersisted(stationId: String) {
        if (dao.stationExists(stationId) != null) {
            return
        }

        val station = stationById(stationId) ?: return
        dao.upsertStation(station.toEntity(updatedAt = clock()))
    }

    companion object {
        private const val TAG = "AaltoLocalData"
        const val DEFAULT_RECENT_LIMIT = 50
    }
}
