package fi.aalto.radio

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.tasks.await

class FirestoreRemoteSyncTransport(
    private val firestore: FirebaseFirestore,
    private val uidProvider: () -> String?,
    private val dao: LocalRadioDao,
    private val pageSize: Long = 75L
) : RemoteSyncTransport {
    override suspend fun send(mutation: SyncMutation): SyncTransportSendResult {
        val uid = uidProvider() ?: return SyncTransportSendResult.PermanentFailure("not authenticated")
        return try {
            val result = firestore.runTransaction { transaction ->
                val root = userRoot(uid)
                val ledgerRef = root.collection("sync_mutations").document(mutation.mutationId)
                val existingLedger = transaction.get(ledgerRef)
                if (existingLedger.exists()) {
                    return@runTransaction FirestoreSendResult(
                        result = existingLedger.getString("result") ?: RESULT_APPLIED,
                        serverRevision = existingLedger.getLong("serverRevision"),
                        rebasedFromRevision = existingLedger.getLong("rebasedFromRevision")
                    )
                }

                val metaRef = root.collection("sync_meta").document("state")
                val favoriteRef = root.collection("favorites").document(mutation.entityId)
                val orderRef = root.collection("favorite_order").document("state")
                val meta = transaction.get(metaRef)
                val currentRevision = meta.getLong("currentRevision") ?: 0L
                val currentEntity = if (mutation.entityType == SyncEntityType.FAVORITE.value) {
                    transaction.get(favoriteRef)
                } else null
                val currentOrder = if (mutation.entityType == SyncEntityType.FAVORITE_ORDER.value) {
                    transaction.get(orderRef)
                } else null
                val currentEntityRevision = currentEntity?.getLong("serverRevision") ?: 0L
                val currentOrderRevision = currentOrder?.getLong("serverRevision") ?: 0L
                val rebaseRevision = if (mutation.entityType == SyncEntityType.FAVORITE.value) {
                    currentEntity?.sameDeviceRebaseRevision(
                        mutation = mutation,
                        currentRevision = currentEntityRevision
                    )
                } else {
                    currentOrder?.sameDeviceRebaseRevision(
                        mutation = mutation,
                        currentRevision = currentOrderRevision
                    )
                }
                val stale = if (mutation.entityType == SyncEntityType.FAVORITE.value) {
                    currentEntity?.exists() == true &&
                        currentEntityRevision > mutation.baseServerRevision &&
                        rebaseRevision == null
                } else {
                    currentOrder?.exists() == true &&
                        currentOrderRevision > mutation.baseServerRevision &&
                        rebaseRevision == null
                }
                val resultName = if (stale) RESULT_REJECTED_STALE else RESULT_APPLIED
                val committedRevision = if (stale) currentRevision else currentRevision + 1L

                if (!stale) {
                    when (mutation.operation) {
                        SyncOperation.UPSERT_FAVORITE.value,
                        SyncOperation.DELETE_FAVORITE.value -> {
                            val snapshot = StationSnapshotPayload.decode(mutation.payload)
                            val data = hashMapOf<String, Any?>(
                                "stationId" to mutation.entityId,
                                "isDeleted" to (mutation.operation == SyncOperation.DELETE_FAVORITE.value),
                                "serverRevision" to committedRevision,
                                "modifiedByDeviceId" to mutation.deviceId,
                                "clientLogicalVersion" to mutation.logicalVersion,
                                "stationSnapshot" to snapshot?.toMap()
                            )
                            transaction.set(favoriteRef, data)
                        }
                        SyncOperation.REORDER_FAVORITES.value -> {
                            // The order document is ordering intent only; favorite documents
                            // remain the source of existence and are never deleted here.
                            val order = FavoriteOrderPayload.decode(mutation.payload)
                            transaction.set(orderRef, hashMapOf<String, Any?>(
                                "orderedStationIds" to order,
                                "serverRevision" to committedRevision,
                                "modifiedByDeviceId" to mutation.deviceId,
                                "clientLogicalVersion" to mutation.logicalVersion
                            ))
                        }
                    }
                    transaction.set(metaRef, hashMapOf<String, Any?>(
                        "currentRevision" to committedRevision,
                        "modifiedByDeviceId" to mutation.deviceId,
                        "schemaVersion" to 1,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ))
                }

                transaction.set(ledgerRef, mutation.toFirestoreMap().apply {
                    put("result", resultName)
                    put("serverRevision", committedRevision)
                    if (rebaseRevision != null) put("rebasedFromRevision", rebaseRevision)
                    put("committedAt", FieldValue.serverTimestamp())
                })
                FirestoreSendResult(
                    result = resultName,
                    serverRevision = committedRevision,
                    rebasedFromRevision = rebaseRevision
                )
            }.await()
            if (result.result == RESULT_REJECTED_STALE) {
                SyncTransportSendResult.PermanentFailure(result.result, serverRevision = result.serverRevision)
            } else {
                SyncTransportSendResult.Success(
                    serverRevision = result.serverRevision,
                    rebasedFromRevision = result.rebasedFromRevision
                )
            }
        } catch (error: Exception) {
            if (error.isRetryableFirebaseFailure()) {
                SyncTransportSendResult.RetryableFailure(error.message ?: "temporary Firebase failure")
            } else {
                SyncTransportSendResult.PermanentFailure(error.message ?: "Firebase sync rejected")
            }
        }
    }

    override suspend fun receive(deviceId: String): List<SyncMutation> {
        val uid = uidProvider() ?: return emptyList()
        val cursor = dao.metadataLongValue(SyncMetadataEntity.LAST_APPLIED_SERVER_REVISION) ?: 0L
        val snapshot = userRoot(uid)
            .collection("sync_mutations")
            .whereGreaterThan("serverRevision", cursor)
            .orderBy("serverRevision")
            .limit(pageSize)
            .get()
            .await()
        return snapshot.documents.mapNotNull(::mutationFromDocument)
    }

    private fun userRoot(uid: String) = firestore.collection("users").document(uid)

    companion object {
        private const val RESULT_APPLIED = "APPLIED"
        private const val RESULT_REJECTED_STALE = "REJECTED_STALE"
    }
}

private fun SyncMutation.toFirestoreMap(): HashMap<String, Any?> = hashMapOf(
    "mutationId" to mutationId,
    "entityType" to entityType,
    "entityId" to entityId,
    "operation" to operation,
    "payload" to payload,
    "deviceId" to deviceId,
    "clientLogicalVersion" to logicalVersion,
    "baseServerRevision" to baseServerRevision,
    "clientCreatedAt" to createdAt,
    "updatedAt" to updatedAt
)

private data class FirestoreSendResult(
    val result: String,
    val serverRevision: Long?,
    val rebasedFromRevision: Long?
)

private fun com.google.firebase.firestore.DocumentSnapshot.sameDeviceRebaseRevision(
    mutation: SyncMutation,
    currentRevision: Long
): Long? {
    if (!exists() || currentRevision <= mutation.baseServerRevision) {
        return null
    }
    if (getString("modifiedByDeviceId") != mutation.deviceId) {
        return null
    }
    val currentClientLogicalVersion = getLong("clientLogicalVersion") ?: 0L
    return if (mutation.logicalVersion > currentClientLogicalVersion) currentRevision else null
}

private fun StationSnapshot.toMap(): Map<String, Any?> = mapOf(
    "stationId" to stationId,
    "radioBrowserStationUuid" to radioBrowserStationUuid,
    "name" to name,
    "streamUrl" to streamUrl,
    "preferredStreamUrl" to preferredStreamUrl,
    "lastKnownWorkingStreamUrl" to lastKnownWorkingStreamUrl,
    "faviconUrl" to faviconUrl,
    "countryCode" to countryCode,
    "tags" to tags,
    "category" to category
)

private fun mutationFromDocument(document: com.google.firebase.firestore.DocumentSnapshot): SyncMutation? {
    return runCatching {
        if ((document.getString("result") ?: "APPLIED") != "APPLIED") {
            return null
        }
        SyncMutation(
            mutationId = document.getString("mutationId") ?: document.id,
            entityType = document.getString("entityType") ?: return null,
            entityId = document.getString("entityId") ?: return null,
            operation = document.getString("operation") ?: return null,
            payload = document.getString("payload"),
            deviceId = document.getString("deviceId") ?: return null,
            logicalVersion = document.getLong("clientLogicalVersion") ?: 0L,
            createdAt = document.getLong("clientCreatedAt") ?: 0L,
            updatedAt = document.getLong("updatedAt") ?: 0L,
            baseServerRevision = document.getLong("baseServerRevision") ?: 0L,
            serverRevision = document.getLong("serverRevision") ?: 0L
        )
    }.getOrNull()
}

private fun Throwable.isRetryableFirebaseFailure(): Boolean {
    val code = (this as? FirebaseFirestoreException)?.code
    return code == FirebaseFirestoreException.Code.UNAVAILABLE ||
        code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ||
        code == FirebaseFirestoreException.Code.ABORTED ||
        code == FirebaseFirestoreException.Code.UNKNOWN
}
