package fi.aalto.radio

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import fi.aalto.radio.audio.StationGainSync
import fi.aalto.radio.catalog.RoomStationCatalogCache
import fi.aalto.radio.catalog.StationCatalogRepository
import fi.aalto.radio.catalog.radiobrowser.RadioBrowserCatalogSource

object AaltoAppContainer {
    @Volatile
    private var stationRepository: StationRepository? = null
    @Volatile
    private var syncCoordinator: AaltoSyncCoordinator? = null
    @Volatile
    private var catalogRepository: StationCatalogRepository? = null

    fun stationRepository(context: Context): StationRepository {
        return stationRepository ?: synchronized(this) {
            stationRepository ?: createStationRepository(context)
                .also { stationRepository = it }
        }
    }

    private fun createStationRepository(context: Context): StationRepository {
        val database = AaltoDatabase.getInstance(context)
        val deviceIdentityStore = DeviceIdentityStore(context.applicationContext)
        return StationRepository(
            dao = database.localRadioDao(),
            legacyFavoriteStore = LegacyFavoriteStationStore(context),
            deviceIdProvider = deviceIdentityStore::deviceId
        )
    }

    fun syncCoordinator(context: Context): AaltoSyncCoordinator {
        return syncCoordinator ?: synchronized(this) {
            syncCoordinator ?: run {
                val appContext = context.applicationContext
                val database = AaltoDatabase.getInstance(appContext)
                val identity = DeviceIdentityStore(appContext)
                AaltoSyncCoordinator(
                    authManager = AaltoAuthManager(appContext),
                    dao = database.localRadioDao(),
                    deviceIdProvider = identity::deviceId,
                    firestore = FirebaseFirestore.getInstance()
                ).also { syncCoordinator = it }
            }
        }.also { StationGainSync.start(context) }
    }

    fun stationCatalogRepository(context: Context): StationCatalogRepository {
        return catalogRepository ?: synchronized(this) {
            catalogRepository ?: run {
                val appContext = context.applicationContext
                val database = AaltoDatabase.getInstance(appContext)
                StationCatalogRepository(
                    source = RadioBrowserCatalogSource(),
                    cache = RoomStationCatalogCache(database.catalogStationDao())
                ).also { catalogRepository = it }
            }
        }
    }
}
