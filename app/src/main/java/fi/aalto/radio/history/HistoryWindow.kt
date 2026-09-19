package fi.aalto.radio.history

import java.time.Instant
import java.time.ZoneId

/** What the history sheet shows, and how many older songs stay out of view. */
internal data class HistoryView(
    val visible: List<PlayedTrack>,
    val hiddenEarlier: Int
)

/**
 * Free: the songs of the current day. Plus: everything that is kept.
 *
 * Only the view is limited. Older songs stay stored either way, so nothing is
 * lost and they appear the moment Plus is on (docs/AALTO_PLUS.md).
 */
internal object HistoryWindow {

    fun view(
        tracks: List<PlayedTrack>,
        plusActive: Boolean,
        now: Long,
        zone: ZoneId
    ): HistoryView {
        if (plusActive) return HistoryView(visible = tracks, hiddenEarlier = 0)
        val startOfToday = Instant.ofEpochMilli(now)
            .atZone(zone)
            .toLocalDate()
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
        val (today, earlier) = tracks.partition { it.playedAt >= startOfToday }
        return HistoryView(visible = today, hiddenEarlier = earlier.size)
    }
}
