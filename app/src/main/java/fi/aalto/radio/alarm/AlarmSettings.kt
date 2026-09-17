package fi.aalto.radio.alarm

import android.content.Context
import java.time.DayOfWeek
import java.time.ZonedDateTime

/**
 * Wake-up radio settings. The station's stream URL and name are stored with
 * the alarm, so the alarm works even if the catalog is not loaded at wake-up.
 */
internal data class AlarmSettings(
    val enabled: Boolean = false,
    val hour: Int = 7,
    val minute: Int = 0,
    /** Empty = rings once. */
    val days: Set<DayOfWeek> = emptySet(),
    val stationId: String? = null,
    val stationName: String? = null,
    val streamUrl: String? = null,
    val snoozeMinutes: Int = DEFAULT_SNOOZE_MINUTES
) {
    val hasStation: Boolean
        get() = !streamUrl.isNullOrBlank()
}

internal const val DEFAULT_SNOOZE_MINUTES = 9
internal val snoozeChoicesMinutes = listOf(5, 9, 10, 15)

internal object AlarmStore {
    private const val PREFS = "aalto_alarm"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_HOUR = "hour"
    private const val KEY_MINUTE = "minute"
    private const val KEY_DAYS = "days"
    private const val KEY_STATION_ID = "station_id"
    private const val KEY_STATION_NAME = "station_name"
    private const val KEY_STREAM_URL = "stream_url"
    private const val KEY_SNOOZE_AT = "snooze_at"
    private const val KEY_SNOOZE_MINUTES = "snooze_minutes"
    private const val KEY_SKIP_AT = "skip_at"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): AlarmSettings {
        val p = prefs(context)
        return AlarmSettings(
            enabled = p.getBoolean(KEY_ENABLED, false),
            hour = p.getInt(KEY_HOUR, 7),
            minute = p.getInt(KEY_MINUTE, 0),
            days = p.getStringSet(KEY_DAYS, emptySet())
                .orEmpty()
                .mapNotNull { runCatching { DayOfWeek.valueOf(it) }.getOrNull() }
                .toSet(),
            stationId = p.getString(KEY_STATION_ID, null),
            stationName = p.getString(KEY_STATION_NAME, null),
            streamUrl = p.getString(KEY_STREAM_URL, null),
            snoozeMinutes = p.getInt(KEY_SNOOZE_MINUTES, DEFAULT_SNOOZE_MINUTES)
                .takeIf { it in 1..60 } ?: DEFAULT_SNOOZE_MINUTES
        )
    }

    fun save(context: Context, settings: AlarmSettings) {
        prefs(context).edit()
            .putBoolean(KEY_ENABLED, settings.enabled)
            .putInt(KEY_HOUR, settings.hour)
            .putInt(KEY_MINUTE, settings.minute)
            .putStringSet(KEY_DAYS, settings.days.map { it.name }.toSet())
            .putString(KEY_STATION_ID, settings.stationId)
            .putString(KEY_STATION_NAME, settings.stationName)
            .putString(KEY_STREAM_URL, settings.streamUrl)
            .putInt(KEY_SNOOZE_MINUTES, settings.snoozeMinutes)
            .apply()
    }

    fun snoozeAt(context: Context): Long = prefs(context).getLong(KEY_SNOOZE_AT, 0L)

    fun setSnoozeAt(context: Context, epochMs: Long) {
        prefs(context).edit().putLong(KEY_SNOOZE_AT, epochMs).apply()
    }

    /** One occurrence of a repeating alarm the user chose to skip (epoch ms), or 0. */
    fun skipAt(context: Context): Long = prefs(context).getLong(KEY_SKIP_AT, 0L)

    fun setSkipAt(context: Context, epochMs: Long) {
        prefs(context).edit().putLong(KEY_SKIP_AT, epochMs).apply()
    }
}

/**
 * Next moment the alarm should ring, strictly after [now].
 * With no [days] the alarm rings at the next matching clock time.
 */
internal fun nextAlarmTime(
    now: ZonedDateTime,
    hour: Int,
    minute: Int,
    days: Set<DayOfWeek>
): ZonedDateTime {
    var candidate = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
    if (!candidate.isAfter(now)) candidate = candidate.plusDays(1)
    if (days.isEmpty()) return candidate
    repeat(7) {
        if (candidate.dayOfWeek in days) return candidate
        candidate = candidate.plusDays(1)
    }
    return candidate
}

internal val finnishDayShort = mapOf(
    DayOfWeek.MONDAY to "ma",
    DayOfWeek.TUESDAY to "ti",
    DayOfWeek.WEDNESDAY to "ke",
    DayOfWeek.THURSDAY to "to",
    DayOfWeek.FRIDAY to "pe",
    DayOfWeek.SATURDAY to "la",
    DayOfWeek.SUNDAY to "su"
)

internal fun formatClock(hour: Int, minute: Int): String = "%d.%02d".format(hour, minute)
