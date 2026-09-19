package fi.aalto.radio

import java.util.ArrayDeque

class InMemoryRemoteSyncState {
    private val processedMutationIds = mutableMapOf<String, RemoteLedgerEntry>()
    private val mutationLog = mutableListOf<RemoteLedgerEntry>()
    private val favorites = mutableMapOf<String, RemoteFavoriteState>()
    private var favoriteOrderState: RemoteFavoriteOrderState? = null
    private val stationGains = mutableMapOf<String, RemoteStationGainState>()
    private var currentRevision = 0L

    @Synchronized
    fun apply(mutation: SyncMutation): SyncTransportSendResult {
        processedMutationIds[mutation.mutationId]?.let { existing ->
            return existing.toSendResult()
        }

        val rebaseRevision = sameDeviceRebaseRevision(mutation)
        val stale = changedSinceBase(mutation) && rebaseRevision == null
        val result = if (stale) RESULT_REJECTED_STALE else RESULT_APPLIED
        val committedRevision = if (stale) currentRevision else currentRevision + 1L
        val committed = mutation.copy(serverRevision = committedRevision)

        if (!stale) {
            currentRevision = committedRevision
            when (committed.operation) {
                SyncOperation.UPSERT_FAVORITE.value -> applyFavoriteUpsert(committed)
                SyncOperation.DELETE_FAVORITE.value -> applyFavoriteDelete(committed)
                SyncOperation.REORDER_FAVORITES.value -> applyFavoriteReorder(committed)
                SyncOperation.SET_STATION_GAIN.value -> applyStationGain(committed)
            }
        }

        val ledgerEntry = RemoteLedgerEntry(
            mutation = committed,
            result = result,
            rebasedFromRevision = rebaseRevision
        )
        processedMutationIds[mutation.mutationId] = ledgerEntry
        mutationLog += ledgerEntry
        return ledgerEntry.toSendResult()
    }

    @Synchronized
    fun mutations(): List<SyncMutation> {
        return mutationLog
            .filter { it.result == RESULT_APPLIED }
            .map { it.mutation }
    }

    @Synchronized
    fun activeFavoriteIds(): List<String> {
        val activeIds = favorites.values
            .filterNot { it.isDeleted }
            .map { it.stationId }
            .toSet()

        val ordered = favoriteOrderState
            ?.orderedStationIds
            ?.filter { it in activeIds }
            .orEmpty()
        val missing = activeIds
            .filter { it !in ordered }
            .sorted()
        return ordered + missing
    }

    @Synchronized
    fun seedFavoriteOrder(
        orderedStationIds: List<String>,
        revision: Long,
        deviceId: String = "seed",
        logicalVersion: Long = revision
    ) {
        currentRevision = maxOf(currentRevision, revision)
        orderedStationIds.forEach { stationId ->
            favorites[stationId] = RemoteFavoriteState(
                stationId = stationId,
                isDeleted = false,
                logicalVersion = logicalVersion,
                modifiedByDeviceId = deviceId,
                serverRevision = revision
            )
        }
        favoriteOrderState = RemoteFavoriteOrderState(
            orderedStationIds = orderedStationIds,
            logicalVersion = logicalVersion,
            modifiedByDeviceId = deviceId,
            serverRevision = revision
        )
    }

    @Synchronized
    fun currentServerRevision(): Long {
        return currentRevision
    }

    @Synchronized
    fun ledgerServerRevisions(): List<Long> {
        return mutationLog.map { it.mutation.serverRevision }
    }

    @Synchronized
    fun appliedMutationIds(): List<String> {
        return mutationLog
            .filter { it.result == RESULT_APPLIED }
            .map { it.mutation.mutationId }
    }

    @Synchronized
    fun resultFor(mutationId: String): String? {
        return processedMutationIds[mutationId]?.result
    }

    @Synchronized
    fun stationGains(): Map<String, Int> {
        return stationGains.mapValues { it.value.gainDb }
    }

    private fun applyStationGain(mutation: SyncMutation) {
        stationGains[mutation.entityId] = RemoteStationGainState(
            gainDb = StationGainPayload.decode(mutation.payload) ?: 0,
            logicalVersion = mutation.logicalVersion,
            modifiedByDeviceId = mutation.deviceId,
            serverRevision = mutation.serverRevision
        )
    }

    private fun applyFavoriteUpsert(mutation: SyncMutation) {
        favorites[mutation.entityId] = RemoteFavoriteState(
            stationId = mutation.entityId,
            isDeleted = false,
            logicalVersion = mutation.logicalVersion,
            modifiedByDeviceId = mutation.deviceId,
            serverRevision = mutation.serverRevision
        )
    }

    private fun applyFavoriteDelete(mutation: SyncMutation) {
        favorites[mutation.entityId] = RemoteFavoriteState(
            stationId = mutation.entityId,
            isDeleted = true,
            logicalVersion = mutation.logicalVersion,
            modifiedByDeviceId = mutation.deviceId,
            serverRevision = mutation.serverRevision
        )
    }

    private fun applyFavoriteReorder(mutation: SyncMutation) {
        favoriteOrderState = RemoteFavoriteOrderState(
            orderedStationIds = FavoriteOrderPayload.decode(mutation.payload),
            logicalVersion = mutation.logicalVersion,
            modifiedByDeviceId = mutation.deviceId,
            serverRevision = mutation.serverRevision
        )
    }

    private fun changedSinceBase(mutation: SyncMutation): Boolean {
        return when (mutation.entityType) {
            SyncEntityType.FAVORITE.value -> {
                val current = favorites[mutation.entityId]
                current != null && current.serverRevision > mutation.baseServerRevision
            }
            SyncEntityType.FAVORITE_ORDER.value -> {
                val current = favoriteOrderState
                current != null && current.serverRevision > mutation.baseServerRevision
            }
            SyncEntityType.STATION_GAIN.value -> {
                val current = stationGains[mutation.entityId]
                current != null && current.serverRevision > mutation.baseServerRevision
            }
            else -> false
        }
    }

    private fun sameDeviceRebaseRevision(mutation: SyncMutation): Long? {
        val current: RemoteEntityState? = when (mutation.entityType) {
            SyncEntityType.FAVORITE.value -> favorites[mutation.entityId]
            SyncEntityType.FAVORITE_ORDER.value -> favoriteOrderState
            SyncEntityType.STATION_GAIN.value -> stationGains[mutation.entityId]
            else -> null
        }
        return if (
            current != null &&
            current.serverRevision > mutation.baseServerRevision &&
            current.modifiedByDeviceId == mutation.deviceId &&
            mutation.logicalVersion > current.logicalVersion
        ) {
            current.serverRevision
        } else {
            null
        }
    }

    private interface RemoteEntityState {
        val logicalVersion: Long
        val modifiedByDeviceId: String
        val serverRevision: Long
    }

    private data class RemoteLedgerEntry(
        val mutation: SyncMutation,
        val result: String,
        val rebasedFromRevision: Long?
    ) {
        fun toSendResult(): SyncTransportSendResult {
            return if (result == RESULT_REJECTED_STALE) {
                SyncTransportSendResult.PermanentFailure(
                    message = result,
                    serverRevision = mutation.serverRevision
                )
            } else {
                SyncTransportSendResult.Success(
                    serverRevision = mutation.serverRevision,
                    rebasedFromRevision = rebasedFromRevision
                )
            }
        }
    }

    private data class RemoteFavoriteState(
        val stationId: String,
        val isDeleted: Boolean,
        override val logicalVersion: Long,
        override val modifiedByDeviceId: String,
        override val serverRevision: Long
    ) : RemoteEntityState

    private data class RemoteFavoriteOrderState(
        val orderedStationIds: List<String>,
        override val logicalVersion: Long,
        override val modifiedByDeviceId: String,
        override val serverRevision: Long
    ) : RemoteEntityState

    private data class RemoteStationGainState(
        val gainDb: Int,
        override val logicalVersion: Long,
        override val modifiedByDeviceId: String,
        override val serverRevision: Long
    ) : RemoteEntityState

    companion object {
        const val RESULT_APPLIED = "APPLIED"
        const val RESULT_REJECTED_STALE = "REJECTED_STALE"
    }
}

class FakeRemoteSyncTransport(
    private val state: InMemoryRemoteSyncState = InMemoryRemoteSyncState(),
    /**
     * When set, [receive] behaves like Firestore: only changes after the
     * device's cursor, oldest first, at most this many at a time.
     */
    private val pageSize: Int? = null,
    private val cursor: suspend () -> Long = { 0L }
) : RemoteSyncTransport {
    var receiveCalls: Int = 0
        private set

    private val queuedSendResults = ArrayDeque<SyncTransportSendResult>()
    private val queuedIncoming = mutableListOf<SyncMutation>()

    var throwOnSend: Boolean = false
    var throwOnReceive: Boolean = false

    override suspend fun send(mutation: SyncMutation): SyncTransportSendResult {
        if (throwOnSend) {
            error("transport unavailable")
        }

        val queued = queuedSendResults.pollFirst()
        if (queued != null && queued !is SyncTransportSendResult.Success) {
            return queued
        }

        return state.apply(mutation)
    }

    override suspend fun receive(deviceId: String): List<SyncMutation> {
        if (throwOnReceive) {
            error("transport unavailable")
        }

        receiveCalls += 1
        val all = state.mutations()
        val page = if (pageSize == null) {
            all
        } else {
            val after = cursor()
            all.filter { it.serverRevision > after }.sortedBy { it.serverRevision }.take(pageSize)
        }
        val incoming = page + queuedIncoming
        queuedIncoming.clear()
        return incoming
    }

    override fun hasMoreAfter(receivedCount: Int): Boolean =
        pageSize != null && receivedCount >= pageSize

    fun enqueueSendResult(result: SyncTransportSendResult) {
        queuedSendResults.add(result)
    }

    fun injectIncoming(mutation: SyncMutation) {
        state.apply(mutation)
    }

    fun queueIncoming(mutation: SyncMutation, count: Int = 1) {
        repeat(count) {
            queuedIncoming += mutation
        }
    }

    fun remoteStationGains(): Map<String, Int> {
        return state.stationGains()
    }

    fun activeRemoteFavoriteIds(): List<String> {
        return state.activeFavoriteIds()
    }

    fun seedRemoteFavoriteOrder(orderedStationIds: List<String>, revision: Long) {
        state.seedFavoriteOrder(orderedStationIds, revision)
    }

    fun currentRemoteRevision(): Long {
        return state.currentServerRevision()
    }

    fun remoteLedgerServerRevisions(): List<Long> {
        return state.ledgerServerRevisions()
    }

    fun appliedRemoteMutationIds(): List<String> {
        return state.appliedMutationIds()
    }

    fun remoteResultFor(mutationId: String): String? {
        return state.resultFor(mutationId)
    }
}
