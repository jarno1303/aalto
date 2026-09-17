package fi.aalto.radio

import fi.aalto.radio.alarm.nextAlarmTime
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

class AlarmTimeTest {

    private val zone = ZoneId.of("Europe/Helsinki")

    // Wednesday 16.9.2026
    private fun at(day: Int, hour: Int, minute: Int) =
        ZonedDateTime.of(2026, 9, day, hour, minute, 0, 0, zone)

    @Test
    fun onceLaterToday() {
        assertEquals(at(16, 7, 0), nextAlarmTime(at(16, 6, 30), 7, 0, emptySet()))
    }

    @Test
    fun onceTomorrowWhenTimeHasPassed() {
        assertEquals(at(17, 7, 0), nextAlarmTime(at(16, 7, 0), 7, 0, emptySet()))
    }

    @Test
    fun weekdaysSkipWeekend() {
        val weekdays = setOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
        )
        // Friday 18.9. after the alarm -> Monday 21.9.
        assertEquals(at(21, 6, 45), nextAlarmTime(at(18, 8, 0), 6, 45, weekdays))
    }

    @Test
    fun singleDayNextWeek() {
        // Wednesday after the alarm, only Wednesdays -> next Wednesday.
        assertEquals(at(23, 7, 0), nextAlarmTime(at(16, 9, 0), 7, 0, setOf(DayOfWeek.WEDNESDAY)))
    }

    @Test
    fun daylightSavingEnd() {
        // DST ends 25.10.2026 in Finland; 7.00 still exists that day.
        val before = ZonedDateTime.of(2026, 10, 24, 23, 0, 0, 0, zone)
        val expected = ZonedDateTime.of(2026, 10, 25, 7, 0, 0, 0, zone)
        assertEquals(expected, nextAlarmTime(before, 7, 0, emptySet()))
    }
}
