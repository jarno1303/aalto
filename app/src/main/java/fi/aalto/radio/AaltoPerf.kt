package fi.aalto.radio

import android.os.SystemClock
import android.util.Log
import androidx.media3.common.PlaybackException
import java.util.concurrent.atomic.AtomicLong

object AaltoPerf {
    private const val TAG = "AALTO_PERF"
    private val traceCounter = AtomicLong(0L)

    private var appStartNanos = 0L
    private var mainUiReadyLogged = false

    class StationTrace internal constructor(
        val traceId: Long,
        val stationId: String,
        val stationName: String,
        val startedAtNanos: Long,
        var isSwitch: Boolean = false,
        var commandIssuedAtNanos: Long = 0L,
        var readyAtNanos: Long = 0L,
        var playbackStartedAtNanos: Long = 0L
    )

    fun markAppStart() {
        if (!BuildConfig.DEBUG) return

        appStartNanos = now()
        mainUiReadyLogged = false
    }

    @Synchronized
    fun reportMainUiReady() {
        if (!BuildConfig.DEBUG || mainUiReadyLogged || appStartNanos == 0L) return

        mainUiReadyLogged = true
        Log.d(TAG, "app_start main_ui_ready=${elapsedMs(appStartNanos)}ms")
    }

    fun beginStationTap(station: RadioStation): StationTrace? {
        if (!BuildConfig.DEBUG) return null

        val trace = StationTrace(
            traceId = traceCounter.incrementAndGet(),
            stationId = station.id,
            stationName = station.name,
            startedAtNanos = now()
        )

        Log.d(
            TAG,
            "station_tap trace_id=${trace.traceId} station_id=${trace.stationId} station=${forLog(trace.stationName)}"
        )

        return trace
    }

    fun reportControllerPending(trace: StationTrace?) {
        if (!BuildConfig.DEBUG || trace == null) return

        Log.d(
            TAG,
            "station_start trace_id=${trace.traceId} station_id=${trace.stationId} controller_pending=${elapsedMs(trace.startedAtNanos)}ms"
        )
    }

    @Synchronized
    fun reportPlaybackCommandIssued(trace: StationTrace?, isSwitch: Boolean) {
        if (!BuildConfig.DEBUG || trace == null || trace.commandIssuedAtNanos != 0L) return

        trace.isSwitch = isSwitch
        trace.commandIssuedAtNanos = now()

        Log.d(
            TAG,
            "${eventName(trace)} trace_id=${trace.traceId} station_id=${trace.stationId} playback_command_issued ui_to_player=${elapsedMs(trace.startedAtNanos, trace.commandIssuedAtNanos)}ms"
        )
    }

    @Synchronized
    fun reportPlayerReady(trace: StationTrace?) {
        if (!BuildConfig.DEBUG || trace == null || trace.readyAtNanos != 0L) return

        trace.readyAtNanos = now()

        Log.d(
            TAG,
            "${eventName(trace)} trace_id=${trace.traceId} station_id=${trace.stationId} player_ready=${elapsedMs(trace.startedAtNanos, trace.readyAtNanos)}ms"
        )
    }

    @Synchronized
    fun reportPlaybackStarted(trace: StationTrace?) {
        if (!BuildConfig.DEBUG || trace == null || trace.playbackStartedAtNanos != 0L) return

        trace.playbackStartedAtNanos = now()

        val uiToPlayer = durationFromStart(trace.commandIssuedAtNanos, trace)
        val playerReady = durationFromStart(trace.readyAtNanos, trace)

        Log.d(
            TAG,
            "${eventName(trace)} trace_id=${trace.traceId} station_id=${trace.stationId} ui_to_player=$uiToPlayer player_ready=$playerReady playback_started=${elapsedMs(trace.startedAtNanos, trace.playbackStartedAtNanos)}ms"
        )
    }

    fun reportPlayerError(trace: StationTrace?, error: PlaybackException) {
        if (!BuildConfig.DEBUG) return

        val tracePart = if (trace == null) {
            "trace_id=none"
        } else {
            "trace_id=${trace.traceId} station_id=${trace.stationId} after=${elapsedMs(trace.startedAtNanos)}ms"
        }

        Log.d(TAG, "player_error $tracePart code=${error.errorCodeName}", error)
    }

    private fun eventName(trace: StationTrace): String {
        return if (trace.isSwitch) "station_switch" else "station_start"
    }

    private fun durationFromStart(nanos: Long, trace: StationTrace): String {
        return if (nanos == 0L) {
            "n/a"
        } else {
            "${elapsedMs(trace.startedAtNanos, nanos)}ms"
        }
    }

    private fun elapsedMs(startNanos: Long, endNanos: Long = now()): Long {
        return (endNanos - startNanos) / 1_000_000L
    }

    private fun now(): Long {
        return SystemClock.elapsedRealtimeNanos()
    }

    private fun forLog(value: String): String {
        return value.replace(Regex("\\s+"), "_")
    }
}
