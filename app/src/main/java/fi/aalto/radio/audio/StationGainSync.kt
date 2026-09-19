package fi.aalto.radio.audio

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import fi.aalto.radio.AaltoAppContainer
import fi.aalto.radio.DeviceIdentityStore
import fi.aalto.radio.StationGainEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Carries per-station gain between the user's devices through Aalto Sync.
 *
 * Playback keeps reading the gain from [AudioSettings] (SharedPreferences),
 * exactly as before: nothing on the playback path waits for Room or the
 * network. Room holds the synced copy, and this object keeps the two in step:
 *
 * - the user's own change is written to [AudioSettings] first (heard at
 *   once) and handed to Sync when the slider is let go;
 * - a change that arrived from another device is copied into
 *   [AudioSettings] and, if that station is playing, applied right away.
 *
 * Free, like the gain itself (docs/AALTO_PLUS.md): no entitlement check here.
 */
internal object StationGainSync {

    private const val TAG = "AALTO_AUDIO"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    @Volatile
    private var started = false

    /** Starts following remote changes. Safe to call more than once. */
    fun start(context: Context) {
        if (started) return
        started = true
        val appContext = context.applicationContext
        scope.launch {
            runCatching { follow(appContext) }
                .onFailure { Log.w(TAG, "station gain sync stopped", it) }
        }
    }

    /** The user let go of the slider, or reset the station. */
    fun onUserChanged(context: Context, stationId: String, gainDb: Int) {
        val appContext = context.applicationContext
        scope.launch {
            runCatching {
                val queued = AaltoAppContainer.stationRepository(appContext)
                    .setStationGain(stationId, gainDb)
                if (queued) AaltoAppContainer.syncCoordinator(appContext).requestSync()
            }.onFailure { Log.w(TAG, "station gain not queued for sync", it) }
        }
    }

    private suspend fun follow(context: Context) {
        val repository = AaltoAppContainer.stationRepository(context)
        val localDeviceId = DeviceIdentityStore(context).deviceId()

        val rows = repository.stationGains()
        val atStart = StationGainReconcile.atStart(
            rows = rows,
            localGains = AudioSettings.stationGains(context),
            localDeviceId = localDeviceId
        )
        atStart.toSync.forEach { (stationId, gainDb) -> repository.setStationGain(stationId, gainDb) }
        atStart.toApplyLocally.forEach { applyLocally(context, it) }

        var previous = rows.associateBy { it.stationId }
        repository.observeStationGains().collect { current ->
            StationGainReconcile.remoteChanges(previous, current, localDeviceId)
                .forEach { applyLocally(context, it) }
            previous = current.associateBy { it.stationId }
        }
    }

    private fun applyLocally(context: Context, row: StationGainEntity) {
        AudioSettings.saveStationGainDb(context, row.stationId, row.gainDb)
        mainHandler.post {
            runCatching { AudioEffects.refreshStationGain(context, row.stationId) }
                .onFailure { Log.w(TAG, "synced gain not applied", it) }
        }
    }
}

internal data class StationGainStartActions(
    /** Local values Sync has not seen yet: queue them. */
    val toSync: Map<String, Int>,
    /** Values another device set that this device has not taken into use. */
    val toApplyLocally: List<StationGainEntity>
)

/** Decisions only, no Android: unit-tested in StationGainReconcileTest. */
internal object StationGainReconcile {

    /**
     * Brings SharedPreferences and Room into agreement once per process.
     *
     * A value set on this device wins over its Room copy, because the local
     * write always lands in SharedPreferences first; the Room copy can only be
     * behind (the app was closed before it was queued). A value from another
     * device wins over SharedPreferences, because it is the newer intent.
     */
    fun atStart(
        rows: List<StationGainEntity>,
        localGains: Map<String, Int>,
        localDeviceId: String
    ): StationGainStartActions {
        val rowsById = rows.associateBy { it.stationId }
        val toSync = linkedMapOf<String, Int>()
        val toApply = mutableListOf<StationGainEntity>()

        localGains.forEach { (stationId, gainDb) ->
            if (stationId !in rowsById) toSync[stationId] = gainDb
        }
        rows.forEach { row ->
            val local = localGains[row.stationId] ?: 0
            if (local == row.gainDb) return@forEach
            if (row.modifiedByDeviceId == localDeviceId) {
                toSync[row.stationId] = local
            } else {
                toApply += row
            }
        }
        return StationGainStartActions(toSync = toSync, toApplyLocally = toApply)
    }

    /**
     * Rows that changed since the last look and were written by another
     * device. Changes this device made are already in SharedPreferences, and
     * comparing to the previous look (not to SharedPreferences) keeps an
     * unrelated update from undoing a slider the user is still moving.
     */
    fun remoteChanges(
        previous: Map<String, StationGainEntity>,
        current: List<StationGainEntity>,
        localDeviceId: String
    ): List<StationGainEntity> {
        return current.filter { row ->
            row.modifiedByDeviceId != localDeviceId && previous[row.stationId] != row
        }
    }
}
