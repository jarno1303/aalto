package fi.aalto.radio

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class LocalRadioDao {
    @Upsert
    abstract suspend fun upsertStations(stations: List<StationEntity>)

    @Upsert
    abstract suspend fun upsertStation(station: StationEntity)

    @Query("SELECT * FROM stations WHERE id IN (:stationIds)")
    abstract suspend fun stationsByIds(stationIds: List<String>): List<StationEntity>

    @Query("SELECT id FROM stations WHERE id = :stationId LIMIT 1")
    abstract suspend fun stationExists(stationId: String): String?

    @Query(
        """
        SELECT stationId
        FROM favorites
        WHERE isDeleted = 0
        ORDER BY sortOrder ASC, createdAt ASC, stationId ASC
        """
    )
    abstract fun observeFavoriteIds(): Flow<List<String>>

    @Query(
        """
        SELECT stationId
        FROM favorites
        WHERE isDeleted = 0
        ORDER BY sortOrder ASC, createdAt ASC, stationId ASC
        """
    )
    abstract suspend fun favoriteIds(): List<String>

    @Query("SELECT * FROM favorites WHERE stationId = :stationId LIMIT 1")
    abstract suspend fun favoriteById(stationId: String): FavoriteEntity?

    @Query("SELECT COUNT(*) FROM favorites WHERE stationId = :stationId AND isDeleted = 0")
    abstract suspend fun activeFavoriteCount(stationId: String): Int

    @Query("SELECT COUNT(*) FROM favorites WHERE stationId = :stationId")
    abstract suspend fun favoriteRowCount(stationId: String): Int

    @Upsert
    abstract suspend fun upsertFavorite(favorite: FavoriteEntity)

    @Query("SELECT COALESCE(MAX(sortOrder) + 1, 0) FROM favorites WHERE isDeleted = 0")
    abstract suspend fun nextActiveFavoriteSortOrder(): Long

    @Query("UPDATE favorites SET sortOrder = :sortOrder WHERE stationId = :stationId")
    abstract suspend fun updateFavoriteSortOrder(stationId: String, sortOrder: Long)

    @Query(
        """
        SELECT stationId
        FROM favorites
        WHERE isDeleted = 0
        ORDER BY sortOrder ASC, createdAt ASC, stationId ASC
        """
    )
    abstract suspend fun activeFavoriteIdsInOrder(): List<String>

    @Transaction
    open suspend fun addFavoriteForMigration(stationId: String, now: Long) {
        val existing = favoriteById(stationId)
        if (existing?.isDeleted == false) {
            return
        }

        upsertFavorite(
            FavoriteEntity(
                stationId = stationId,
                createdAt = existing?.createdAt ?: now,
                sortOrder = existing?.sortOrder ?: nextActiveFavoriteSortOrder(),
                isDeleted = false,
                deletedAt = null,
                updatedAt = now,
                logicalVersion = existing?.logicalVersion ?: 0L,
                modifiedByDeviceId = existing?.modifiedByDeviceId ?: LEGACY_DEVICE_ID
            )
        )
    }

    @Transaction
    open suspend fun addFavoriteAndEnqueueMutation(
        stationId: String,
        deviceId: String,
        mutationId: String,
        now: Long,
        payload: String? = null
    ): Boolean {
        val existing = favoriteById(stationId)
        if (existing?.isDeleted == false) {
            return false
        }

        val logicalVersion = nextLogicalVersion()
        val baseServerRevision = metadataLongValue(SyncMetadataEntity.LAST_APPLIED_SERVER_REVISION) ?: 0L
        upsertFavorite(
            FavoriteEntity(
                stationId = stationId,
                createdAt = existing?.createdAt ?: now,
                sortOrder = existing?.sortOrder ?: nextActiveFavoriteSortOrder(),
                isDeleted = false,
                deletedAt = null,
                updatedAt = now,
                logicalVersion = logicalVersion,
                modifiedByDeviceId = deviceId
            )
        )
        insertSyncMutation(
            SyncMutationEntity(
                mutationId = mutationId,
                entityType = SyncEntityType.FAVORITE.value,
                entityId = stationId,
                operation = SyncOperation.UPSERT_FAVORITE.value,
                payload = payload,
                deviceId = deviceId,
                logicalVersion = logicalVersion,
                baseServerRevision = baseServerRevision,
                createdAt = now,
                updatedAt = now,
                attemptCount = 0,
                nextAttemptAt = null,
                state = SyncMutationState.PENDING.value,
                lastError = null
            )
        )
        return true
    }

    @Transaction
    open suspend fun removeFavoriteAndEnqueueMutation(
        stationId: String,
        deviceId: String,
        mutationId: String,
        now: Long,
        payload: String? = null
    ): Boolean {
        val existing = favoriteById(stationId)
        if (existing?.isDeleted == true) {
            return false
        }

        val logicalVersion = nextLogicalVersion()
        val baseServerRevision = metadataLongValue(SyncMetadataEntity.LAST_APPLIED_SERVER_REVISION) ?: 0L
        upsertFavorite(
            FavoriteEntity(
                stationId = stationId,
                createdAt = existing?.createdAt ?: now,
                sortOrder = existing?.sortOrder ?: nextActiveFavoriteSortOrder(),
                isDeleted = true,
                deletedAt = now,
                updatedAt = now,
                logicalVersion = logicalVersion,
                modifiedByDeviceId = deviceId
            )
        )
        insertSyncMutation(
            SyncMutationEntity(
                mutationId = mutationId,
                entityType = SyncEntityType.FAVORITE.value,
                entityId = stationId,
                operation = SyncOperation.DELETE_FAVORITE.value,
                payload = payload,
                deviceId = deviceId,
                logicalVersion = logicalVersion,
                baseServerRevision = baseServerRevision,
                createdAt = now,
                updatedAt = now,
                attemptCount = 0,
                nextAttemptAt = null,
                state = SyncMutationState.PENDING.value,
                lastError = null
            )
        )
        return true
    }

    @Transaction
    open suspend fun reorderFavoritesAndEnqueueMutation(
        orderedStationIds: List<String>,
        deviceId: String,
        mutationId: String,
        now: Long
    ): Boolean {
        val activeIds = activeFavoriteIdsInOrder()
        val orderedActiveIds = normalizeFavoriteOrder(orderedStationIds, activeIds)
        if (orderedActiveIds == activeIds) {
            return false
        }

        val logicalVersion = nextLogicalVersion()
        val baseServerRevision = metadataLongValue(SyncMetadataEntity.LAST_APPLIED_SERVER_REVISION) ?: 0L
        orderedActiveIds.forEachIndexed { index, stationId ->
            updateFavoriteSortOrder(stationId, index.toLong())
        }
        upsertFavoriteOrderState(
            FavoriteOrderStateEntity(
                id = FavoriteOrderStateEntity.DEFAULT_ID,
                orderedStationIds = FavoriteOrderPayload.encode(orderedActiveIds),
                logicalVersion = logicalVersion,
                modifiedByDeviceId = deviceId,
                updatedAt = now
            )
        )
        insertSyncMutation(
            SyncMutationEntity(
                mutationId = mutationId,
                entityType = SyncEntityType.FAVORITE_ORDER.value,
                entityId = FavoriteOrderStateEntity.DEFAULT_ID,
                operation = SyncOperation.REORDER_FAVORITES.value,
                payload = FavoriteOrderPayload.encode(orderedActiveIds),
                deviceId = deviceId,
                logicalVersion = logicalVersion,
                baseServerRevision = baseServerRevision,
                createdAt = now,
                updatedAt = now,
                attemptCount = 0,
                nextAttemptAt = null,
                state = SyncMutationState.PENDING.value,
                lastError = null
            )
        )
        coalescePendingFavoriteOrderReordersForDevice(
            deviceId = deviceId,
            now = now,
            states = COALESCIBLE_REORDER_STATES,
            acknowledgedState = SyncMutationState.ACKNOWLEDGED.value
        )
        return true
    }

    @Query("SELECT * FROM station_gains WHERE stationId = :stationId LIMIT 1")
    abstract suspend fun stationGain(stationId: String): StationGainEntity?

    @Query("SELECT * FROM station_gains ORDER BY stationId ASC")
    abstract suspend fun allStationGains(): List<StationGainEntity>

    @Query("SELECT * FROM station_gains ORDER BY stationId ASC")
    abstract fun observeStationGains(): Flow<List<StationGainEntity>>

    @Upsert
    abstract suspend fun upsertStationGain(stationGain: StationGainEntity)

    /**
     * Saves how loud a station should be and queues it for Sync in the same
     * transaction. Returns false when nothing changed, so a reset of a station
     * that was never adjusted does not create Sync traffic.
     */
    @Transaction
    open suspend fun setStationGainAndEnqueueMutation(
        stationId: String,
        gainDb: Int,
        deviceId: String,
        mutationId: String,
        now: Long
    ): Boolean {
        val clamped = StationGainPayload.clamp(gainDb)
        val existing = stationGain(stationId)
        if (existing == null && clamped == 0) {
            return false
        }
        if (existing?.gainDb == clamped) {
            return false
        }

        val logicalVersion = nextLogicalVersion()
        val baseServerRevision = metadataLongValue(SyncMetadataEntity.LAST_APPLIED_SERVER_REVISION) ?: 0L
        upsertStationGain(
            StationGainEntity(
                stationId = stationId,
                gainDb = clamped,
                logicalVersion = logicalVersion,
                modifiedByDeviceId = deviceId,
                updatedAt = now
            )
        )
        insertSyncMutation(
            SyncMutationEntity(
                mutationId = mutationId,
                entityType = SyncEntityType.STATION_GAIN.value,
                entityId = stationId,
                operation = SyncOperation.SET_STATION_GAIN.value,
                payload = StationGainPayload.encode(clamped),
                deviceId = deviceId,
                logicalVersion = logicalVersion,
                baseServerRevision = baseServerRevision,
                createdAt = now,
                updatedAt = now,
                attemptCount = 0,
                nextAttemptAt = null,
                state = SyncMutationState.PENDING.value,
                lastError = null
            )
        )
        coalescePendingStationGainMutationsForDevice(
            stationId = stationId,
            deviceId = deviceId,
            now = now,
            states = COALESCIBLE_REORDER_STATES,
            acknowledgedState = SyncMutationState.ACKNOWLEDGED.value
        )
        return true
    }

    /**
     * Only the latest unsent value of a station matters: older ones from this
     * device are retired, the same way pending reorders are.
     */
    @Query(
        """
        UPDATE sync_mutations
        SET state = :acknowledgedState,
            updatedAt = :now,
            nextAttemptAt = NULL,
            lastError = NULL
        WHERE entityType = 'station_gain'
            AND entityId = :stationId
            AND operation = 'SET_STATION_GAIN'
            AND deviceId = :deviceId
            AND state IN (:states)
            AND logicalVersion < (
                SELECT MAX(logicalVersion)
                FROM sync_mutations
                WHERE entityType = 'station_gain'
                    AND entityId = :stationId
                    AND operation = 'SET_STATION_GAIN'
                    AND deviceId = :deviceId
                    AND state IN (:states)
            )
        """
    )
    abstract suspend fun coalescePendingStationGainMutationsForDevice(
        stationId: String,
        deviceId: String,
        now: Long,
        states: List<String>,
        acknowledgedState: String
    ): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertRecentStation(recentStation: RecentStationEntity)

    @Query(
        """
        SELECT stationId
        FROM recent_stations
        ORDER BY lastPlayedAt DESC, stationId ASC
        LIMIT :limit
        """
    )
    abstract suspend fun recentStationIds(limit: Int): List<String>

    @Query("SELECT COUNT(*) FROM recent_stations")
    abstract suspend fun recentStationCount(): Int

    @Query(
        """
        DELETE FROM recent_stations
        WHERE stationId NOT IN (
            SELECT stationId
            FROM recent_stations
            ORDER BY lastPlayedAt DESC, stationId ASC
            LIMIT :limit
        )
        """
    )
    abstract suspend fun pruneRecentStations(limit: Int)

    @Transaction
    open suspend fun recordRecentStation(stationId: String, lastPlayedAt: Long, limit: Int) {
        upsertRecentStation(
            RecentStationEntity(
                stationId = stationId,
                lastPlayedAt = lastPlayedAt
            )
        )
        pruneRecentStations(limit)
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertSyncMutation(syncMutation: SyncMutationEntity): Long

    @Query("SELECT COUNT(*) FROM sync_mutations WHERE entityType = :entityType AND entityId = :entityId AND operation = :operation")
    abstract suspend fun mutationCount(entityType: String, entityId: String, operation: String): Int

    @Query(
        """
        SELECT COUNT(*) FROM sync_mutations
        WHERE entityType = :entityType AND entityId = :entityId AND operation = :operation
            AND payload IS NOT NULL AND payload != ''
        """
    )
    abstract suspend fun mutationWithPayloadCount(entityType: String, entityId: String, operation: String): Int

    @Transaction
    open suspend fun enqueueBootstrapFavorite(
        stationId: String,
        payload: String,
        deviceId: String,
        mutationId: String,
        now: Long
    ): Boolean {
        // Once per favourite, counting only upserts that carried the station:
        // catalog favourites used to be sent without it, and the other device
        // could never show them. This sends them once more, now with the station.
        if (mutationWithPayloadCount(SyncEntityType.FAVORITE.value, stationId, SyncOperation.UPSERT_FAVORITE.value) > 0) return false
        val current = favoriteById(stationId) ?: return false
        val version = nextLogicalVersion()
        val baseServerRevision = metadataLongValue(SyncMetadataEntity.LAST_APPLIED_SERVER_REVISION) ?: 0L
        upsertFavorite(current.copy(logicalVersion = version, updatedAt = now, modifiedByDeviceId = deviceId))
        insertSyncMutation(
            SyncMutationEntity(
                mutationId = mutationId,
                entityType = SyncEntityType.FAVORITE.value,
                entityId = stationId,
                operation = SyncOperation.UPSERT_FAVORITE.value,
                payload = payload,
                deviceId = deviceId,
                logicalVersion = version,
                baseServerRevision = baseServerRevision,
                createdAt = now,
                updatedAt = now,
                attemptCount = 0,
                nextAttemptAt = null,
                state = SyncMutationState.PENDING.value,
                lastError = null
            )
        )
        return true
    }

    @Query("SELECT * FROM sync_mutations WHERE mutationId = :mutationId LIMIT 1")
    abstract suspend fun syncMutationById(mutationId: String): SyncMutationEntity?

    @Query(
        """
        SELECT *
        FROM sync_mutations
        ORDER BY createdAt ASC, logicalVersion ASC, mutationId ASC
        """
    )
    abstract suspend fun allSyncMutations(): List<SyncMutationEntity>

    @Query("SELECT COUNT(*) FROM sync_mutations WHERE state = :state")
    abstract suspend fun syncMutationCountByState(state: String): Int

    @Query(
        """
        SELECT *
        FROM sync_mutations
        WHERE state IN (:states)
            AND (nextAttemptAt IS NULL OR nextAttemptAt <= :now)
        ORDER BY logicalVersion ASC, createdAt ASC, mutationId ASC
        LIMIT :limit
        """
    )
    abstract suspend fun dueSyncMutations(
        states: List<String>,
        now: Long,
        limit: Int
    ): List<SyncMutationEntity>

    @Query(
        """
        UPDATE sync_mutations
        SET state = :acknowledgedState,
            updatedAt = :now,
            nextAttemptAt = NULL,
            lastError = NULL
        WHERE entityType = 'favorite_order'
            AND entityId = 'favorites'
            AND operation = 'REORDER_FAVORITES'
            AND deviceId = :deviceId
            AND state IN (:states)
            AND logicalVersion < (
                SELECT MAX(logicalVersion)
                FROM sync_mutations
                WHERE entityType = 'favorite_order'
                    AND entityId = 'favorites'
                    AND operation = 'REORDER_FAVORITES'
                    AND deviceId = :deviceId
                    AND state IN (:states)
            )
        """
    )
    abstract suspend fun coalescePendingFavoriteOrderReordersForDevice(
        deviceId: String,
        now: Long,
        states: List<String>,
        acknowledgedState: String
    ): Int

    @Transaction
    open suspend fun recoverLatestRejectedStaleFavoriteOrderReorderForDevice(
        deviceId: String,
        now: Long,
        rejectedStaleError: String,
        maxAttemptCount: Int,
        activeStates: List<String>,
        pendingState: String,
        permanentState: String
    ): Int {
        val latest = latestFavoriteOrderReorderForDevice(deviceId) ?: return 0
        if (activeFavoriteOrderReorderCountForDevice(deviceId, activeStates, latest.logicalVersion) > 0) {
            return 0
        }
        val latestOrderState = favoriteOrderState(FavoriteOrderStateEntity.DEFAULT_ID) ?: return 0
        val latestOrderPayload = latestOrderState.orderedStationIds
        if (
            latest.state != permanentState ||
            latest.lastError != rejectedStaleError ||
            latest.attemptCount > maxAttemptCount ||
            latest.payload != latestOrderPayload
        ) {
            return 0
        }

        resetSyncMutationForRetry(
            mutationId = latest.mutationId,
            state = pendingState,
            updatedAt = now
        )
        return 1
    }

    @Query(
        """
        SELECT COUNT(*)
        FROM sync_mutations
        WHERE entityType = 'favorite_order'
            AND entityId = 'favorites'
            AND operation = 'REORDER_FAVORITES'
            AND deviceId = :deviceId
            AND state IN (:states)
            AND logicalVersion >= :logicalVersion
        """
    )
    abstract suspend fun activeFavoriteOrderReorderCountForDevice(
        deviceId: String,
        states: List<String>,
        logicalVersion: Long
    ): Int

    @Query(
        """
        SELECT *
        FROM sync_mutations
        WHERE entityType = 'favorite_order'
            AND entityId = 'favorites'
            AND operation = 'REORDER_FAVORITES'
            AND deviceId = :deviceId
        ORDER BY logicalVersion DESC, createdAt DESC, mutationId DESC
        LIMIT 1
        """
    )
    abstract suspend fun latestFavoriteOrderReorderForDevice(deviceId: String): SyncMutationEntity?

    @Query(
        """
        UPDATE sync_mutations
        SET state = :state,
            updatedAt = :updatedAt,
            nextAttemptAt = NULL,
            lastError = NULL
        WHERE mutationId = :mutationId
        """
    )
    abstract suspend fun resetSyncMutationForRetry(
        mutationId: String,
        state: String,
        updatedAt: Long
    )

    @Query("SELECT COUNT(*) FROM sync_mutations WHERE state != :acknowledgedState")
    abstract suspend fun unacknowledgedMutationCount(acknowledgedState: String): Int

    @Query("SELECT COUNT(*) FROM sync_mutations")
    abstract suspend fun syncMutationCount(): Int

    @Query(
        """
        UPDATE sync_mutations
        SET state = :state, updatedAt = :updatedAt, lastError = NULL
        WHERE mutationId = :mutationId
        """
    )
    abstract suspend fun markSyncMutationState(
        mutationId: String,
        state: String,
        updatedAt: Long
    )

    @Query(
        """
        UPDATE sync_mutations
        SET state = :state,
            updatedAt = :updatedAt,
            attemptCount = attemptCount + 1,
            nextAttemptAt = :nextAttemptAt,
            lastError = :lastError
        WHERE mutationId = :mutationId
        """
    )
    abstract suspend fun markRetryableFailure(
        mutationId: String,
        state: String,
        updatedAt: Long,
        nextAttemptAt: Long,
        lastError: String
    )

    @Query(
        """
        UPDATE sync_mutations
        SET state = :state,
            updatedAt = :updatedAt,
            attemptCount = attemptCount + 1,
            nextAttemptAt = NULL,
            lastError = :lastError
        WHERE mutationId = :mutationId
        """
    )
    abstract suspend fun markPermanentFailure(
        mutationId: String,
        state: String,
        updatedAt: Long,
        lastError: String
    )

    @Query(
        """
        UPDATE sync_mutations
        SET state = :pendingState,
            updatedAt = :now,
            nextAttemptAt = NULL,
            lastError = NULL
        WHERE state = :inFlightState AND updatedAt <= :staleBefore
        """
    )
    abstract suspend fun recoverStaleInFlightMutations(
        inFlightState: String,
        pendingState: String,
        staleBefore: Long,
        now: Long
    )

    @Query("SELECT longValue FROM sync_metadata WHERE `key` = :key LIMIT 1")
    abstract suspend fun metadataLongValue(key: String): Long?

    @Query("SELECT stringValue FROM sync_metadata WHERE `key` = :key LIMIT 1")
    abstract suspend fun metadataStringValue(key: String): String?

    @Transaction
    open suspend fun bindFirebaseUidIfAbsent(uid: String): Boolean {
        val existing = metadataStringValue(SyncMetadataEntity.BOUND_FIREBASE_UID)
        if (existing != null) return existing == uid
        upsertSyncMetadata(
            SyncMetadataEntity(
                key = SyncMetadataEntity.BOUND_FIREBASE_UID,
                longValue = 0L,
                stringValue = uid
            )
        )
        return true
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertSyncMetadata(syncMetadata: SyncMetadataEntity)

    @Transaction
    open suspend fun applyRemoteMutationsAndAdvanceCursor(
        mutations: List<SyncMutationEntity>,
        now: Long,
        localDeviceId: String
    ): RemoteApplyBatchResult {
        var changed = 0
        var cursor = metadataLongValue(SyncMetadataEntity.LAST_APPLIED_SERVER_REVISION) ?: 0L
        val events = mutableListOf<RemoteApplyEvent>()
        mutations.sortedBy { it.serverRevision }.forEach { mutation ->
            val authoritativeRemote = mutation.serverRevision > cursor && mutation.serverRevision > 0L
            val event = applyRemoteMutation(
                mutation = mutation,
                now = now,
                localDeviceId = localDeviceId,
                authoritativeRemote = authoritativeRemote
            )
            events += event
            if (event.applied) changed += 1
            cursor = maxOf(cursor, mutation.serverRevision)
        }
        upsertSyncMetadata(
            SyncMetadataEntity(
                key = SyncMetadataEntity.LAST_APPLIED_SERVER_REVISION,
                longValue = cursor,
                stringValue = null
            )
        )
        return RemoteApplyBatchResult(
            appliedCount = changed,
            events = events
        )
    }

    @Transaction
    open suspend fun nextLogicalVersion(): Long {
        val next = (metadataLongValue(SyncMetadataEntity.LOCAL_LOGICAL_VERSION) ?: 0L) + 1L
        upsertSyncMetadata(
            SyncMetadataEntity(
                key = SyncMetadataEntity.LOCAL_LOGICAL_VERSION,
                longValue = next,
                stringValue = null
            )
        )
        return next
    }

    @Query("SELECT COUNT(*) FROM sync_applied_mutations WHERE mutationId = :mutationId")
    abstract suspend fun appliedMutationCount(mutationId: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertAppliedMutation(appliedMutation: AppliedSyncMutationEntity): Long

    @Query("SELECT * FROM favorite_order_state WHERE id = :id LIMIT 1")
    abstract suspend fun favoriteOrderState(id: String): FavoriteOrderStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertFavoriteOrderState(favoriteOrderState: FavoriteOrderStateEntity)

    @Transaction
    open suspend fun applyRemoteMutation(
        mutation: SyncMutationEntity,
        now: Long,
        localDeviceId: String,
        authoritativeRemote: Boolean = false
    ): RemoteApplyEvent {
        if (appliedMutationCount(mutation.mutationId) > 0) {
            return mutation.remoteApplyEvent(applied = false, reason = "already_applied")
        }

        val localOutboxMutation = syncMutationById(mutation.mutationId)
        if (localOutboxMutation?.deviceId == localDeviceId) {
            insertAppliedMutation(AppliedSyncMutationEntity(mutation.mutationId, now))
            return mutation.remoteApplyEvent(applied = false, reason = "local_echo")
        }

        val authoritativeOtherDeviceRemote = authoritativeRemote && mutation.deviceId != localDeviceId
        val result = when (mutation.operation) {
            SyncOperation.UPSERT_FAVORITE.value -> applyRemoteFavoriteUpsert(mutation, now, authoritativeOtherDeviceRemote)
            SyncOperation.DELETE_FAVORITE.value -> applyRemoteFavoriteDelete(mutation, now, authoritativeOtherDeviceRemote)
            SyncOperation.REORDER_FAVORITES.value -> applyRemoteFavoriteReorder(mutation, now, authoritativeOtherDeviceRemote)
            SyncOperation.SET_STATION_GAIN.value -> applyRemoteStationGain(mutation, now, authoritativeOtherDeviceRemote)
            else -> mutation.remoteApplyEvent(applied = false, reason = "unknown_operation")
        }

        insertAppliedMutation(AppliedSyncMutationEntity(mutation.mutationId, now))
        return result
    }

    private suspend fun applyRemoteFavoriteUpsert(
        mutation: SyncMutationEntity,
        now: Long,
        authoritativeRemote: Boolean
    ): RemoteApplyEvent {
        StationSnapshotPayload.decode(mutation.payload)?.let { snapshot ->
            upsertStation(snapshot.toEntity(now))
        }
        // A favourite needs its station row (foreign key). Without station data
        // and without the station here, skip it: inserting would throw, abort the
        // whole batch and stop Sync at this change for good.
        if (stationExists(mutation.entityId) == null) {
            return mutation.remoteApplyEvent(applied = false, reason = "missing_station")
        }
        val current = favoriteById(mutation.entityId)
        if (!authoritativeRemote && !SyncConflictPolicy.favoriteMutationWins(mutation, current)) {
            return mutation.remoteApplyEvent(applied = false, reason = "conflict_lost")
        }

        upsertFavorite(
            FavoriteEntity(
                stationId = mutation.entityId,
                createdAt = current?.createdAt ?: mutation.createdAt,
                sortOrder = current?.sortOrder ?: nextActiveFavoriteSortOrder(),
                isDeleted = false,
                deletedAt = null,
                updatedAt = now,
                logicalVersion = mutation.logicalVersion,
                modifiedByDeviceId = mutation.deviceId,
                serverRevision = mutation.serverRevision,
                baseServerRevision = mutation.baseServerRevision
            )
        )
        return mutation.remoteApplyEvent(applied = true, reason = "applied")
    }

    private suspend fun applyRemoteFavoriteDelete(
        mutation: SyncMutationEntity,
        now: Long,
        authoritativeRemote: Boolean
    ): RemoteApplyEvent {
        StationSnapshotPayload.decode(mutation.payload)?.let { snapshot ->
            if (stationExists(snapshot.stationId) == null) upsertStation(snapshot.toEntity(now))
        }
        // Nothing to delete on a device that never had the station; a tombstone
        // row would need the station row too (foreign key).
        if (stationExists(mutation.entityId) == null) {
            return mutation.remoteApplyEvent(applied = false, reason = "missing_station")
        }
        val current = favoriteById(mutation.entityId)
        if (!authoritativeRemote && !SyncConflictPolicy.favoriteMutationWins(mutation, current)) {
            return mutation.remoteApplyEvent(applied = false, reason = "conflict_lost")
        }

        upsertFavorite(
            FavoriteEntity(
                stationId = mutation.entityId,
                createdAt = current?.createdAt ?: mutation.createdAt,
                sortOrder = current?.sortOrder ?: nextActiveFavoriteSortOrder(),
                isDeleted = true,
                deletedAt = mutation.updatedAt,
                updatedAt = now,
                logicalVersion = mutation.logicalVersion,
                modifiedByDeviceId = mutation.deviceId,
                serverRevision = mutation.serverRevision,
                baseServerRevision = mutation.baseServerRevision
            )
        )
        return mutation.remoteApplyEvent(applied = true, reason = "applied")
    }

    private suspend fun applyRemoteFavoriteReorder(
        mutation: SyncMutationEntity,
        now: Long,
        authoritativeRemote: Boolean
    ): RemoteApplyEvent {
        val current = favoriteOrderState(FavoriteOrderStateEntity.DEFAULT_ID)
        if (!authoritativeRemote && !SyncConflictPolicy.orderMutationWins(mutation, current)) {
            return mutation.remoteApplyEvent(applied = false, reason = "conflict_lost")
        }

        val activeIds = activeFavoriteIdsInOrder()
        val orderedActiveIds = normalizeFavoriteOrder(
            orderedStationIds = FavoriteOrderPayload.decode(mutation.payload),
            activeStationIds = activeIds
        )
        orderedActiveIds.forEachIndexed { index, stationId ->
            updateFavoriteSortOrder(stationId, index.toLong())
        }
        upsertFavoriteOrderState(
            FavoriteOrderStateEntity(
                id = FavoriteOrderStateEntity.DEFAULT_ID,
                orderedStationIds = FavoriteOrderPayload.encode(orderedActiveIds),
                logicalVersion = mutation.logicalVersion,
                modifiedByDeviceId = mutation.deviceId,
                updatedAt = now
            )
        )
        return mutation.remoteApplyEvent(
            applied = orderedActiveIds != activeIds,
            reason = if (orderedActiveIds != activeIds) "applied" else "no_visible_change"
        )
    }

    private suspend fun applyRemoteStationGain(
        mutation: SyncMutationEntity,
        now: Long,
        authoritativeRemote: Boolean
    ): RemoteApplyEvent {
        val gainDb = StationGainPayload.decode(mutation.payload)
            ?: return mutation.remoteApplyEvent(applied = false, reason = "invalid_payload")
        val current = stationGain(mutation.entityId)
        if (!authoritativeRemote && !SyncConflictPolicy.stationGainMutationWins(mutation, current)) {
            return mutation.remoteApplyEvent(applied = false, reason = "conflict_lost")
        }

        upsertStationGain(
            StationGainEntity(
                stationId = mutation.entityId,
                gainDb = gainDb,
                logicalVersion = mutation.logicalVersion,
                modifiedByDeviceId = mutation.deviceId,
                updatedAt = now
            )
        )
        return mutation.remoteApplyEvent(
            applied = current?.gainDb != gainDb,
            reason = if (current?.gainDb != gainDb) "applied" else "no_visible_change"
        )
    }

    companion object {
        const val LEGACY_DEVICE_ID = "legacy"
        val COALESCIBLE_REORDER_STATES = listOf(
            SyncMutationState.PENDING.value,
            SyncMutationState.FAILED_RETRYABLE.value
        )

        fun normalizeFavoriteOrder(
            orderedStationIds: List<String>,
            activeStationIds: List<String>
        ): List<String> {
            val active = activeStationIds.toSet()
            val explicit = orderedStationIds
                .filter { it in active }
                .distinct()
            val missing = activeStationIds.filter { it !in explicit }
            return explicit + missing
        }
    }
}

private fun StationSnapshot.toEntity(now: Long): StationEntity {
    val stationName = name.ifBlank { stationId }
    return StationEntity(
        id = stationId,
        radioBrowserStationUuid = radioBrowserStationUuid,
        name = stationName,
        description = category.ifBlank { stationName },
        initials = stationName.take(4).uppercase(),
        logoColorArgb = 0xFF1769FF,
        streamUrl = streamUrl,
        preferredStreamUrl = preferredStreamUrl,
        lastKnownWorkingStreamUrl = lastKnownWorkingStreamUrl,
        faviconUrl = faviconUrl,
        countryCode = countryCode,
        tags = tags,
        category = category,
        streamHealthStatus = null,
        updatedAt = now
    )
}

private fun SyncMutationEntity.remoteApplyEvent(
    applied: Boolean,
    reason: String
): RemoteApplyEvent {
    return RemoteApplyEvent(
        mutationId = mutationId,
        operation = operation,
        serverRevision = serverRevision,
        deviceId = deviceId,
        applied = applied,
        reason = reason
    )
}
