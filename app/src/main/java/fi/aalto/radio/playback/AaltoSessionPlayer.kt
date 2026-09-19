package fi.aalto.radio.playback

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import fi.aalto.radio.RadioStation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The player the media session (phone, car, widget, notification) talks to.
 *
 * Adds two things on top of ExoPlayer, without changing how a stream starts:
 *
 * 1. **Automatic stream fallback.** When an address fails (or buffers too
 *    long before any audio), the next known address of the same station is
 *    tried. While that happens the error is not shown to controllers, so the
 *    UI simply keeps saying "Yhdistetään…". Only when every address has
 *    failed does the error reach the UI.
 * 2. **Own stations as the playlist.** When an own station plays, the whole
 *    own-station list is the player's playlist: the car's "Jono" shows it,
 *    and steering wheel / headset / notification previous-next move through
 *    it. Only the current station is ever loaded; the rest are just entries.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class AaltoSessionPlayer(
    private val context: Context,
    private val exo: ExoPlayer,
    private val scope: CoroutineScope,
    private val presets: () -> List<RadioStation>
) : ForwardingPlayer(exo) {

    private val handler = Handler(Looper.getMainLooper())

    private var currentId: String? = null
    private val candidates = mutableListOf<String>()
    private var candidateIndex = 0
    private var retriesForCandidate = 0
    private var readyOnce = false
    private var switching = false
    private val resolvedPlaylists = mutableSetOf<String>()

    /** True while a failed address is being replaced; errors are hidden meanwhile. */
    private var recovering = false

    private val bufferingTimeout = Runnable {
        if (exo.playbackState == Player.STATE_BUFFERING && !readyOnce && hasNextCandidate()) {
            recovering = true
            switchToNext()
        }
    }

    // Registered before the session is created, so it runs before the
    // session's own listener for every event.
    private var lastIndex = 0

    private val internalListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && exo.mediaItemCount > 1) {
                // A live stream "ended": never jump to another station by itself.
                val back = lastIndex
                handler.post {
                    if (back < exo.mediaItemCount) {
                        exo.seekToDefaultPosition(back)
                        exo.prepare()
                    }
                }
                return
            }
            lastIndex = exo.currentMediaItemIndex
            if (switching && mediaItem?.mediaId == currentId) return
            startTracking(mediaItem)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> if (!readyOnce) scheduleTimeout()
                Player.STATE_READY -> {
                    cancelTimeout()
                    readyOnce = true
                    recovering = false
                    retriesForCandidate = 0
                    val id = currentId
                    val url = candidates.getOrNull(candidateIndex)
                    if (id != null && url != null) StreamMemory.put(context, id, url)
                }
                else -> cancelTimeout()
            }
        }

        override fun onPlayerErrorChanged(error: PlaybackException?) {
            if (error == null || recovering) return
            if (canRecover()) {
                recovering = true
                handler.post(::recover)
            }
        }
    }

    init {
        exo.addListener(internalListener)
        // Shown as the heading of the car's queue: the queue is the own stations.
        exo.playlistMetadata = androidx.media3.common.MediaMetadata.Builder()
            .setTitle(context.getString(fi.aalto.radio.R.string.home_mine))
            .build()
        startTracking(exo.currentMediaItem)
    }

    // ---- Listener filtering: hide errors while recovering -----------------

    override fun addListener(listener: Player.Listener) {
        val wrapped = FilteringListener(listener)
        sessionListeners += wrapped
        super.addListener(wrapped)
    }

    override fun removeListener(listener: Player.Listener) {
        val wrapped = FilteringListener(listener)
        sessionListeners -= wrapped
        super.removeListener(wrapped)
    }

    // ---- Song in the shared metadata: car, Bluetooth, system media card -----

    private val sessionListeners = linkedSetOf<Player.Listener>()
    private var announcedTrack: String? = null
    private var announcedStationId: String? = null

    /**
     * The song the station announced, or null. Everything outside the app
     * (a Bluetooth car display, Android Auto, the system media card) reads
     * only title and artist, so while a song is known it becomes the title and
     * the station moves to the artist line. Nothing about playback changes.
     */
    fun setAnnouncedTrack(stationId: String?, track: String?) {
        if (announcedStationId == stationId && announcedTrack == track) return
        announcedStationId = stationId
        announcedTrack = track
        val metadata = mediaMetadata
        val events = Player.Events(
            androidx.media3.common.FlagSet.Builder().add(Player.EVENT_MEDIA_METADATA_CHANGED).build()
        )
        sessionListeners.toList().forEach { listener ->
            runCatching {
                listener.onMediaMetadataChanged(metadata)
                listener.onEvents(this, events)
            }
        }
    }

    override fun getMediaMetadata(): androidx.media3.common.MediaMetadata {
        val base = super.getMediaMetadata()
        val track = announcedTrack
        val item = exo.currentMediaItem
        // Only for the station it was announced on: a station change must not
        // show the previous station's song even for a moment.
        if (track.isNullOrBlank() || item?.mediaId != announcedStationId) return base
        val stationName = item?.mediaMetadata?.title ?: base.title
        return base.buildUpon()
            .setTitle(track)
            .setDisplayTitle(track)
            .setArtist(stationName)
            .setSubtitle(stationName)
            .build()
    }

    override fun getPlayerError(): PlaybackException? =
        if (recovering) null else super.getPlayerError()

    override fun getPlaybackState(): Int {
        val state = super.getPlaybackState()
        return if (recovering && state == Player.STATE_IDLE) Player.STATE_BUFFERING else state
    }

    /**
     * Passes every event on, except errors (and the idle / not-playing state
     * they cause) while a fallback address is being tried. All callbacks are
     * written out so nothing depends on how default methods are delegated.
     */
    private inner class FilteringListener(private val delegate: Player.Listener) : Player.Listener {
        override fun onPlayerErrorChanged(error: PlaybackException?) {
            if (!recovering) delegate.onPlayerErrorChanged(error)
        }

        override fun onPlayerError(error: PlaybackException) {
            if (!recovering) delegate.onPlayerError(error)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (recovering && playbackState == Player.STATE_IDLE) return
            delegate.onPlaybackStateChanged(playbackState)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (recovering && !isPlaying) return
            delegate.onIsPlayingChanged(isPlaying)
        }

        override fun onEvents(player: Player, events: Player.Events) = delegate.onEvents(player, events)
        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) =
            delegate.onTimelineChanged(timeline, reason)
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) =
            delegate.onMediaItemTransition(mediaItem, reason)
        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) = delegate.onTracksChanged(tracks)
        override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) =
            delegate.onMediaMetadataChanged(mediaMetadata)
        override fun onPlaylistMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) =
            delegate.onPlaylistMetadataChanged(mediaMetadata)
        override fun onIsLoadingChanged(isLoading: Boolean) = delegate.onIsLoadingChanged(isLoading)
        override fun onAvailableCommandsChanged(availableCommands: Player.Commands) =
            delegate.onAvailableCommandsChanged(availableCommands)
        override fun onTrackSelectionParametersChanged(parameters: androidx.media3.common.TrackSelectionParameters) =
            delegate.onTrackSelectionParametersChanged(parameters)
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) =
            delegate.onPlayWhenReadyChanged(playWhenReady, reason)
        override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) =
            delegate.onPlaybackSuppressionReasonChanged(playbackSuppressionReason)
        override fun onRepeatModeChanged(repeatMode: Int) = delegate.onRepeatModeChanged(repeatMode)
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) =
            delegate.onShuffleModeEnabledChanged(shuffleModeEnabled)
        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) = delegate.onPositionDiscontinuity(oldPosition, newPosition, reason)
        override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) =
            delegate.onPlaybackParametersChanged(playbackParameters)
        override fun onSeekBackIncrementChanged(seekBackIncrementMs: Long) =
            delegate.onSeekBackIncrementChanged(seekBackIncrementMs)
        override fun onSeekForwardIncrementChanged(seekForwardIncrementMs: Long) =
            delegate.onSeekForwardIncrementChanged(seekForwardIncrementMs)
        override fun onMaxSeekToPreviousPositionChanged(maxSeekToPreviousPositionMs: Long) =
            delegate.onMaxSeekToPreviousPositionChanged(maxSeekToPreviousPositionMs)
        override fun onAudioSessionIdChanged(audioSessionId: Int) = delegate.onAudioSessionIdChanged(audioSessionId)
        override fun onAudioAttributesChanged(audioAttributes: androidx.media3.common.AudioAttributes) =
            delegate.onAudioAttributesChanged(audioAttributes)
        override fun onVolumeChanged(volume: Float) = delegate.onVolumeChanged(volume)
        override fun onSkipSilenceEnabledChanged(skipSilenceEnabled: Boolean) =
            delegate.onSkipSilenceEnabledChanged(skipSilenceEnabled)
        override fun onDeviceInfoChanged(deviceInfo: androidx.media3.common.DeviceInfo) =
            delegate.onDeviceInfoChanged(deviceInfo)
        override fun onDeviceVolumeChanged(volume: Int, muted: Boolean) = delegate.onDeviceVolumeChanged(volume, muted)
        override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) =
            delegate.onVideoSizeChanged(videoSize)
        override fun onSurfaceSizeChanged(width: Int, height: Int) = delegate.onSurfaceSizeChanged(width, height)
        override fun onRenderedFirstFrame() = delegate.onRenderedFirstFrame()
        override fun onCues(cueGroup: androidx.media3.common.text.CueGroup) = delegate.onCues(cueGroup)
        override fun onMetadata(metadata: androidx.media3.common.Metadata) = delegate.onMetadata(metadata)

        override fun equals(other: Any?): Boolean =
            other is FilteringListener && other.delegate == delegate

        override fun hashCode(): Int = delegate.hashCode()
    }

    // ---- Fallback ----------------------------------------------------------

    private fun startTracking(item: MediaItem?) {
        cancelTimeout()
        currentId = item?.mediaId
        candidates.clear()
        if (item != null) candidates.addAll(StreamCandidates.fromItem(item))
        candidateIndex = 0
        retriesForCandidate = 0
        readyOnce = false
        recovering = false
    }

    private fun hasNextCandidate(): Boolean = candidateIndex + 1 < candidates.size

    private fun canRecover(): Boolean {
        val failed = candidates.getOrNull(candidateIndex) ?: return false
        return when {
            // A stream that already played gets one quick retry (network blip).
            readyOnce && retriesForCandidate == 0 -> true
            hasNextCandidate() -> true
            StreamCandidates.isPlaylistUrl(failed) && failed !in resolvedPlaylists -> true
            else -> false
        }
    }

    private fun recover() {
        if (!recovering) return
        if (readyOnce && retriesForCandidate == 0) {
            retriesForCandidate++
            handler.postDelayed({ if (recovering) applyCandidate(candidateIndex) }, RETRY_DELAY_MS)
            return
        }
        switchToNext()
    }

    private fun switchToNext() {
        val failed = candidates.getOrNull(candidateIndex)
        val id = currentId
        if (failed != null && StreamCandidates.isPlaylistUrl(failed) && resolvedPlaylists.add(failed)) {
            scope.launch {
                val resolved = withContext(Dispatchers.IO) { PlaylistResolver.resolve(failed) }
                if (currentId != id || !recovering) return@launch
                val fresh = resolved.filterNot { it in candidates }
                candidates.addAll(candidateIndex + 1, fresh)
                // Nothing new: retry the same address so the real error surfaces.
                applyCandidate(if (fresh.isEmpty()) candidateIndex else candidateIndex + 1)
            }
            return
        }
        applyCandidate(if (hasNextCandidate()) candidateIndex + 1 else candidateIndex)
    }

    private fun applyCandidate(index: Int) {
        val item = exo.currentMediaItem ?: run { recovering = false; return }
        val url = candidates.getOrNull(index) ?: run { recovering = false; return }
        if (index == candidateIndex && !readyOnce) {
            // Last attempt: let a new error reach the UI.
            recovering = false
        }
        val sameCandidate = index == candidateIndex
        candidateIndex = index
        if (!sameCandidate) retriesForCandidate = 0
        readyOnce = false
        val replacement = item.buildUpon().setUri(url).build()
        switching = true
        try {
            exo.replaceMediaItem(exo.currentMediaItemIndex, replacement)
            exo.prepare()
        } finally {
            switching = false
        }
    }

    private fun scheduleTimeout() {
        cancelTimeout()
        handler.postDelayed(bufferingTimeout, START_TIMEOUT_MS)
    }

    private fun cancelTimeout() {
        handler.removeCallbacks(bufferingTimeout)
    }

    // ---- Previous / next through own stations ------------------------------

    override fun getAvailableCommands(): Player.Commands =
        super.getAvailableCommands().buildUpon()
            .addAll(
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
            )
            .build()

    override fun isCommandAvailable(command: Int): Boolean =
        command in STEP_COMMANDS || super.isCommandAvailable(command)

    override fun hasNextMediaItem(): Boolean = presets().size >= 2
    override fun hasPreviousMediaItem(): Boolean = presets().size >= 2
    override fun seekToNext() = step(1)
    override fun seekToNextMediaItem() = step(1)
    override fun seekToPrevious() = step(-1)
    override fun seekToPreviousMediaItem() = step(-1)

    private fun step(direction: Int) {
        val list = presets()
        if (list.isEmpty()) return
        val currentStationId = exo.currentMediaItem?.mediaId
        val index = list.indexOfFirst { it.id == currentStationId }
        val next = if (index < 0) {
            if (direction > 0) 0 else list.lastIndex
        } else {
            Math.floorMod(index + direction, list.size)
        }
        val inPlaylist = exo.mediaItemCount == list.size &&
            (0 until exo.mediaItemCount).all { exo.getMediaItemAt(it).mediaId == list[it].id }
        if (inPlaylist) {
            exo.seekToDefaultPosition(next)
        } else {
            exo.setMediaItems(presetItems(list), next, C.TIME_UNSET)
        }
        exo.prepare()
        exo.play()
    }

    private fun presetItems(list: List<RadioStation>, keep: MediaItem? = null): List<MediaItem> =
        list.map { station ->
            if (keep != null && keep.mediaId == station.id) keep else StationMediaItems.build(context, station)
        }

    // ---- Playlist = own stations -------------------------------------------

    /**
     * A single station from the app, car, widget or alarm always comes with
     * the own stations: an own station plays at its place in the list, any
     * other station plays first with the own stations after it. So the queue
     * is always "Omat asemat".
     */
    private fun expand(items: List<MediaItem>, startIndex: Int, startPositionMs: Long) {
        val single = items.singleOrNull()
        val list = presets()
        if (single == null || list.isEmpty()) {
            exo.setMediaItems(items, startIndex, startPositionMs)
            return
        }
        val index = list.indexOfFirst { it.id == single.mediaId }
        if (index >= 0) {
            exo.setMediaItems(presetItems(list, keep = single), index, C.TIME_UNSET)
        } else {
            exo.setMediaItems(listOf(single) + presetItems(list), 0, C.TIME_UNSET)
        }
    }

    /** Station ids the playlist should have around [current]. */
    private fun wantedIds(current: MediaItem, list: List<RadioStation>): List<String> {
        val ids = list.map { it.id }
        return when {
            current.mediaId in ids -> ids
            else -> listOf(current.mediaId) + ids
        }
    }

    override fun setMediaItem(mediaItem: MediaItem) = expand(listOf(mediaItem), 0, C.TIME_UNSET)
    override fun setMediaItem(mediaItem: MediaItem, startPositionMs: Long) =
        expand(listOf(mediaItem), 0, startPositionMs)
    override fun setMediaItem(mediaItem: MediaItem, resetPosition: Boolean) =
        expand(listOf(mediaItem), 0, C.TIME_UNSET)
    override fun setMediaItems(mediaItems: MutableList<MediaItem>) = expand(mediaItems, 0, C.TIME_UNSET)
    override fun setMediaItems(mediaItems: MutableList<MediaItem>, resetPosition: Boolean) =
        expand(mediaItems, 0, C.TIME_UNSET)
    override fun setMediaItems(mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long) =
        expand(mediaItems, startIndex, startPositionMs)

    /**
     * Own stations changed (added, removed, reordered): update the playlist
     * around the current station without interrupting it.
     */
    fun syncPlaylist() {
        val current = exo.currentMediaItem ?: return
        val list = presets()
        val index = list.indexOfFirst { it.id == current.mediaId }
        val wanted = wantedIds(current, list)
        val actual = (0 until exo.mediaItemCount).map { exo.getMediaItemAt(it).mediaId }
        if (actual == wanted) return
        val currentIndex = exo.currentMediaItemIndex
        switching = true
        try {
            // Keep only the playing entry, then rebuild the list around it.
            if (currentIndex + 1 < exo.mediaItemCount) exo.removeMediaItems(currentIndex + 1, exo.mediaItemCount)
            if (currentIndex > 0) exo.removeMediaItems(0, currentIndex)
            if (index >= 0) {
                exo.addMediaItems(0, presetItems(list.subList(0, index)))
                exo.addMediaItems(presetItems(list.subList(index + 1, list.size)))
            } else {
                exo.addMediaItems(presetItems(list))
            }
            lastIndex = exo.currentMediaItemIndex
        } finally {
            switching = false
        }
    }

    /** Stops fallback timers and listening; the player itself is released by the session owner. */
    fun detach() {
        cancelTimeout()
        handler.removeCallbacksAndMessages(null)
        exo.removeListener(internalListener)
    }

    private companion object {
        const val START_TIMEOUT_MS = 12_000L
        const val RETRY_DELAY_MS = 1_500L
        val STEP_COMMANDS = setOf(
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
        )
    }
}
