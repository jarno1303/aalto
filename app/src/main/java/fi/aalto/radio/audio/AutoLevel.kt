package fi.aalto.radio.audio

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import fi.aalto.radio.plus.Plus
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

/** A station's measured loudness and how many seconds it rests on. */
internal data class MeasuredLoudness(val lufs: Double, val seconds: Int)

/**
 * Automatic levelling: every station is measured while it plays and brought
 * to one reference loudness, so switching from a quiet public broadcaster to
 * a loud commercial station no longer means reaching for the volume.
 *
 * - Measuring always runs (it costs next to nothing), so switching the
 *   feature on works at once for stations already heard.
 * - Applying it is Aalto Plus (docs/AALTO_PLUS.md), checked here once.
 * - The user's own per-station adjustment is added on top and stays free.
 * - The level changes only when a station starts, plus once, gently, when a
 *   station heard for the first time has been measured: never mid-song.
 */
/** Where decoded audio goes to be measured. */
internal interface PcmSink {
    fun configure(sampleRate: Int, channels: Int)
    fun feed(buffer: ByteBuffer, encoding: Int)
}

/** Reads 16-bit or float PCM into [meter]. */
internal fun LoudnessMeter.addPcm(buffer: ByteBuffer, encoding: Int) {
    val data = buffer.duplicate().order(ByteOrder.nativeOrder())
    when (encoding) {
        C.ENCODING_PCM_16BIT -> while (data.remaining() >= 2) add(data.short / 32768.0)
        C.ENCODING_PCM_FLOAT -> while (data.remaining() >= 4) add(data.float.toDouble())
    }
}

internal object AutoLevel : PcmSink {
    const val REFERENCE_LUFS = -16.0
    const val MAX_BOOST_DB = 6.0
    const val MAX_CUT_DB = -12.0
    const val MIN_MEASURE_SECONDS = 20.0
    /** Older listening counts for at most this much, so the value can follow a station's changes. */
    const val MAX_HISTORY_SECONDS = 600

    private const val PREFS = "aalto_loudness"
    private const val KEY_LUFS = "lufs_"
    private const val KEY_SECONDS = "secs_"

    private val lock = Any()
    private var meter: LoudnessMeter? = null
    private var resetPending = true
    private var stationId: String? = null
    private var base: MeasuredLoudness? = null
    private var firstApplied = false

    /** A station chosen as the reference may move the target only this far. */
    const val MIN_TARGET_LUFS = -20.0
    const val MAX_TARGET_LUFS = -12.0

    /** Gain the target asks for: louder stations down, quiet ones up a little. */
    fun gainFor(lufs: Double, target: Double = REFERENCE_LUFS): Double =
        (target - lufs).coerceIn(MAX_CUT_DB, MAX_BOOST_DB)

    /**
     * The level stations are brought to: the default, or the loudness of the
     * station the user chose as the reference, kept within limits so a very
     * loud reference cannot force distorting boosts on quiet stations.
     */
    fun target(context: Context): Double =
        clampTarget(AudioSettings.referenceLufs(context)) ?: REFERENCE_LUFS

    fun clampTarget(lufs: Double?): Double? = lufs?.coerceIn(MIN_TARGET_LUFS, MAX_TARGET_LUFS)

    /** Two measurements combined by the time each rests on, averaged as power. */
    fun combine(old: MeasuredLoudness?, new: MeasuredLoudness): MeasuredLoudness {
        if (old == null || old.seconds <= 0) return new
        val oldWeight = min(old.seconds, MAX_HISTORY_SECONDS).toDouble()
        val newWeight = new.seconds.toDouble()
        val power = (LoudnessMeter.power(old.lufs) * oldWeight + LoudnessMeter.power(new.lufs) * newWeight) /
            (oldWeight + newWeight)
        return MeasuredLoudness(
            lufs = LoudnessMeter.loudness(power),
            seconds = min(old.seconds + new.seconds, MAX_HISTORY_SECONDS)
        )
    }

    fun enabled(context: Context): Boolean =
        AudioSettings.autoLevelOn(context) && Plus.access(context).isActive()

    /** Decibels the levelling adds for [stationId]; 0 when off or not yet measured. */
    fun gainDb(context: Context, stationId: String?): Double {
        if (stationId.isNullOrBlank() || !enabled(context)) return 0.0
        return measured(context, stationId)?.let { gainFor(it.lufs, target(context)) } ?: 0.0
    }

    fun measured(context: Context, stationId: String): MeasuredLoudness? {
        val prefs = prefs(context)
        if (!prefs.contains(KEY_LUFS + stationId)) return null
        return MeasuredLoudness(
            lufs = prefs.getFloat(KEY_LUFS + stationId, 0f).toDouble(),
            seconds = prefs.getInt(KEY_SECONDS + stationId, 0)
        )
    }

    /** A measurement made elsewhere (the background survey), combined with what is kept. */
    fun record(context: Context, stationId: String, value: MeasuredLoudness) {
        save(context, stationId, combine(measured(context, stationId), value))
    }

    /** The station the player's own meter is measuring now. */
    fun currentStationId(): String? = synchronized(lock) { stationId }

    /**
     * 0…1 while [id] is being measured by the player and has no value yet;
     * null otherwise. For the "measuring" bar.
     */
    fun progress(id: String?): Float? = synchronized(lock) {
        if (id == null || id != stationId || firstApplied) return@synchronized null
        val m = meter ?: return@synchronized 0f
        if (resetPending) return@synchronized 0f
        (m.gatedSeconds / MIN_MEASURE_SECONDS).toFloat().coerceIn(0f, 1f)
    }

    private fun save(context: Context, stationId: String, value: MeasuredLoudness) {
        prefs(context).edit()
            .putFloat(KEY_LUFS + stationId, value.lufs.toFloat())
            .putInt(KEY_SECONDS + stationId, value.seconds)
            .apply()
    }

    /** A new station started (not the same one reloaded). Main thread. */
    fun startStation(context: Context, id: String?) {
        val stored = id?.let { measured(context, it) }
        synchronized(lock) {
            stationId = id
            base = stored
            firstApplied = stored != null
            resetPending = true
        }
    }

    /**
     * Saves what has been measured so far. True when a station heard for the
     * first time has just been measured: the caller then applies its level.
     * Main thread, every few seconds while playing.
     */
    fun checkpoint(context: Context): Boolean {
        val id: String
        val session: MeasuredLoudness
        val firstNow: Boolean
        synchronized(lock) {
            id = stationId ?: return false
            val m = meter ?: return false
            if (resetPending) return false
            val seconds = m.gatedSeconds
            if (seconds < MIN_MEASURE_SECONDS) return false
            val lufs = m.integratedLufs() ?: return false
            session = MeasuredLoudness(lufs, seconds.toInt())
            firstNow = !firstApplied
            firstApplied = true
        }
        save(context, id, combine(base, session))
        return firstNow
    }

    // ---- Audio thread ------------------------------------------------------

    override fun configure(sampleRate: Int, channels: Int) {
        synchronized(lock) {
            val current = meter
            if (current == null || current.sampleRate != sampleRate || current.channels != channels) {
                meter = LoudnessMeter(sampleRate, channels)
            }
        }
    }

    override fun feed(buffer: ByteBuffer, encoding: Int) {
        synchronized(lock) {
            var m = meter ?: return
            if (resetPending) {
                m = LoudnessMeter(m.sampleRate, m.channels)
                meter = m
                resetPending = false
            }
            m.addPcm(buffer, encoding)
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/**
 * Listens to the decoded audio on its way to the speaker and hands it to
 * [AutoLevel]. Passes every byte through unchanged: it measures, never shapes.
 */
@androidx.annotation.OptIn(UnstableApi::class)
internal class LoudnessProcessor(private val sink: PcmSink = AutoLevel) : BaseAudioProcessor() {
    private var encoding = C.ENCODING_INVALID

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        encoding = inputAudioFormat.encoding
        if (encoding != C.ENCODING_PCM_16BIT && encoding != C.ENCODING_PCM_FLOAT) {
            // Not a format we read: stay out of the way entirely.
            return AudioProcessor.AudioFormat.NOT_SET
        }
        sink.configure(inputAudioFormat.sampleRate, inputAudioFormat.channelCount)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        runCatching { sink.feed(inputBuffer, encoding) }
        replaceOutputBuffer(remaining).put(inputBuffer).flip()
    }
}
