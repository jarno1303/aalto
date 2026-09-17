package fi.aalto.radio

import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Sleep timer: pauses playback after the chosen time.
 *
 * Lives at process level (not in a screen), so it keeps running while the
 * screen is off and the activity is in the background. The pause goes to
 * PlaybackService through its own short-lived MediaController, so the timer
 * does not depend on any screen's player instance and does not touch the
 * protected playback path.
 */
internal object SleepTimer {

    val choicesMinutes = listOf(15, 30, 45, 60, 90)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    /** Elapsed-realtime moment when playback stops, or null when the timer is off. */
    var endsAtElapsedMs by mutableStateOf<Long?>(null)
        private set

    val isActive: Boolean
        get() = endsAtElapsedMs != null

    fun start(context: Context, minutes: Int) {
        val appContext = context.applicationContext
        job?.cancel()
        val endsAt = SystemClock.elapsedRealtime() + minutes * 60_000L
        endsAtElapsedMs = endsAt
        job = scope.launch {
            delay(endsAt - SystemClock.elapsedRealtime())
            pausePlayback(appContext)
            endsAtElapsedMs = null
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        endsAtElapsedMs = null
    }

    /** Whole minutes left, rounded up, or null when the timer is off. */
    fun remainingMinutes(nowElapsedMs: Long = SystemClock.elapsedRealtime()): Int? {
        val endsAt = endsAtElapsedMs ?: return null
        val left = (endsAt - nowElapsedMs).coerceAtLeast(0L)
        return ((left + 59_999L) / 60_000L).toInt()
    }

    private fun pausePlayback(context: Context) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            {
                runCatching { future.get().pause() }
                MediaController.releaseFuture(future)
            },
            ContextCompat.getMainExecutor(context)
        )
    }
}
