package fi.aalto.radio.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import android.graphics.Bitmap
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import fi.aalto.radio.MainActivity
import fi.aalto.radio.PlaybackService
import fi.aalto.radio.R
import fi.aalto.radio.playback.LastStationStore

/**
 * Home screen widget: logo, station, what is on, play/pause and next own
 * station. Buttons talk to PlaybackService through a short-lived controller,
 * so they work without opening the app.
 */
class AaltoWidgetProvider : AppWidgetProvider() {

    data class State(
        val stationName: String?,
        val track: String?,
        val isPlaying: Boolean,
        val logo: Bitmap?
    )

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        render(
            context,
            lastState ?: State(LastStationStore.name(context), null, false, null)
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_TOGGLE -> control(context) { controller ->
                when {
                    controller.currentMediaItem == null -> {
                        val item = LastStationStore.load(context)
                        if (item == null) {
                            openApp(context)
                        } else {
                            controller.setMediaItem(item)
                            controller.prepare()
                            controller.play()
                        }
                    }
                    controller.isPlaying -> controller.pause()
                    else -> {
                        if (controller.playbackState == Player.STATE_IDLE) controller.prepare()
                        controller.play()
                    }
                }
            }
            ACTION_NEXT -> control(context) { controller ->
                controller.seekToNext()
                controller.play()
            }
        }
    }

    private fun control(context: Context, action: (MediaController) -> Unit) {
        val pending = goAsync()
        val appContext = context.applicationContext
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        future.addListener(
            {
                try {
                    runCatching { action(future.get()) }
                } finally {
                    // Keep the connection a moment so the command is delivered.
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        MediaController.releaseFuture(future)
                        pending.finish()
                    }, 1_000L)
                }
            },
            ContextCompat.getMainExecutor(appContext)
        )
    }

    private fun openApp(context: Context) {
        runCatching {
            context.startActivity(
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    companion object {
        private const val ACTION_TOGGLE = "fi.aalto.radio.widget.TOGGLE"
        private const val ACTION_NEXT = "fi.aalto.radio.widget.NEXT"

        @Volatile
        private var lastState: State? = null

        fun render(context: Context, state: State) {
            lastState = state
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, AaltoWidgetProvider::class.java))
            if (ids.isEmpty()) return

            val views = RemoteViews(context.packageName, R.layout.widget_now_playing)
            views.setTextViewText(
                R.id.widget_station,
                state.stationName ?: context.getString(R.string.widget_empty)
            )
            val subtitle = when {
                state.track != null && state.isPlaying -> state.track
                state.isPlaying -> context.getString(R.string.state_playing)
                else -> context.getString(R.string.widget_tap_to_play)
            }
            views.setTextViewText(R.id.widget_subtitle, subtitle)
            if (state.logo != null) {
                views.setImageViewBitmap(R.id.widget_logo, state.logo)
                views.setViewVisibility(R.id.widget_logo, View.VISIBLE)
            } else {
                views.setImageViewResource(R.id.widget_logo, R.drawable.ic_widget_radio)
            }
            views.setImageViewResource(
                R.id.widget_play,
                if (state.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
            )
            views.setContentDescription(
                R.id.widget_play,
                context.getString(if (state.isPlaying) R.string.action_pause else R.string.action_play)
            )

            views.setOnClickPendingIntent(R.id.widget_play, broadcast(context, ACTION_TOGGLE, 1))
            views.setOnClickPendingIntent(R.id.widget_next, broadcast(context, ACTION_NEXT, 2))
            views.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(
                    context,
                    3,
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            manager.updateAppWidget(ids, views)
        }

        private fun broadcast(context: Context, action: String, requestCode: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                requestCode,
                Intent(context, AaltoWidgetProvider::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
    }
}
