package fi.aalto.radio.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import fi.aalto.radio.PlaybackService
import fi.aalto.radio.R

/** Ringing state shared with the lock-screen AlarmActivity (same process). */
internal object AlarmRuntime {
    var ringing by mutableStateOf(false)
    var stationName by mutableStateOf("")
    var usingFallback by mutableStateOf(false)
}

/**
 * Plays the wake-up radio.
 *
 * Deliberately separate from PlaybackService (AGENTS.md protected path):
 * its own ExoPlayer on the alarm audio stream, a gentle volume ramp, and a
 * built-in alarm tone if the stream does not start. "Jatka kuuntelua" hands
 * the station over to the normal PlaybackService through a MediaController.
 */
class AlarmService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var player: ExoPlayer? = null
    private var fallbackPlayer: MediaPlayer? = null
    private var startedAt = 0L
    private var settings = AlarmSettings()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startAlarm()
            ACTION_SNOOZE -> {
                AlarmScheduler.scheduleSnooze(this, SNOOZE_MINUTES)
                stopAlarm()
            }
            ACTION_CONTINUE -> {
                val station = settings
                stopPlayers()
                continueInRadio(station)
                stopAlarm()
            }
            else -> stopAlarm()
        }
        return START_NOT_STICKY
    }

    private fun startAlarm() {
        settings = AlarmStore.load(this)
        AlarmRuntime.stationName = settings.stationName.orEmpty()
        AlarmRuntime.usingFallback = false
        AlarmRuntime.ringing = true

        // Must be in the foreground within a few seconds of the start request.
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                0
            }
        )

        if (player != null || fallbackPlayer != null) return
        startedAt = SystemClock.elapsedRealtime()

        val url = settings.streamUrl
        if (url.isNullOrBlank()) {
            startFallback()
        } else {
            startRadio(url)
            handler.postDelayed(::checkStarted, START_TIMEOUT_MS)
            handler.post(rampStep)
        }
        handler.postDelayed(::stopAlarm, AUTO_STOP_MS)
    }

    private fun startRadio(url: String) {
        val exo = ExoPlayer.Builder(this).build()
        exo.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_ALARM)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true
        )
        exo.setWakeMode(C.WAKE_MODE_NETWORK)
        exo.volume = START_VOLUME
        exo.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                startFallback()
            }
        })
        exo.setMediaItem(MediaItem.fromUri(url))
        exo.prepare()
        exo.play()
        player = exo
    }

    private fun checkStarted() {
        if (player?.isPlaying != true) startFallback()
    }

    private val rampStep = object : Runnable {
        override fun run() {
            val exo = player ?: return
            val elapsed = SystemClock.elapsedRealtime() - startedAt
            val volume = (START_VOLUME + (1f - START_VOLUME) * elapsed / RAMP_MS.toFloat())
                .coerceIn(START_VOLUME, 1f)
            exo.volume = volume
            if (volume < 1f) handler.postDelayed(this, 1_000L)
        }
    }

    /** Built-in alarm tone: the alarm must never stay silent. */
    private fun startFallback() {
        if (fallbackPlayer != null) return
        player?.release()
        player = null
        AlarmRuntime.usingFallback = true

        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: return
        runCatching {
            fallbackPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@AlarmService, uri)
                isLooping = true
                prepare()
                start()
            }
        }
    }

    private fun stopPlayers() {
        handler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
        fallbackPlayer?.let { runCatching { it.stop() }; it.release() }
        fallbackPlayer = null
    }

    private fun stopAlarm() {
        stopPlayers()
        AlarmRuntime.ringing = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopPlayers()
        AlarmRuntime.ringing = false
        super.onDestroy()
    }

    /** Hands the station to the normal player so it keeps playing as usual. */
    private fun continueInRadio(station: AlarmSettings) {
        val url = station.streamUrl ?: return
        val appContext = applicationContext
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        future.addListener(
            {
                runCatching {
                    val controller = future.get()
                    controller.setMediaItem(
                        MediaItem.Builder()
                            .setMediaId(station.stationId ?: url)
                            .setUri(url)
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(station.stationName ?: "Aalto Radio")
                                    .setArtist("Aalto")
                                    .build()
                            )
                            .build()
                    )
                    controller.prepare()
                    controller.play()
                }
                // Give the session a moment to take over before letting go.
                // (A fresh handler: this service's own callbacks are cleared when it stops.)
                Handler(Looper.getMainLooper())
                    .postDelayed({ MediaController.releaseFuture(future) }, 3_000L)
            },
            ContextCompat.getMainExecutor(appContext)
        )
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager != null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.alarm_channel),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setSound(null, null)
                enableVibration(false)
            }
            manager.createNotificationChannel(channel)
        }

        val fullScreen = PendingIntent.getActivity(
            this,
            0,
            Intent(this, AlarmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = getString(R.string.alarm_title)
        val text = settings.stationName ?: formatClock(settings.hour, settings.minute)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(fullScreen)
            .setFullScreenIntent(fullScreen, true)
            .addAction(0, getString(R.string.alarm_snooze), serviceIntent(this, ACTION_SNOOZE, 1))
            .addAction(0, getString(R.string.alarm_dismiss), serviceIntent(this, ACTION_DISMISS, 2))
            .addAction(0, getString(R.string.alarm_continue), serviceIntent(this, ACTION_CONTINUE, 3))
            .build()
    }

    companion object {
        const val ACTION_START = "fi.aalto.radio.alarm.START"
        const val ACTION_SNOOZE = "fi.aalto.radio.alarm.SNOOZE"
        const val ACTION_DISMISS = "fi.aalto.radio.alarm.DISMISS"
        const val ACTION_CONTINUE = "fi.aalto.radio.alarm.CONTINUE"

        const val SNOOZE_MINUTES = 9
        private const val CHANNEL_ID = "aalto_alarm"
        private const val NOTIFICATION_ID = 4101
        private const val START_TIMEOUT_MS = 15_000L
        private const val RAMP_MS = 45_000L
        private const val AUTO_STOP_MS = 30L * 60_000L
        private const val START_VOLUME = 0.08f

        internal fun serviceIntent(context: Context, action: String, requestCode: Int): PendingIntent =
            PendingIntent.getService(
                context,
                requestCode,
                Intent(context, AlarmService::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        internal fun send(context: Context, action: String) {
            context.startService(Intent(context, AlarmService::class.java).setAction(action))
        }
    }
}
