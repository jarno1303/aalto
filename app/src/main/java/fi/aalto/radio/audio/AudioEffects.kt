package fi.aalto.radio.audio

import android.content.Context
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.util.Log
import androidx.media3.common.Player

/**
 * The equalizer and the per-station gain, attached to the player's audio
 * session.
 *
 * Deliberately outside the playback path (AGENTS.md): it never chooses what
 * plays or how it is fetched, it only shapes the sound of the session that is
 * already playing. Every call into the platform is wrapped, because audio
 * effects are the part of Android that fails differently on every device —
 * and a missing equalizer must never cost the user their radio.
 */
internal object AudioEffects {

    private const val TAG = "AALTO_AUDIO"

    private var equalizer: Equalizer? = null
    private var loudness: LoudnessEnhancer? = null
    private var player: Player? = null
    private var sessionId: Int = 0

    /** Bands the device offers, or an empty list when there is no equalizer. */
    val bands: List<BandInfo> get() = readBands()

    data class BandInfo(
        val index: Int,
        val centerHz: Int,
        val minLevelMb: Int,
        val maxLevelMb: Int,
        val levelMb: Int
    )

    /** True when this device gave us a working equalizer. */
    val available: Boolean get() = equalizer != null

    /**
     * Binds to the player's audio session. Called once when the service
     * creates the player, and again if the session id changes.
     */
    fun attach(context: Context, player: Player, audioSessionId: Int) {
        this.player = player
        if (audioSessionId == 0 || audioSessionId == sessionId) {
            applyStationGain(context, currentStationId())
            return
        }
        release()
        sessionId = audioSessionId
        runCatching { Equalizer(EFFECT_PRIORITY, audioSessionId) }
            .onSuccess { equalizer = it }
            .onFailure { Log.w(TAG, "no equalizer on this device", it) }
        runCatching { LoudnessEnhancer(audioSessionId) }
            .onSuccess { loudness = it }
            .onFailure { Log.w(TAG, "no loudness enhancer on this device", it) }
        applyEqualizer(AudioSettings.equalizer(context))
        applyStationGain(context, currentStationId())
    }

    fun release() {
        runCatching { equalizer?.release() }
        runCatching { loudness?.release() }
        equalizer = null
        loudness = null
        sessionId = 0
    }

    fun applyEqualizer(settings: EqualizerSettings) {
        val eq = equalizer ?: return
        runCatching {
            eq.enabled = settings.enabled
            if (!settings.enabled) return
            if (settings.preset >= 0 && settings.preset < eq.numberOfPresets) {
                eq.usePreset(settings.preset.toShort())
                return
            }
            settings.bandLevels.forEachIndexed { index, level ->
                if (index < eq.numberOfBands) {
                    eq.setBandLevel(index.toShort(), level.toShort())
                }
            }
        }.onFailure { Log.w(TAG, "equalizer not applied", it) }
    }

    fun presetNames(): List<String> {
        val eq = equalizer ?: return emptyList()
        return runCatching {
            (0 until eq.numberOfPresets).map { eq.getPresetName(it.toShort()) }
        }.getOrDefault(emptyList())
    }

    private val mainHandler by lazy { android.os.Handler(android.os.Looper.getMainLooper()) }
    private var ramp: Runnable? = null

    /**
     * Makes this station as loud as it should be: the automatic level (Aalto
     * Plus, [AutoLevel]) plus the user's own adjustment. Quieter is done with
     * the player's own volume, louder with the loudness enhancer, because the
     * player cannot go above its own level.
     *
     * [smooth] eases the change in over a few seconds; used when a station
     * heard for the first time has just been measured, mid-listen.
     */
    fun applyStationGain(context: Context, stationId: String?, smooth: Boolean = false) {
        val totalDb = totalGainDb(context, stationId)
        val target = decibelsToVolume(totalDb)
        ramp?.let(mainHandler::removeCallbacks)
        ramp = null
        val start = runCatching { player?.volume }.getOrNull()
        if (smooth && start != null && kotlin.math.abs(start - target) > 0.01f) {
            var step = 0
            val runnable = object : Runnable {
                override fun run() {
                    step++
                    val fraction = step / RAMP_STEPS.toFloat()
                    runCatching { player?.volume = start + (target - start) * fraction }
                    if (step < RAMP_STEPS) mainHandler.postDelayed(this, RAMP_STEP_MS) else ramp = null
                }
            }
            ramp = runnable
            mainHandler.post(runnable)
        } else {
            runCatching { player?.volume = target }
                .onFailure { Log.w(TAG, "volume not applied", it) }
        }
        runCatching {
            loudness?.let { enhancer ->
                val boostMb = (totalDb.coerceAtLeast(0.0) * 100).toInt()
                enhancer.setTargetGain(boostMb)
                enhancer.enabled = boostMb > 0
            }
        }.onFailure { Log.w(TAG, "loudness not applied", it) }
    }

    /** Automatic level plus the user's own adjustment, within safe limits. */
    fun totalGainDb(context: Context, stationId: String?): Double =
        (AutoLevel.gainDb(context, stationId) + AudioSettings.stationGainDb(context, stationId))
            .coerceIn(MIN_TOTAL_DB, MAX_TOTAL_DB)

    /**
     * Re-applies the gain if [stationId] is the station playing right now,
     * e.g. after Sync brought a new value from another device. Main thread.
     */
    fun refreshStationGain(context: Context, stationId: String) {
        if (currentStationId() == stationId) {
            applyStationGain(context, stationId)
        }
    }

    private fun currentStationId(): String? =
        runCatching { player?.currentMediaItem?.mediaId }.getOrNull()

    private fun readBands(): List<BandInfo> {
        val eq = equalizer ?: return emptyList()
        return runCatching {
            val range = eq.bandLevelRange
            (0 until eq.numberOfBands).map { index ->
                val band = index.toShort()
                BandInfo(
                    index = index,
                    centerHz = eq.getCenterFreq(band) / 1000,
                    minLevelMb = range[0].toInt(),
                    maxLevelMb = range[1].toInt(),
                    levelMb = eq.getBandLevel(band).toInt()
                )
            }
        }.getOrDefault(emptyList())
    }

    /** Above the system's own effects, below anything the user installs. */
    private const val EFFECT_PRIORITY = 100
    private const val RAMP_STEPS = 30
    private const val RAMP_STEP_MS = 100L
    private const val MIN_TOTAL_DB = -20.0
    /** Boosting further only makes the enhancer squash the sound. */
    private const val MAX_TOTAL_DB = 9.0
}
