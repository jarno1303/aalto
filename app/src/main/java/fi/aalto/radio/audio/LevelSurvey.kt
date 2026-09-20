package fi.aalto.radio.audio

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import fi.aalto.radio.RadioStation
import fi.aalto.radio.playback.StationMediaItems
import java.nio.ByteBuffer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** What the background measuring is doing, for the audio sheet's list. */
internal data class SurveyState(
    val running: Boolean = false,
    val currentId: String? = null,
    /** 0…1 for [currentId]. */
    val progress: Float = 0f,
    val waiting: List<String> = emptyList(),
    val failed: Set<String> = emptySet()
)

/**
 * Measures the user's own stations one after another in the background, so
 * levelling is ready for all of them without listening to each first.
 *
 * A second player, silent (volume 0) and without audio focus, so it never
 * interrupts or shows up anywhere: it only decodes a station for about
 * 20 seconds of audible sound and hands the sound to its own meter. The
 * listener's radio plays on untouched. Costs roughly 0.3 MB per station.
 */
@androidx.annotation.OptIn(UnstableApi::class)
internal object LevelSurvey {
    private const val TAG = "AALTO_AUDIO"
    /** A station that has not given enough sound by then is skipped. */
    private const val PER_STATION_TIMEOUT_MS = 60_000L
    private const val TICK_MS = 1_000L

    private val main = Handler(Looper.getMainLooper())
    private var player: ExoPlayer? = null
    private var appContext: Context? = null
    private var queue = ArrayDeque<RadioStation>()
    private var current: RadioStation? = null
    private var startedAtMs = 0L

    private val _state = MutableStateFlow(SurveyState())
    val state: StateFlow<SurveyState> = _state

    /** The survey's own meter; the player's meter keeps measuring what is heard. */
    private object Sink : PcmSink {
        private val lock = Any()
        var meter: LoudnessMeter? = null
            get() = synchronized(lock) { field }
            private set

        fun reset() = synchronized(lock) { meter = null }

        /** Seconds measured and the result so far, read safely beside the audio thread. */
        fun snapshot(): Pair<Double, Double?> = synchronized(lock) {
            val m = meter ?: return@synchronized 0.0 to null
            m.gatedSeconds to m.integratedLufs()
        }

        override fun configure(sampleRate: Int, channels: Int) {
            synchronized(lock) {
                val m = meter
                if (m == null || m.sampleRate != sampleRate || m.channels != channels) {
                    meter = LoudnessMeter(sampleRate, channels)
                }
            }
        }

        override fun feed(buffer: ByteBuffer, encoding: Int) {
            synchronized(lock) { meter?.addPcm(buffer, encoding) }
        }
    }

    /**
     * Measures every station in [stations] that has no value yet (skipping
     * the one playing, which the player measures itself). Safe to call again:
     * stations already queued or measured are left alone. Main thread.
     */
    fun start(context: Context, stations: List<RadioStation>) {
        if (!AutoLevel.enabled(context)) return
        appContext = context.applicationContext
        val known = (queue.map { it.id } + listOfNotNull(current?.id)).toSet()
        val failed = _state.value.failed
        val todo = stations
            .distinctBy { it.id }
            .filter { it.id !in known && it.id !in failed }
            .filter { AutoLevel.measured(context, it.id) == null }
        if (todo.isEmpty() && current == null) return
        queue.addAll(todo)
        if (current == null) next() else publish()
    }

    fun stop() {
        main.removeCallbacks(tick)
        runCatching { player?.release() }
        player = null
        current = null
        queue.clear()
        Sink.reset()
        _state.value = SurveyState(failed = _state.value.failed)
    }

    private fun next() {
        val context = appContext ?: return stop()
        main.removeCallbacks(tick)
        var station = queue.removeFirstOrNull()
        // The one being listened to is measured by the player itself.
        while (station != null && (station.id == AutoLevel.currentStationId() ||
                AutoLevel.measured(context, station.id) != null)
        ) {
            station = queue.removeFirstOrNull()
        }
        if (station == null) {
            Log.d(TAG, "survey done")
            stop()
            return
        }
        current = station
        startedAtMs = android.os.SystemClock.elapsedRealtime()
        Sink.reset()
        val exo = player ?: build(context).also { player = it }
        runCatching {
            exo.setMediaItem(StationMediaItems.build(context, station))
            exo.prepare()
            exo.play()
        }.onFailure { fail("start: $it") }
        publish()
        main.postDelayed(tick, TICK_MS)
    }

    private val tick = object : Runnable {
        override fun run() {
            val context = appContext ?: return
            val station = current ?: return
            if (!AutoLevel.enabled(context)) return stop()
            val (seconds, result) = Sink.snapshot()
            if (seconds >= AutoLevel.MIN_MEASURE_SECONDS) {
                result?.let { lufs ->
                    AutoLevel.record(context, station.id, MeasuredLoudness(lufs, seconds.toInt()))
                    Log.d(TAG, "survey: ${station.name} %.1f LUFS".format(lufs))
                }
                current = null
                next()
                return
            }
            if (android.os.SystemClock.elapsedRealtime() - startedAtMs > PER_STATION_TIMEOUT_MS) {
                fail("timeout")
                return
            }
            publish((seconds / AutoLevel.MIN_MEASURE_SECONDS).toFloat())
            main.postDelayed(this, TICK_MS)
        }
    }

    private fun fail(why: String) {
        val station = current ?: return
        Log.d(TAG, "survey: ${station.name} skipped ($why)")
        _state.value = _state.value.copy(failed = _state.value.failed + station.id)
        current = null
        next()
    }

    private fun publish(progress: Float = 0f) {
        _state.value = SurveyState(
            running = current != null,
            currentId = current?.id,
            progress = progress.coerceIn(0f, 1f),
            waiting = queue.map { it.id },
            failed = _state.value.failed
        )
    }

    private fun build(context: Context): ExoPlayer {
        val renderers = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioOutputPlaybackParams: Boolean
            ): AudioSink? = DefaultAudioSink.Builder(context)
                .setAudioProcessors(arrayOf<AudioProcessor>(LoudnessProcessor(Sink)))
                .build()
        }
        return ExoPlayer.Builder(context, renderers)
            // No audio focus: the listener's radio must not pause or duck.
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                false
            )
            .build()
            .apply {
                volume = 0f
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        main.post { fail(error.errorCodeName) }
                    }
                })
            }
    }
}
