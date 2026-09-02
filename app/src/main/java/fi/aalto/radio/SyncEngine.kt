package fi.aalto.radio

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SyncOnceResult(
    val sent: Int,
    val appliedIncoming: Int,
    val retryableFailures: Int,
    val permanentFailures: Int
)

class SyncEngine(
    private val dao: LocalRadioDao,
    private val deviceId: String,
    private val transport: RemoteSyncTransport,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val batchSize: Int = DEFAULT_BATCH_SIZE,
    private val inFlightStaleAfterMs: Long = DEFAULT_IN_FLIGHT_STALE_AFTER_MS
) {
    suspend fun syncOnce(): SyncOnceResult {
        return withContext(ioDispatcher) {
            val now = clock()
            dao.recoverStaleInFlightMutations(
                inFlightState = SyncMutationState.IN_FLIGHT.value,
                pendingState = SyncMutationState.PENDING.value,
                staleBefore = now - inFlightStaleAfterMs,
                now = now
            )
            val recoveredRejectedReorder = dao.recoverLatestRejectedStaleFavoriteOrderReorderForDevice(
                deviceId = deviceId,
                now = now,
                rejectedStaleError = RESULT_REJECTED_STALE,
                maxAttemptCount = 1,
                activeStates = LocalRadioDao.COALESCIBLE_REORDER_STATES,
                pendingState = SyncMutationState.PENDING.value,
                permanentState = SyncMutationState.FAILED_PERMANENT.value
            )
            if (recoveredRejectedReorder > 0) {
                debugLog("mutation_recovered_rejected_stale operation=${SyncOperation.REORDER_FAVORITES.value} count=$recoveredRejectedReorder")
            }
            val coalescedReorders = dao.coalescePendingFavoriteOrderReordersForDevice(
                deviceId = deviceId,
                now = now,
                states = LocalRadioDao.COALESCIBLE_REORDER_STATES,
                acknowledgedState = SyncMutationState.ACKNOWLEDGED.value
            )
            if (coalescedReorders > 0) {
                debugLog("mutation_coalesced operation=${SyncOperation.REORDER_FAVORITES.value} count=$coalescedReorders")
            }

            var sent = 0
            var retryableFailures = 0
            var permanentFailures = 0

            val dueMutations = dao.dueSyncMutations(
                states = listOf(
                    SyncMutationState.PENDING.value,
                    SyncMutationState.FAILED_RETRYABLE.value
                ),
                now = now,
                limit = batchSize
            )
            debugLog("push_count count=${dueMutations.size}")

            dueMutations.forEach { mutation ->
                dao.markSyncMutationState(
                    mutationId = mutation.mutationId,
                    state = SyncMutationState.IN_FLIGHT.value,
                    updatedAt = clock()
                )

                debugLog(
                    "mutation_send id=${mutation.mutationId.abbreviated()} " +
                        "operation=${mutation.operation} baseRevision=${mutation.baseServerRevision}"
                )
                when (val result = sendMutation(mutation)) {
                    is SyncTransportSendResult.Success -> {
                        sent += 1
                        result.rebasedFromRevision?.let { rebasedFromRevision ->
                            debugLog(
                                "mutation_rebased id=${mutation.mutationId.abbreviated()} " +
                                    "operation=${mutation.operation} " +
                                    "oldBase=${mutation.baseServerRevision} newBase=$rebasedFromRevision"
                            )
                        }
                        dao.markSyncMutationState(
                            mutationId = mutation.mutationId,
                            state = SyncMutationState.ACKNOWLEDGED.value,
                            updatedAt = clock()
                        )
                        debugLog(
                            "mutation_ack id=${mutation.mutationId.abbreviated()} " +
                                "operation=${mutation.operation} " +
                                "serverRevision=${result.serverRevision ?: "unknown"}"
                        )
                    }

                    is SyncTransportSendResult.RetryableFailure -> {
                        retryableFailures += 1
                        debugLog(
                            "mutation_retryable id=${mutation.mutationId.abbreviated()} " +
                                "operation=${mutation.operation} message=${result.message}"
                        )
                        markRetryable(mutation, result.message)
                    }

                    is SyncTransportSendResult.PermanentFailure -> {
                        permanentFailures += 1
                        if (result.message == RESULT_REJECTED_STALE) {
                            debugLog(
                                "mutation_rejected_stale id=${mutation.mutationId.abbreviated()} " +
                                    "operation=${mutation.operation} " +
                                    "baseRevision=${mutation.baseServerRevision} " +
                                    "canonicalRevision=${result.serverRevision ?: "unknown"}"
                            )
                        } else {
                            debugLog(
                                "mutation_failed_permanent id=${mutation.mutationId.abbreviated()} " +
                                    "operation=${mutation.operation} message=${result.message}"
                            )
                        }
                        dao.markPermanentFailure(
                            mutationId = mutation.mutationId,
                            state = SyncMutationState.FAILED_PERMANENT.value,
                            updatedAt = clock(),
                            lastError = result.message
                        )
                    }
                }
            }

            val pullCursor = dao.metadataLongValue(SyncMetadataEntity.LAST_APPLIED_SERVER_REVISION) ?: 0L
            debugLog("pull_from_revision revision=$pullCursor")
            val incoming = runCatching {
                transport.receive(deviceId)
            }.getOrElse { error ->
                Log.w(TAG, "Remote receive failed", error)
                emptyList()
            }
            debugLog("pull_count count=${incoming.size}")
            incoming.forEach { mutation ->
                debugLog(
                    "incoming_received id=${mutation.mutationId.abbreviated()} " +
                        "operation=${mutation.operation} " +
                        "serverRevision=${mutation.serverRevision} " +
                        "device=${mutation.deviceId.abbreviated()}"
                )
            }

            val remoteApplyResult = dao.applyRemoteMutationsAndAdvanceCursor(
                mutations = incoming.map { it.toEntity() },
                now = clock(),
                localDeviceId = deviceId
            )
            remoteApplyResult.events.forEach { event ->
                if (event.applied) {
                    debugLog(
                        "incoming_applied id=${event.mutationId.abbreviated()} " +
                            "operation=${event.operation} " +
                            "serverRevision=${event.serverRevision}"
                    )
                } else {
                    debugLog(
                        "incoming_skipped id=${event.mutationId.abbreviated()} " +
                            "operation=${event.operation} " +
                            "serverRevision=${event.serverRevision} " +
                            "reason=${event.reason}"
                    )
                }
            }
            val appliedIncoming = remoteApplyResult.appliedCount

            SyncOnceResult(
                sent = sent,
                appliedIncoming = appliedIncoming,
                retryableFailures = retryableFailures,
                permanentFailures = permanentFailures
            ).also { result ->
                debugLog(
                    "sync_complete sent=${result.sent} appliedIncoming=${result.appliedIncoming} " +
                        "retryableFailures=${result.retryableFailures} " +
                        "permanentFailures=${result.permanentFailures}"
                )
            }
        }
    }

    private suspend fun sendMutation(mutation: SyncMutationEntity): SyncTransportSendResult {
        return runCatching {
            transport.send(mutation.toSyncMutation())
        }.getOrElse { error ->
            SyncTransportSendResult.RetryableFailure(
                error.message ?: error::class.java.simpleName
            )
        }
    }

    private suspend fun markRetryable(mutation: SyncMutationEntity, message: String) {
        val reloaded = dao.syncMutationById(mutation.mutationId) ?: mutation
        val nextAttemptCount = reloaded.attemptCount + 1
        val now = clock()
        dao.markRetryableFailure(
            mutationId = mutation.mutationId,
            state = SyncMutationState.FAILED_RETRYABLE.value,
            updatedAt = now,
            nextAttemptAt = SyncRetryPolicy.nextAttemptAt(now, nextAttemptCount),
            lastError = message
        )
    }

    companion object {
        private const val TAG = "AaltoSync"
        private const val RESULT_REJECTED_STALE = "REJECTED_STALE"
        private const val DEFAULT_BATCH_SIZE = 50
        private const val DEFAULT_IN_FLIGHT_STALE_AFTER_MS = 30_000L
    }

    private fun debugLog(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }
}

private fun String.abbreviated(maxLength: Int = 8): String {
    return if (length <= maxLength) this else take(maxLength)
}
