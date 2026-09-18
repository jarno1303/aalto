package fi.aalto.radio.history

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** One song, as the station announced it while it played. */
@Entity(tableName = "played_tracks", indices = [Index("playedAt")])
data class PlayedTrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val stationId: String,
    val stationName: String,
    val title: String,
    val playedAt: Long
)

@Dao
interface PlayedTrackDao {

    @Insert
    suspend fun insert(track: PlayedTrackEntity)

    @Query("SELECT * FROM played_tracks ORDER BY playedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<PlayedTrackEntity>>

    @Query("SELECT * FROM played_tracks ORDER BY playedAt DESC LIMIT 1")
    suspend fun latest(): PlayedTrackEntity?

    @Query("DELETE FROM played_tracks")
    suspend fun clear()

    /** Keeps the newest [keep] rows; the history is a convenience, not an archive. */
    @Query(
        """
        DELETE FROM played_tracks
        WHERE id NOT IN (SELECT id FROM played_tracks ORDER BY playedAt DESC LIMIT :keep)
        """
    )
    suspend fun trimTo(keep: Int)
}
