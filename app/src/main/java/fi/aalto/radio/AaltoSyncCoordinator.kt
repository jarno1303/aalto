package fi.aalto.radio

import android.app.Activity
import android.util.Log
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SyncUiState(
    val enabled: Boolean = false,
    val accountEmail: String? = null,
    val status: String = "Signed out",
    val lastSyncAt: Long? = null,
    val error: String? = null
)

class AaltoSyncCoordinator internal constructor(
    private val authSession: SyncAuthSession,
    private val dao: LocalRadioDao,
    private val deviceIdProvider: () -> String,
    private val liveRevisionListenerFactory: LiveRevisionListenerFactory,
    private val syncOnce: suspend (uid: String, deviceId: String) -> SyncOnceResult,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    constructor(
        authManager: AaltoAuthManager,
        dao: LocalRadioDao,
        deviceIdProvider: () -> String,
        firestore: FirebaseFirestore,
        scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        clock: () -> Long = { System.currentTimeMillis() }
    ) : this(
        authSession = FirebaseSyncAuthSession(authManager),
        dao = dao,
        deviceIdProvider = deviceIdProvider,
        liveRevisionListenerFactory = FirestoreLiveRevisionListenerFactory(firestore),
        syncOnce = { uid, deviceId ->
            val transport = FirestoreRemoteSyncTransport(firestore, { uid }, dao)
            SyncEngine(dao, deviceId, transport).syncOnce()
        },
        scope = scope,
        clock = clock
    )

    private val mutex = Mutex()
    private val liveRevisionMutex = Mutex()
    private var syncJob: Job? = null
    private var liveRevisionListener: LiveRevisionListenerRegistration? = null
    private var isForeground = false
    private var currentAccount: SyncAccount? = null
    private var syncRequestedWhileActive = false
    private var lastLiveRequestedRevision = 0L
    private val _state = MutableStateFlow(SyncUiState())
    val state: StateFlow<SyncUiState> = _state

    init {
        scope.launch {
            authSession.accounts.collect { account ->
                onAuthChanged(account)
            }
        }
    }

    fun requestSync() {
        if (currentAccount == null) return
        if (syncJob?.isActive == true) {
            syncRequestedWhileActive = true
            return
        }
        debugLog("sync_requested")
        syncJob = scope.launch {
            mutex.withLock {
                do {
                    syncRequestedWhileActive = false
                    syncOnceForCurrentAccount()
                } while (syncRequestedWhileActive)
            }
        }
    }

    fun onForeground() {
        isForeground = true
        startLiveRevisionListenerIfNeeded()
        requestSync()
    }

    fun onBackground() {
        isForeground = false
        stopLiveRevisionListener()
    }

    suspend fun signIn(activity: Activity): Result<Unit> = authSession.signIn(activity)

    fun signOut() {
        stopLiveRevisionListener()
        authSession.signOut()
    }

    private suspend fun syncOnceForCurrentAccount() {
        debugLog("sync_start")
        val account = currentAccount ?: return
        if (!dao.bindFirebaseUidIfAbsent(account.uid)) {
            _state.value = _state.value.copy(status = "Account mismatch", error = "This device's synced radio data is linked to another Aalto account.")
            return
        }
        val bootstrapDeviceId = deviceIdProvider()
        dao.favoriteIds().forEach { stationId ->
            // Built-in stations from the catalog in code; every other station
            // (Radio Browser, the user's own) from its stored row.
            val payload = StationCatalog.stationById(stationId)?.let(StationSnapshotPayload::encode)
                ?: dao.stationsByIds(listOf(stationId)).firstOrNull()?.let(StationSnapshotPayload::encode)
            if (payload != null) {
                dao.enqueueBootstrapFavorite(
                    stationId = stationId,
                    payload = payload,
                    deviceId = bootstrapDeviceId,
                    mutationId = SyncMutationIds.newId(),
                    now = clock()
                )
            }
        }
        _state.value = _state.value.copy(status = "Sync pending", error = null)
        val result = runCatching { syncOnce(account.uid, bootstrapDeviceId) }
        result.onSuccess { syncResult ->
            debugLog(
                "sync_complete sent=${syncResult.sent} " +
                    "appliedIncoming=${syncResult.appliedIncoming} " +
                    "retryableFailures=${syncResult.retryableFailures} " +
                    "permanentFailures=${syncResult.permanentFailures}"
            )
            _state.value = _state.value.copy(status = "Connected", lastSyncAt = clock(), error = null)
        }.onFailure { error ->
            Log.w(TAG, "sync_failure", error)
            _state.value = _state.value.copy(status = "Sync problem", error = error.message)
        }
    }

    private fun onAuthChanged(account: SyncAccount?) {
        val accountChanged = currentAccount?.uid != account?.uid
        if (accountChanged) {
            stopLiveRevisionListener()
            lastLiveRequestedRevision = 0L
        }
        currentAccount = account
        if (account == null) {
            debugLog("auth_signed_out")
            _state.value = SyncUiState()
            return
        }
        debugLog("auth_signed_in")
        _state.value = _state.value.copy(enabled = true, accountEmail = account.email, status = "Connected", error = null)
        if (isForeground) {
            startLiveRevisionListenerIfNeeded()
            if (accountChanged) requestSync()
        }
    }

    private fun startLiveRevisionListenerIfNeeded() {
        val account = currentAccount ?: return
        if (!isForeground || liveRevisionListener != null) return
        liveRevisionListener = liveRevisionListenerFactory.start(
            uid = account.uid,
            onRevision = ::onLiveRevisionSeen,
            onError = { error -> Log.w(TAG, "live_listener_revision_failed", error) }
        )
        debugLog("live_listener_started")
    }

    private fun stopLiveRevisionListener() {
        val listener = liveRevisionListener ?: return
        listener.remove()
        liveRevisionListener = null
        debugLog("live_listener_stopped")
    }

    private fun onLiveRevisionSeen(event: LiveRevisionEvent) {
        scope.launch {
            liveRevisionMutex.withLock {
                val localRevision = dao.metadataLongValue(SyncMetadataEntity.LAST_APPLIED_SERVER_REVISION) ?: 0L
                debugLog("live_revision_seen remote=${event.remoteRevision} local=$localRevision")
                if (event.remoteRevision <= localRevision) return@withLock
                if (event.modifiedByDeviceId == deviceIdProvider()) return@withLock
                if (event.remoteRevision <= lastLiveRequestedRevision) return@withLock
                lastLiveRequestedRevision = event.remoteRevision
                debugLog("live_sync_requested")
                requestSync()
            }
        }
    }

    fun close() {
        stopLiveRevisionListener()
        scope.cancel()
    }

    companion object { private const val TAG = "AaltoSync" }

    private fun debugLog(event: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, event)
    }
}

internal data class SyncAccount(
    val uid: String,
    val email: String?
)

internal data class LiveRevisionEvent(
    val remoteRevision: Long,
    val modifiedByDeviceId: String?
)

internal interface SyncAuthSession {
    val accounts: Flow<SyncAccount?>
    suspend fun signIn(activity: Activity): Result<Unit>
    fun signOut()
}

internal fun interface LiveRevisionListenerRegistration {
    fun remove()
}

internal fun interface LiveRevisionListenerFactory {
    fun start(
        uid: String,
        onRevision: (LiveRevisionEvent) -> Unit,
        onError: (Throwable) -> Unit
    ): LiveRevisionListenerRegistration
}

private class FirebaseSyncAuthSession(
    private val authManager: AaltoAuthManager
) : SyncAuthSession {
    override val accounts: Flow<SyncAccount?> = authManager.state
        .map { state -> state.user?.toSyncAccount() }
        .distinctUntilChanged()

    override suspend fun signIn(activity: Activity): Result<Unit> {
        return authManager.signIn(activity).map { Unit }
    }

    override fun signOut() {
        authManager.signOut()
    }
}

private class FirestoreLiveRevisionListenerFactory(
    private val firestore: FirebaseFirestore
) : LiveRevisionListenerFactory {
    override fun start(
        uid: String,
        onRevision: (LiveRevisionEvent) -> Unit,
        onError: (Throwable) -> Unit
    ): LiveRevisionListenerRegistration {
        val registration = firestore.collection("users").document(uid)
            .collection("sync_meta").document("state")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }
                onRevision(
                    LiveRevisionEvent(
                        remoteRevision = snapshot?.getLong("currentRevision") ?: 0L,
                        modifiedByDeviceId = snapshot?.getString("modifiedByDeviceId")
                    )
                )
            }
        return LiveRevisionListenerRegistration { registration.remove() }
    }
}

private fun FirebaseUser.toSyncAccount(): SyncAccount {
    return SyncAccount(uid = uid, email = email)
}
