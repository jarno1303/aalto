package fi.aalto.radio.audio

import android.content.Context
import kotlin.math.pow

/** Equalizer settings, as the user left them. */
internal data class EqualizerSettings(
    val enabled: Boolean = false,
    val preset: Int = PRESET_CUSTOM,
    /** One level per band in millibels, device order. Empty until touched. */
    val bandLevels: List<Int> = emptyList()
) {
    companion object {
        const val PRESET_CUSTOM = -1
    }
}

/**
 * Remembers how the user wants Aalto to sound: the equalizer, and how loud
 * each station should be relative to the others.
 *
 * Station gain exists because internet radio is mastered wildly differently —
 * switching from a quiet public broadcaster to a loud commercial station is
 * the one thing every listener notices every day.
 */
internal object AudioSettings {

    private const val PREFS = "aalto_audio"
    private const val KEY_EQ_ENABLED = "eq_enabled"
    private const val KEY_EQ_PRESET = "eq_preset"
    private const val KEY_EQ_BANDS = "eq_bands"
    private const val KEY_GAIN_PREFIX = "gain_"

    /** How far a station can be nudged, in decibels. */
    const val MIN_GAIN_DB = -8
    const val MAX_GAIN_DB = 8

    fun equalizer(context: Context): EqualizerSettings {
        val prefs = prefs(context)
        return EqualizerSettings(
            enabled = prefs.getBoolean(KEY_EQ_ENABLED, false),
            preset = prefs.getInt(KEY_EQ_PRESET, EqualizerSettings.PRESET_CUSTOM),
            bandLevels = prefs.getString(KEY_EQ_BANDS, null)
                ?.split(',')
                ?.mapNotNull { it.trim().toIntOrNull() }
                .orEmpty()
        )
    }

    fun saveEqualizer(context: Context, settings: EqualizerSettings) {
        prefs(context).edit()
            .putBoolean(KEY_EQ_ENABLED, settings.enabled)
            .putInt(KEY_EQ_PRESET, settings.preset)
            .putString(KEY_EQ_BANDS, settings.bandLevels.joinToString(","))
            .apply()
    }

    /** Extra decibels for one station; 0 means "as broadcast". */
    fun stationGainDb(context: Context, stationId: String?): Int {
        if (stationId.isNullOrBlank()) return 0
        return prefs(context).getInt(KEY_GAIN_PREFIX + stationId, 0)
            .coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
    }

    fun saveStationGainDb(context: Context, stationId: String?, gainDb: Int) {
        if (stationId.isNullOrBlank()) return
        val key = KEY_GAIN_PREFIX + stationId
        val editor = prefs(context).edit()
        if (gainDb == 0) editor.remove(key) else editor.putInt(key, gainDb.coerceIn(MIN_GAIN_DB, MAX_GAIN_DB))
        editor.apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** Decibels as a volume multiplier: -6 dB is half as loud in amplitude. */
internal fun decibelsToVolume(db: Int): Float =
    10f.pow(db.coerceAtMost(0) / 20f).coerceIn(0.1f, 1f)
