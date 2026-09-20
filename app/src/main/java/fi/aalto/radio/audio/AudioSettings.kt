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
    private const val KEY_AUTO_LEVEL = "auto_level"
    private const val KEY_REFERENCE_LUFS = "reference_lufs"
    private const val KEY_REFERENCE_NAME = "reference_name"
    private const val KEY_OLD_GAINS_ASKED = "auto_level_old_gains_asked"

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

    /** Automatic levelling (Aalto Plus) is on unless the user turned it off. */
    fun autoLevelOn(context: Context): Boolean = prefs(context).getBoolean(KEY_AUTO_LEVEL, true)

    fun setAutoLevelOn(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_LEVEL, on).apply()
    }

    /**
     * The level every station is brought to, when the user chose a station
     * of their own as the reference ("Käytä tätä asemaa tasona"); null means
     * the default level.
     */
    fun referenceLufs(context: Context): Double? {
        val prefs = prefs(context)
        return if (prefs.contains(KEY_REFERENCE_LUFS)) prefs.getFloat(KEY_REFERENCE_LUFS, 0f).toDouble() else null
    }

    fun referenceName(context: Context): String? = prefs(context).getString(KEY_REFERENCE_NAME, null)

    fun setReference(context: Context, lufs: Double?, stationName: String?) {
        val editor = prefs(context).edit()
        if (lufs == null) {
            editor.remove(KEY_REFERENCE_LUFS).remove(KEY_REFERENCE_NAME)
        } else {
            editor.putFloat(KEY_REFERENCE_LUFS, lufs.toFloat()).putString(KEY_REFERENCE_NAME, stationName)
        }
        editor.apply()
    }

    /** Whether the user has been asked once about adjustments made before levelling. */
    fun oldGainsAsked(context: Context): Boolean = prefs(context).getBoolean(KEY_OLD_GAINS_ASKED, false)

    fun markOldGainsAsked(context: Context) {
        prefs(context).edit().putBoolean(KEY_OLD_GAINS_ASKED, true).apply()
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

    /** Every station the user has made louder or quieter, by station id. */
    fun stationGains(context: Context): Map<String, Int> {
        return prefs(context).all
            .filterKeys { it.startsWith(KEY_GAIN_PREFIX) }
            .mapNotNull { (key, value) ->
                val stationId = key.removePrefix(KEY_GAIN_PREFIX)
                val gainDb = (value as? Int)?.coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
                if (stationId.isBlank() || gainDb == null || gainDb == 0) null else stationId to gainDb
            }
            .toMap()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** Decibels as a volume multiplier, for any gain; boosts are the enhancer's job. */
internal fun decibelsToVolume(db: Double): Float =
    10.0.pow(db.coerceAtMost(0.0) / 20.0).toFloat().coerceIn(0.05f, 1f)

/** Decibels as a volume multiplier: -6 dB is half as loud in amplitude. */
internal fun decibelsToVolume(db: Int): Float =
    10f.pow(db.coerceAtMost(0) / 20f).coerceIn(0.1f, 1f)
