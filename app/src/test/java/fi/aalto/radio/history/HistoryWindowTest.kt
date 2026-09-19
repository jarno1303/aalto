package fi.aalto.radio.history

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryWindowTest {

    private val zone = ZoneId.of("Europe/Helsinki")

    private fun at(day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(2026, 9, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun track(id: Long, playedAt: Long) = PlayedTrack(
        id = id,
        stationId = "ylex",
        stationName = "YleX",
        title = "Song $id",
        playedAt = playedAt
    )

    private val tracks = listOf(
        track(4, at(19, 15)),
        track(3, at(19, 0, 5)),
        track(2, at(18, 23, 59)),
        track(1, at(17, 8))
    )

    @Test
    fun freeShowsOnlyTodayAndCountsTheRest() {
        val view = HistoryWindow.view(tracks, plusActive = false, now = at(19, 16), zone = zone)

        assertEquals(listOf(4L, 3L), view.visible.map { it.id })
        assertEquals(2, view.hiddenEarlier)
    }

    @Test
    fun plusShowsEverything() {
        val view = HistoryWindow.view(tracks, plusActive = true, now = at(19, 16), zone = zone)

        assertEquals(listOf(4L, 3L, 2L, 1L), view.visible.map { it.id })
        assertEquals(0, view.hiddenEarlier)
    }

    @Test
    fun justAfterMidnightTheNewDayStartsEmpty() {
        val view = HistoryWindow.view(tracks, plusActive = false, now = at(20, 0, 1), zone = zone)

        assertEquals(emptyList<Long>(), view.visible.map { it.id })
        assertEquals(4, view.hiddenEarlier)
    }
}
