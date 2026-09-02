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
                    playbackError = "Toisto ei ole kaytettavissa."
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
        }

        AaltoPerf.reportPlaybackCommandIssued(
            trace = trace,
            isSwitch = currentUrl != null && currentUrl != url
        )

        currentController.play()
    }

    fun toggle(station: RadioStation) {
        toggle(station.streamUrl, station.name)
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
        } else {
            play(url, title)
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        this.isPlaying = isPlaying

        if (isPlaying) {
            AaltoPerf.reportPlaybackStarted(activeTrace)
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_READY) {
            AaltoPerf.reportPlayerReady(activeTrace)
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        isPlaying = false
        playbackError = "Toisto ei kaynnistynyt."
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
