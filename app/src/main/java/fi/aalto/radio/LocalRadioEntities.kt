package fi.aalto.radio

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "stations",
    indices = [
        Index(value = ["radioBrowserStationUuid"])
    ]
)
data class StationEntity(
    @PrimaryKey val id: String,
    val radioBrowserStationUuid: String?,
    val name: String,
    val description: String,
    val initials: String,
    val logoColorArgb: Long,
    val streamUrl: String,
    val preferredStreamUrl: String?,
    val lastKnownWorkingStreamUrl: String?,
    val faviconUrl: String?,
    val countryCode: String?,
    val tags: String?,
    val category: String,
    val streamHealthStatus: String?,
    val updatedAt: Long
)

@Entity(
    tableName = "favorites",
    foreignKeys = [
        ForeignKey(
            entity = StationEntity::class,
            parentColumns = ["id"],
            childColumns = ["stationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["stationId"])
    ]
)
data class FavoriteEntity(
    @PrimaryKey val stationId: String,
    val createdAt: Long,
    val sortOrder: Long,
    val isDeleted: Boolean,
    val deletedAt: Long?,
    val updatedAt: Long,
    val logicalVersion: Long,
    val modifiedByDeviceId: String,
    val serverRevision: Long = 0L,
    val baseServerRevision: Long = 0L
)

@Entity(
    tableName = "recent_stations",
    foreignKeys = [
        ForeignKey(
            entity = StationEntity::class,
            parentColumns = ["id"],
            childColumns = ["stationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["lastPlayedAt"])
    ]
)
data class RecentStationEntity(
    @PrimaryKey val stationId: String,
    val lastPlayedAt: Long
)

@Entity(tableName = "favorite_order_state")
data class FavoriteOrderStateEntity(
    @PrimaryKey val id: String,
    val orderedStationIds: String,
    val logicalVersion: Long,
    val modifiedByDeviceId: String,
    val updatedAt: Long
) {
    companion object {
        const val DEFAULT_ID = "favorites"
    }
}

/**
 * How loud one station should be compared to the others, as Sync sees it.
 *
 * No foreign key to `stations`: the adjustment belongs to the station id and
 * must survive even when that station is not in the local station table
 * (a catalog station on the other device, for example). A reset keeps the row
 * with 0 dB so its logical version still orders later changes.
 */
@Entity(tableName = "station_gains")
data class StationGainEntity(
    @PrimaryKey val stationId: String,
    val gainDb: Int,
    val logicalVersion: Long,
    val modifiedByDeviceId: String,
    val updatedAt: Long
)

@Entity(
    tableName = "sync_mutations",
    indices = [
        Index(value = ["state", "nextAttemptAt"]),
        Index(value = ["entityType", "entityId"])
    ]
)
data class SyncMutationEntity(
    @PrimaryKey val mutationId: String,
    val entityType: String,
    val entityId: String,
    val operation: String,
    val payload: String?,
    val deviceId: String,
    val logicalVersion: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val attemptCount: Int,
    val nextAttemptAt: Long?,
    val state: String,
    val lastError: String?,
    val baseServerRevision: Long = 0L,
    val serverRevision: Long = 0L
)

@Entity(tableName = "sync_applied_mutations")
data class AppliedSyncMutationEntity(
    @PrimaryKey val mutationId: String,
    val appliedAt: Long
)

@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey val key: String,
    val longValue: Long,
    val stringValue: String?
) {
    companion object {
        const val LOCAL_LOGICAL_VERSION = "local_logical_version"
        const val LAST_APPLIED_SERVER_REVISION = "last_applied_server_revision"
        const val BOUND_FIREBASE_UID = "bound_firebase_uid"
    }
}
