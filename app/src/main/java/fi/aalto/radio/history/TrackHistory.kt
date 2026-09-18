package fi.aalto.radio.history

import android.content.Context
import fi.aalto.radio.AaltoDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A song as the history shows it. */
internal data class PlayedTrack(
    val id: Long,
    val stationId: String,
    val stationName: String,
    val title: String,
    val playedAt: Long
)

/**
 * "What was that song?" — the tracks stations announced while they played.
 *
 * Kept deliberately small: the newest [KEEP_ROWS] rows, nothing else. A longer
 * history, search and export are what Aalto Plus is for.
 */
internal object TrackHistory {

    private val writeLock = Mutex()

    /**
     * Stores one song. Silent about anything that is not worth keeping: an
     * empty or junk line, or the same song repeated (stations resend their
     * metadata every few seconds).
     */
    suspend fun record(
        context: Context,
        stationId: String,
        stationName: String?,
        line: String?
    ) {
        val title = TrackTitle.forHistory(line, stationName) ?: return
        val dao = AaltoDatabase.getInstance(context).playedTrackDao()
        writeLock.withLock {
            val previous = dao.latest()
            if (TrackTitle.isRepeat(previous?.stationId, previous?.title, stationId, title)) return
            dao.insert(
                PlayedTrackEntity(
                    stationId = stationId,
                    stationName = stationName.orEmpty(),
                    title = title,
                    playedAt = System.currentTimeMillis()
                )
            )
            dao.trimTo(KEEP_ROWS)
        }
    }

    fun observe(context: Context, limit: Int = KEEP_ROWS): Flow<List<PlayedTrack>> =
        AaltoDatabase.getInstance(context).playedTrackDao()
            .observeRecent(limit)
            .map { rows -> rows.map(::toTrack) }

    suspend fun clear(context: Context) {
        AaltoDatabase.getInstance(context).playedTrackDao().clear()
    }

    private fun toTrack(entity: PlayedTrackEntity) = PlayedTrack(
        id = entity.id,
        stationId = entity.stationId,
        stationName = entity.stationName,
        title = entity.title,
        playedAt = entity.playedAt
    )

    const val KEEP_ROWS = 300
}
