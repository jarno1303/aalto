package fi.aalto.radio

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

class RadioPlayer(context: Context) : Player.Listener {

    private val appContext = context.applicationContext

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private var pendingStationId: String? = null
    private var pendingUrl: String? = null
    private var pendingTitle: String? = null
    private var pendingTrace: AaltoPerf.StationTrace? = null
    private var activeTrace: AaltoPerf.StationTrace? = null

    var isPlaying by mutableStateOf(false)
        private set

    var playbackError by mutableStateOf<String?>(null)
        private set

    /**
     * True from the moment a play command is issued until audio starts,
     * the user pauses, or playback fails. Drives the "Yhdistetään…" state.
     */
    var isConnecting by mutableStateOf(false)
        private set

    init {
        val sessionToken = SessionToken(
            appContext,
            ComponentName(appContext, PlaybackService::class.java)
        )

        val future = MediaController.Builder(appContext, sessionToken)
            .buildAsync()

        controllerFuture = future

        future.addListener(
            {
                try {
                    controller = future.get()
                    controller?.addListener(this)

                    isPlaying = controller?.isPlaying == true

                    val queuedStationId = pendingStationId
                    val queuedUrl = pendingUrl
                    val queuedTitle = pendingTitle
                    val queuedTrace = pendingTrace

                    if (queuedUrl != null) {
                        play(
                            stationId = queuedStationId,
                            url = queuedUrl,
                            title = queuedTitle,
                            trace = queuedTrace
                        )
                    }
                } catch (_: Exception) {
                    isPlaying = false
                    isConnecting = false
                    playbackError = appContext.getString(R.string.playback_error_unavailable)
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    fun play(station: RadioStation, trace: AaltoPerf.StationTrace? = null) {
        play(
            stationId = station.id,
            url = station.preferredStreamUrl,
            title = station.name,
            trace = trace
        )
    }

    fun play(url: String, title: String? = null) {
        play(
            stationId = null,
            url = url,
            title = title,
            trace = null
        )
    }

    private fun play(
        stationId: String?,
        url: String,
        title: String?,
        trace: AaltoPerf.StationTrace?
    ) {
        val currentController = controller
        playbackError = null
        isConnecting = true

        if (currentController == null) {
            pendingStationId = stationId
            pendingUrl = url
            pendingTitle = title
            pendingTrace = trace
            AaltoPerf.reportControllerPending(trace)
            return
        }

        pendingStationId = null
        pendingUrl = null
        pendingTitle = null
        pendingTrace = null

        val currentUrl = currentController.currentMediaItem
            ?.localConfiguration
            ?.uri
            ?.toString()

        activeTrace = trace

        if (currentUrl != url) {
            val mediaItem = MediaItem.Builder()
                .setMediaId(stationId ?: url)
                .setUri(url)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(title ?: "Aalto Radio")
                        .setArtist("Aalto")
                        .build()
                )
                .build()
            currentController.setMediaItem(mediaItem)
            currentController.prepare()
        } else if (currentController.playbackState == Player.STATE_IDLE) {
            // Same stream after an error or stop: the player must be prepared again,
            // otherwise play() does nothing and "retry" appears broken.
            currentController.prepare()
        }

        AaltoPerf.reportPlaybackCommandIssued(
            trace = trace,
            isSwitch = currentUrl != null && currentUrl != url
        )

        currentController.play()
    }

    fun toggle(station: RadioStation) {
        val currentController = controller

        if (currentController == null) {
            play(station)
            return
        }

        val currentUrl = currentController.currentMediaItem
            ?.localConfiguration
            ?.uri
            ?.toString()

        // play(station) uses preferredStreamUrl, so the pause check must compare the same URL.
        val isActive = currentController.isPlaying || isConnecting
        if (isActive && currentUrl == station.preferredStreamUrl) {
            currentController.pause()
            isConnecting = false
        } else {
            play(station)
        }
    }

    fun toggle(url: String, title: String? = null) {
        val currentController = controller

        if (currentController == null) {
            pendingUrl = url
            pendingTitle = title
            return
        }

        val currentUrl = currentController.currentMediaItem
            ?.localConfiguration
            ?.uri
            ?.toString()

        if (currentController.isPlaying && currentUrl == url) {
            currentController.pause()
            isConnecting = false
        } else {
            play(url, title)
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        this.isPlaying = isPlaying

        if (isPlaying) {
            isConnecting = false
            AaltoPerf.reportPlaybackStarted(activeTrace)
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        when (playbackState) {
            Player.STATE_BUFFERING -> {
                if (controller?.playWhenReady == true) isConnecting = true
            }
            Player.STATE_READY -> {
                AaltoPerf.reportPlayerReady(activeTrace)
                if (controller?.playWhenReady != true) isConnecting = false
            }
            Player.STATE_IDLE, Player.STATE_ENDED -> isConnecting = false
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        isPlaying = false
        isConnecting = false
        playbackError = appContext.getString(R.string.playback_error_start)
        AaltoPerf.reportPlayerError(activeTrace, error)
    }

    fun release() {
        controller?.removeListener(this)
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }

        controller = null
        controllerFuture = null
    }
}

@Composable
fun rememberRadioPlayer(): RadioPlayer {
    val context = LocalContext.current

    val player = remember(context) {
        RadioPlayer(context)
    }

    DisposableEffect(player) {
        onDispose {
            player.release()
        }
    }

    return player
}
