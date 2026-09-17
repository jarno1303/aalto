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

/** Short on-device log of the last alarms, shown in the alarm dialog for troubleshooting. */
internal object AlarmLog {
    private const val PREFS = "aalto_alarm_log"
    private const val KEY = "lines"
    private const val MAX_LINES = 12

    fun add(context: Context, message: String) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val time = java.time.LocalDateTime.now()
        val stamp = "%d.%d. %d.%02d.%02d".format(
            time.dayOfMonth, time.monthValue, time.hour, time.minute, time.second
        )
        val lines = (prefs.getString(KEY, "").orEmpty().lines().filter { it.isNotBlank() } +
            "$stamp $message").takeLast(MAX_LINES)
        prefs.edit().putString(KEY, lines.joinToString("\n")).apply()
    }

    fun read(context: Context): List<String> =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "").orEmpty().lines().filter { it.isNotBlank() }
}

/**
 * "Jatka kuuntelua": the alarm stops and the normal app opens and plays the
 * alarm station through its usual path, so the app shows the right station.
 */
internal object AlarmHandoff {
    private const val ACTION_CONTINUE_IN_APP = "fi.aalto.radio.alarm.CONTINUE_IN_APP"

    var pending by mutableStateOf<AlarmSettings?>(null)

    fun continueIntent(context: Context): Intent =
        Intent(context, fi.aalto.radio.MainActivity::class.java)
            .setAction(ACTION_CONTINUE_IN_APP)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )

    /** Called by MainActivity for every incoming intent. */
    fun consume(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_CONTINUE_IN_APP) return
        intent.action = null
        if (AlarmRuntime.ringing) AlarmService.send(context, AlarmService.ACTION_DISMISS)
        pending = AlarmStore.load(context)
        AlarmLog.add(context, "jatka kuuntelua sovelluksessa")
    }
}

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
    private var radioStartedAt = 0L
    private var retries = 0
    private var settings = AlarmSettings()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startAlarm()
            ACTION_SNOOZE -> {
                AlarmLog.add(this, "torkku $SNOOZE_MINUTES min")
                AlarmScheduler.scheduleSnooze(this, SNOOZE_MINUTES)
                stopAlarm()
            }
            ACTION_CONTINUE -> {
                AlarmLog.add(this, "jatka kuuntelua")
                val station = settings
                stopPlayers()
                continueInRadio(station)
                stopAlarm()
            }
            ACTION_DISMISS -> {
                AlarmLog.add(this, "lopetettu")
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
        radioStartedAt = 0L
        retries = 0

        // The normal player does not give way by itself: pause it so only the
        // alarm station is heard.
        pauseNormalPlayback()

        val url = settings.streamUrl
        val host = url?.let { runCatching { android.net.Uri.parse(it).host }.getOrNull() }
        AlarmLog.add(this, "soi: ${settings.stationName ?: "-"} (${host ?: "ei osoitetta"})")
        if (url.isNullOrBlank()) {
            AlarmLog.add(this, "ei aseman osoitetta -> hälytysääni")
            startFallback()
        } else {
            startRadio(url)
            // The network can take a while to wake up with the phone. The tone
            // starts after a short wait, but the radio keeps trying and takes
            // over as soon as it plays.
            handler.postDelayed(::checkStarted, FALLBACK_AFTER_MS)
        }
        handler.postDelayed({
            AlarmLog.add(this, "hiljennetty automaattisesti")
            stopAlarm()
        }, AUTO_STOP_MS)
    }

    private fun startRadio(url: String) {
        player?.release()
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
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) onRadioPlaying()
            }

            override fun onPlayerError(error: PlaybackException) {
                onRadioError(url, error)
            }
        })
        exo.setMediaItem(MediaItem.fromUri(url))
        exo.prepare()
        exo.play()
        player = exo
    }

    private fun onRadioPlaying() {
        if (radioStartedAt != 0L) return
        radioStartedAt = SystemClock.elapsedRealtime()
        val seconds = (radioStartedAt - startedAt) / 1000
        AlarmLog.add(this, "radio soi ${seconds} s kohdalla")
        stopFallback()
        handler.post(rampStep)
    }

    private fun onRadioError(url: String, error: PlaybackException) {
        val elapsed = SystemClock.elapsedRealtime() - startedAt
        if (retries < 3) AlarmLog.add(this, "virhe: ${error.errorCodeName}, yritetään uudelleen")
        if (radioStartedAt == 0L && elapsed >= FALLBACK_AFTER_MS) startFallback()
        if (elapsed < RADIO_GIVE_UP_MS && retries < MAX_RETRIES) {
            retries++
            radioStartedAt = 0L
            handler.postDelayed({ if (AlarmRuntime.ringing) startRadio(url) }, RETRY_DELAY_MS)
        } else {
            AlarmLog.add(this, "radio ei käynnisty -> hälytysääni")
            player?.release()
            player = null
            startFallback()
        }
    }

    private fun checkStarted() {
        if (player?.isPlaying != true) {
            AlarmLog.add(this, "radio ei soi ${FALLBACK_AFTER_MS / 1000} s -> hälytysääni, radio yrittää yhä")
            startFallback()
        }
    }

    private val rampStep = object : Runnable {
        override fun run() {
            val exo = player ?: return
            val elapsed = SystemClock.elapsedRealtime() - radioStartedAt
            val volume = (START_VOLUME + (1f - START_VOLUME) * elapsed / RAMP_MS.toFloat())
                .coerceIn(START_VOLUME, 1f)
            exo.volume = volume
            if (volume < 1f) handler.postDelayed(this, 1_000L)
        }
    }

    /** Built-in alarm tone: the alarm must never stay silent. */
    private fun startFallback() {
        if (fallbackPlayer != null) return
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

    private fun stopFallback() {
        fallbackPlayer?.let { runCatching { it.stop() }; it.release() }
        fallbackPlayer = null
        AlarmRuntime.usingFallback = false
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

    private fun pauseNormalPlayback() {
        val appContext = applicationContext
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        future.addListener(
            {
                runCatching {
                    val controller = future.get()
                    if (controller.isPlaying || controller.playWhenReady) {
                        controller.pause()
                        AlarmLog.add(appContext, "tavallinen toisto pysäytetty")
                    }
                }
                MediaController.releaseFuture(future)
            },
            ContextCompat.getMainExecutor(appContext)
        )
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
        val text = listOfNotNull(settings.stationName, getString(R.string.alarm_tap_for_more))
            .joinToString(" · ")

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
            // When the phone is in use Android shows a small heads-up instead of
            // the full-screen view. Two short actions fit there; "Jatka kuuntelua"
            // is in the full view that opens when the notification is tapped.
            .addAction(0, getString(R.string.alarm_snooze), serviceIntent(this, ACTION_SNOOZE, 1))
            .addAction(0, getString(R.string.alarm_dismiss), serviceIntent(this, ACTION_DISMISS, 2))
            .build()
    }

    companion object {
        const val ACTION_START = "fi.aalto.radio.alarm.START"
        const val ACTION_SNOOZE = "fi.aalto.radio.alarm.SNOOZE"
        const val ACTION_DISMISS = "fi.aalto.radio.alarm.DISMISS"
        const val ACTION_CONTINUE = "fi.aalto.radio.alarm.CONTINUE"

        /** Rings right away with the saved settings (test button in the dialog). */
        internal fun ringNow(context: Context) {
            AlarmLog.add(context, "testiherätys")
            ContextCompat.startForegroundService(
                context,
                Intent(context, AlarmService::class.java).setAction(ACTION_START)
            )
        }

        const val SNOOZE_MINUTES = 9
        private const val CHANNEL_ID = "aalto_alarm"
        private const val NOTIFICATION_ID = 4101
        private const val FALLBACK_AFTER_MS = 20_000L
        private const val RADIO_GIVE_UP_MS = 120_000L
        private const val RETRY_DELAY_MS = 4_000L
        private const val MAX_RETRIES = 20
        private const val RAMP_MS = 30_000L
        private const val AUTO_STOP_MS = 30L * 60_000L
        private const val START_VOLUME = 0.2f

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
