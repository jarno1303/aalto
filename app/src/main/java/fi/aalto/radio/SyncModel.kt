package fi.aalto.radio

import org.json.JSONObject
import java.util.UUID
import kotlin.math.min

enum class SyncEntityType(val value: String) {
    FAVORITE("favorite"),
    FAVORITE_ORDER("favorite_order"),
    STATION_GAIN("station_gain")
}

enum class SyncOperation(val value: String) {
    UPSERT_FAVORITE("UPSERT_FAVORITE"),
    DELETE_FAVORITE("DELETE_FAVORITE"),
    REORDER_FAVORITES("REORDER_FAVORITES"),
    SET_STATION_GAIN("SET_STATION_GAIN")
}

enum class SyncMutationState(val value: String) {
    PENDING("PENDING"),
    IN_FLIGHT("IN_FLIGHT"),
    ACKNOWLEDGED("ACKNOWLEDGED"),
    FAILED_RETRYABLE("FAILED_RETRYABLE"),
    FAILED_PERMANENT("FAILED_PERMANENT")
}

data class SyncMutation(
    val mutationId: String,
    val entityType: String,
    val entityId: String,
    val operation: String,
    val payload: String?,
    val deviceId: String,
    val logicalVersion: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val baseServerRevision: Long = 0L,
    val serverRevision: Long = 0L
)

data class RemoteApplyBatchResult(
    val appliedCount: Int,
    val events: List<RemoteApplyEvent>
)

data class RemoteApplyEvent(
    val mutationId: String,
    val operation: String,
    val serverRevision: Long,
    val deviceId: String,
    val applied: Boolean,
    val reason: String
)

data class StationSnapshot(
    val stationId: String,
    val radioBrowserStationUuid: String?,
    val name: String,
    val streamUrl: String,
    val preferredStreamUrl: String?,
    val lastKnownWorkingStreamUrl: String?,
    val faviconUrl: String?,
    val countryCode: String?,
    val tags: String?,
    val category: String
)

object StationSnapshotPayload {
    fun encode(station: RadioStation): String {
        return JSONObject().apply {
            put("stationId", station.id)
            put("radioBrowserStationUuid", station.radioBrowserStationUuid)
            put("name", station.name)
            put("streamUrl", station.streamUrl)
            put("preferredStreamUrl", station.preferredStreamUrl)
            put("lastKnownWorkingStreamUrl", station.lastKnownWorkingStreamUrl)
            put("faviconUrl", station.faviconUrl)
            put("countryCode", station.countryCode)
            put("tags", station.tags.joinToString(","))
            put("category", station.category)
        }.toString()
    }

    fun decode(payload: String?): StationSnapshot? = runCatching {
        val json = JSONObject(payload ?: return null)
        StationSnapshot(
            stationId = json.getString("stationId"),
            radioBrowserStationUuid = json.optString("radioBrowserStationUuid").ifBlank { null },
            name = json.getString("name"),
            streamUrl = json.getString("streamUrl"),
            preferredStreamUrl = json.optString("preferredStreamUrl").ifBlank { null },
            lastKnownWorkingStreamUrl = json.optString("lastKnownWorkingStreamUrl").ifBlank { null },
            faviconUrl = json.optString("faviconUrl").ifBlank { null },
            countryCode = json.optString("countryCode").ifBlank { null },
            tags = json.optString("tags").ifBlank { null },
            category = json.optString("category")
        )
    }.getOrNull()
}

sealed class SyncTransportSendResult {
    data class Success(
        val serverRevision: Long? = null,
        val rebasedFromRevision: Long? = null
    ) : SyncTransportSendResult()
    data class RetryableFailure(val message: String) : SyncTransportSendResult()
    data class PermanentFailure(
        val message: String,
        val serverRevision: Long? = null
    ) : SyncTransportSendResult()
}

interface RemoteSyncTransport {
    suspend fun send(mutation: SyncMutation): SyncTransportSendResult
    suspend fun receive(deviceId: String): List<SyncMutation>
}

object SyncMutationIds {
    fun newId(): String {
        return UUID.randomUUID().toString()
    }
}

object FavoriteOrderPayload {
    private const val SEPARATOR = "|"

    fun encode(stationIds: List<String>): String {
        return stationIds.distinct().joinToString(separator = SEPARATOR)
    }

    fun decode(payload: String?): List<String> {
        return payload
            ?.split(SEPARATOR)
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            .orEmpty()
    }
}

/** A station's gain travels as its decibel value, e.g. "-4". */
object StationGainPayload {
    const val MIN_GAIN_DB = -8
    const val MAX_GAIN_DB = 8

    fun encode(gainDb: Int): String = clamp(gainDb).toString()

    fun decode(payload: String?): Int? = payload?.trim()?.toIntOrNull()?.let(::clamp)

    fun clamp(gainDb: Int): Int = gainDb.coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
}

object SyncConflictPolicy {
    fun favoriteMutationWins(
        incoming: SyncMutationEntity,
        current: FavoriteEntity?
    ): Boolean {
        if (current == null) {
            return true
        }

        if (incoming.logicalVersion != current.logicalVersion) {
            return incoming.logicalVersion > current.logicalVersion
        }

        val incomingDeletes = incoming.operation == SyncOperation.DELETE_FAVORITE.value
        if (incomingDeletes != current.isDeleted) {
            return incomingDeletes
        }

        return incoming.deviceId > current.modifiedByDeviceId
    }

    fun stationGainMutationWins(
        incoming: SyncMutationEntity,
        current: StationGainEntity?
    ): Boolean {
        if (current == null) {
            return true
        }

        if (incoming.logicalVersion != current.logicalVersion) {
            return incoming.logicalVersion > current.logicalVersion
        }

        return incoming.deviceId > current.modifiedByDeviceId
    }

    fun orderMutationWins(
        incoming: SyncMutationEntity,
        current: FavoriteOrderStateEntity?
    ): Boolean {
        if (current == null) {
            return true
        }

        if (incoming.logicalVersion != current.logicalVersion) {
            return incoming.logicalVersion > current.logicalVersion
        }

        return incoming.deviceId > current.modifiedByDeviceId
    }
}

object SyncRetryPolicy {
    private const val BASE_BACKOFF_MS = 1_000L
    private const val MAX_BACKOFF_MS = 60_000L

    fun nextAttemptAt(now: Long, attemptCount: Int): Long {
        val exponent = (attemptCount - 1).coerceAtLeast(0).coerceAtMost(6)
        val delay = min(MAX_BACKOFF_MS, BASE_BACKOFF_MS * (1L shl exponent))
        return now + delay
    }
}

fun SyncMutationEntity.toSyncMutation(): SyncMutation {
    return SyncMutation(
        mutationId = mutationId,
        entityType = entityType,
        entityId = entityId,
        operation = operation,
        payload = payload,
        deviceId = deviceId,
        logicalVersion = logicalVersion,
        createdAt = createdAt,
        updatedAt = updatedAt,
        baseServerRevision = baseServerRevision,
        serverRevision = serverRevision
    )
}

fun SyncMutation.toEntity(state: SyncMutationState = SyncMutationState.ACKNOWLEDGED): SyncMutationEntity {
    return SyncMutationEntity(
        mutationId = mutationId,
        entityType = entityType,
        entityId = entityId,
        operation = operation,
        payload = payload,
        deviceId = deviceId,
        logicalVersion = logicalVersion,
        createdAt = createdAt,
        updatedAt = updatedAt,
        attemptCount = 0,
        nextAttemptAt = null,
        state = state.value,
        lastError = null,
        baseServerRevision = baseServerRevision,
        serverRevision = serverRevision
    )
}
