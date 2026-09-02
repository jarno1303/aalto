package fi.aalto.radio

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore

object AaltoAppContainer {
    @Volatile
    private var stationRepository: StationRepository? = null
    @Volatile
    private var syncCoordinator: AaltoSyncCoordinator? = null

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
        }
    }
}
