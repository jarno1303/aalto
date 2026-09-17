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
 * Sleep timer: fades the radio out over half a minute and then stops it
 * (live radio has nothing to resume, and a stopped player also removes the
 * media notification).
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
            fadeOutAndStop(appContext)
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

    private fun fadeOutAndStop(context: Context) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            {
                val controller = runCatching { future.get() }.getOrNull()
                if (controller == null) {
                    endsAtElapsedMs = null
                    MediaController.releaseFuture(future)
                    return@addListener
                }
                job = scope.launch {
                    try {
                        val steps = FADE_STEPS
                        for (step in 1..steps) {
                            if (!controller.isPlaying) break
                            controller.volume = 1f - step.toFloat() / steps
                            delay(FADE_MS / steps)
                        }
                        controller.stop()
                    } finally {
                        // Next listening session starts at normal volume.
                        runCatching { controller.volume = 1f }
                        MediaController.releaseFuture(future)
                        endsAtElapsedMs = null
                    }
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    private const val FADE_MS = 30_000L
    private const val FADE_STEPS = 30
}
