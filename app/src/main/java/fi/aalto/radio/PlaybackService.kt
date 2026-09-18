package fi.aalto.radio

import android.app.PendingIntent
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.MediaConstants
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import fi.aalto.radio.audio.AudioEffects
import fi.aalto.radio.history.StreamTrack
import fi.aalto.radio.history.TrackHistory
import fi.aalto.radio.history.TrackTitle
import fi.aalto.radio.playback.AaltoSessionPlayer
import fi.aalto.radio.playback.BrowseActions
import fi.aalto.radio.playback.LastStationStore
import fi.aalto.radio.playback.LogoTiles
import fi.aalto.radio.playback.StationLookup
import fi.aalto.radio.playback.StationMediaItems
import fi.aalto.radio.widget.AaltoWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the one persistent player (AGENTS.md §8).
 *
 * A MediaLibraryService, so Android Auto (and other media browsers) can list
 * Omat asemat / Viimeksi kuunnellut / Suositut and search. The ExoPlayer is
 * wrapped in [AaltoSessionPlayer] for automatic stream fallback and for
 * previous / next through the user's own stations.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mediaSession: MediaLibrarySession? = null
    private var sessionPlayer: AaltoSessionPlayer? = null
    private lateinit var lookup: StationLookup

    /** Own stations in the user's order, for previous / next. */
    @Volatile
    private var presets: List<RadioStation> = emptyList()
    private var favoriteIds: Set<String> = emptySet()

    /** Heart button next to the playback controls (car, notification). */
    private val favoriteCommand = SessionCommand(ACTION_TOGGLE_FAVORITE, Bundle.EMPTY)

    private val searchResults = mutableMapOf<String, List<RadioStation>>()

    /** The song the current station last announced, or null. */
    @Volatile
    private var currentTrack: String? = null

    /** The raw player, for the equalizer and the per-station gain. */
    private var exoPlayer: ExoPlayer? = null

    override fun onCreate() {
        super.onCreate()
        lookup = StationLookup(this)

        val exo = ExoPlayer.Builder(this)
            .setHandleAudioBecomingNoisy(true)
            // Gives way to calls and navigation prompts (required in the car).
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        val player = AaltoSessionPlayer(this, exo, scope) { presets }
        sessionPlayer = player
        exoPlayer = exo
        exo.addListener(widgetAndResumeListener)
        // Sound shaping sits beside the player, never in its path.
        AudioEffects.attach(this, exo, exo.audioSessionId)

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaLibrarySession.Builder(this, player, LibraryCallback())
            .setSessionActivity(openApp)
            .build()
        updateFavoriteButton()

        scope.launch {
            AaltoAppContainer.stationRepository(this@PlaybackService)
                .observeFavoriteOrder()
                .collectLatest { ids ->
                    favoriteIds = ids.toSet()
                    updateFavoriteButton()
                    presets = lookup.byIds(ids)
                    sessionPlayer?.syncPlaylist()
                }
        }
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaLibrarySession? {
        return mediaSession
    }

    override fun onDestroy() {
        AudioEffects.release()
        exoPlayer = null
        mediaSession?.let { session ->
            sessionPlayer?.detach()
            session.player.release()
            session.release()
        }
        mediaSession = null
        sessionPlayer = null
        scope.cancel()
        super.onDestroy()
    }

    // ---- Widget + "resume last station" ------------------------------------

    private val widgetAndResumeListener = object : Player.Listener {
        /** The session id appears once audio starts, and can change later. */
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            val exo = exoPlayer ?: return
            AudioEffects.attach(this@PlaybackService, exo, audioSessionId)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            mediaItem?.let { LastStationStore.save(this@PlaybackService, it) }
            // Each station keeps its own level.
            AudioEffects.applyStationGain(this@PlaybackService, mediaItem?.mediaId)
            // A new station has not announced anything yet.
            currentTrack = null
            publishTrack(null)
            updateFavoriteButton()
            refreshWidget()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            refreshWidget()
            if (isPlaying) recordRecent()
        }

        override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) = refreshWidget()

        /**
         * ICY and ID3 announcements from the stream itself. The player's
         * combined MediaMetadata cannot be used for this: the MediaItem's own
         * title (the station name) takes precedence there, so the song never
         * appears in it.
         */
        override fun onMetadata(metadata: androidx.media3.common.Metadata) {
            val announcement = StreamTrack.from(metadata)
            if (announcement == null) {
                // Something arrived but carried no song: worth seeing, because
                // it separates "the station sends nothing" from "we drop it".
                Log.d(TRACK_TAG, "no song in ${StreamTrack.describe(metadata)}")
                return
            }
            val item = sessionPlayer?.currentMediaItem ?: return
            val stationId = item.mediaId.takeIf { it.isNotBlank() } ?: return
            val stationName = item.mediaMetadata.title?.toString()
            val line = TrackTitle.format(
                title = announcement.title,
                artist = announcement.artist,
                stationTitle = stationName,
                stationDetails = item.mediaMetadata.artist?.toString()
            )
            Log.d(TRACK_TAG, "station=$stationName announced=$announcement line=$line")
            if (line == currentTrack) return
            currentTrack = line
            publishTrack(line)
            refreshWidget()
            if (line != null) {
                scope.launch {
                    runCatching { TrackHistory.record(this@PlaybackService, stationId, stationName, line) }
                }
            }
        }
    }

    private fun favoriteButton(isFavorite: Boolean): CommandButton =
        CommandButton.Builder(
            if (isFavorite) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED
        )
            .setDisplayName(getString(if (isFavorite) R.string.favorite_remove else R.string.favorite_add))
            .setSessionCommand(favoriteCommand)
            .build()

    private fun updateFavoriteButton() {
        val session = mediaSession ?: return
        val id = sessionPlayer?.currentMediaItem?.mediaId
        val buttons = if (id == null) emptyList() else listOf(favoriteButton(id in favoriteIds))
        session.setMediaButtonPreferences(buttons)
    }

    private fun setFavorite(id: String, add: Boolean) {
        val wasFavorite = id in favoriteIds
        if (wasFavorite == add) return
        // Show the new state at once; the database flow confirms it.
        favoriteIds = if (wasFavorite) favoriteIds - id else favoriteIds + id
        updateFavoriteButton()
        scope.launch {
            val repository = AaltoAppContainer.stationRepository(this@PlaybackService)
            val committed = runCatching {
                if (repository.stationById(id) == null) {
                    lookup.byId(id)?.let { repository.registerCatalogStations(listOf(it)) }
                }
                if (wasFavorite) repository.removeFavorite(id) else repository.addFavorite(id)
            }.getOrDefault(false)
            if (committed) {
                runCatching { AaltoAppContainer.syncCoordinator(this@PlaybackService).requestSync() }
                // Let the car refresh "Omat asemat".
                mediaSession?.notifyChildrenChanged(MINE_ID, Int.MAX_VALUE, null)
            } else {
                favoriteIds = if (wasFavorite) favoriteIds + id else favoriteIds - id
                updateFavoriteButton()
            }
        }
    }

    private var lastRecordedId: String? = null

    /**
     * Recents are recorded here too, so stations started from the car,
     * widget or notification show up in "Viimeisimmät". Runs after playback
     * has started and never blocks it (AGENTS.md §8).
     */
    private fun recordRecent() {
        val id = sessionPlayer?.currentMediaItem?.mediaId ?: return
        if (id == lastRecordedId) return
        lastRecordedId = id
        scope.launch {
            runCatching {
                val repository = AaltoAppContainer.stationRepository(this@PlaybackService)
                if (repository.stationById(id) == null) {
                    lookup.byId(id)?.let { repository.registerCatalogStations(listOf(it)) }
                }
                repository.recordRecentlyPlayed(id)
            }.onFailure { Log.w(TAG, "recent not recorded for $id", it) }
        }
    }

    /**
     * Tells every controller (the app, and anything else connected) what is
     * playing. Session extras, because the song cannot travel in the media
     * metadata without taking the station name's place there.
     */
    private fun publishTrack(line: String?) {
        val session = mediaSession ?: return
        runCatching {
            session.setSessionExtras(
                Bundle().apply { if (line != null) putString(EXTRA_NOW_PLAYING_TRACK, line) }
            )
        }
    }

    private fun refreshWidget() {
        val player = sessionPlayer ?: return
        val item = player.currentMediaItem
        val stationId = item?.mediaId
        val stationName = item?.mediaMetadata?.title?.toString()
        val track = currentTrack
        val playing = player.isPlaying
        scope.launch {
            val logo = if (stationId != null) {
                withContext(Dispatchers.IO) {
                    runCatching {
                        val station = lookup.byId(stationId)
                        station?.let {
                            StationLogoResolver.resolveFile(this@PlaybackService, it)
                                ?.let { logo -> LogoTiles.square(this@PlaybackService, logo) }
                                ?: LogoTiles.initials(this@PlaybackService, it)
                        }
                            ?.let { file ->
                                BitmapFactory.decodeFile(
                                    file.absolutePath,
                                    BitmapFactory.Options().apply { inSampleSize = 2 }
                                )
                            }
                    }.getOrNull()
                }
            } else {
                null
            }
            AaltoWidgetProvider.render(
                this@PlaybackService,
                AaltoWidgetProvider.State(
                    stationName = stationName ?: LastStationStore.name(this@PlaybackService),
                    track = track,
                    isPlaying = playing,
                    logo = logo
                )
            )
        }
    }

    // ---- Library for Android Auto ------------------------------------------

    private inner class LibraryCallback : MediaLibrarySession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                .buildUpon()
                .add(favoriteCommand)
                .add(SessionCommand(BrowseActions.ADD_FAVORITE, Bundle.EMPTY))
                .add(SessionCommand(BrowseActions.REMOVE_FAVORITE, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                ACTION_TOGGLE_FAVORITE -> {
                    val id = sessionPlayer?.currentMediaItem?.mediaId
                    if (id != null) setFavorite(id, id !in favoriteIds)
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                BrowseActions.ADD_FAVORITE, BrowseActions.REMOVE_FAVORITE -> {
                    val id = args.getString(BrowseActions.KEY_MEDIA_ITEM_ID)
                        ?: return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE))
                    val add = customCommand.customAction == BrowseActions.ADD_FAVORITE
                    setFavorite(id, add)
                    val message = getString(if (add) R.string.auto_added_favorite else R.string.auto_removed_favorite)
                    return Futures.immediateFuture(
                        SessionResult(SessionResult.RESULT_SUCCESS, BrowseActions.result(message))
                    )
                }
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val extras = Bundle().apply {
                putInt(
                    MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
                    MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
                )
                putInt(
                    MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
                    MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
                )
                putBoolean("android.media.browse.SEARCH_SUPPORTED", true)
                BrowseActions.putRootActions(this@PlaybackService, this)
            }
            Log.d(TAG, "root requested by ${browser.packageName}")
            val rootParams = LibraryParams.Builder().setExtras(extras).build()
            return Futures.immediateFuture(
                LibraryResult.ofItem(StationMediaItems.folder(ROOT_ID, getString(R.string.app_name)), rootParams)
            )
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = future {
            val items: List<MediaItem> = when (parentId) {
                ROOT_ID -> listOf(
                    StationMediaItems.folder(MINE_ID, getString(R.string.home_mine)),
                    StationMediaItems.folder(RECENT_ID, getString(R.string.auto_recent)),
                    // Stable, short tab labels: the car shows at most four tabs in one row,
                    // and a driver relies on them staying the same.
                    StationMediaItems.folder(POPULAR_ID, getString(R.string.auto_popular)),
                    StationMediaItems.folder(COUNTRIES_ID, getString(R.string.auto_countries))
                )
                COUNTRIES_ID -> {
                    val current = lookup.homeCountry()
                    // Home country first, then the rest alphabetically.
                    (listOf(current) + browsableCountryCodes.filter { it != current }
                        .sortedBy { countryName(it) })
                        .map { code -> StationMediaItems.folder(COUNTRY_PREFIX + code, countryName(code)) }
                }
                MINE_ID -> lookup.favorites().map { browseItem(it) }
                RECENT_ID -> lookup.recents().map { browseItem(it) }
                POPULAR_ID -> lookup.popular().map { browseItem(it) }
                else -> if (parentId.startsWith(COUNTRY_PREFIX)) {
                    lookup.popular(countryCode = parentId.removePrefix(COUNTRY_PREFIX))
                        .map { browseItem(it) }
                } else {
                    emptyList()
                }
            }
            Log.d(TAG, "children parent=$parentId page=$page size=$pageSize -> ${items.size} by ${browser.packageName}")
            LibraryResult.ofItemList(paged(items, page, pageSize), params)
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> = future {
            val station = lookup.byId(mediaId)
            if (station != null) {
                LibraryResult.ofItem(StationMediaItems.build(this@PlaybackService, station), null)
            } else {
                LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
            }
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> {
            scope.launch {
                val results = lookup.search(query)
                searchResults[query] = results
                session.notifySearchResultChanged(browser, query, results.size, params)
            }
            return Futures.immediateFuture(LibraryResult.ofVoid())
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = future {
            val results = searchResults[query] ?: lookup.search(query).also { searchResults[query] = it }
            val items = results.map { browseItem(it) }
            LibraryResult.ofItemList(paged(items, page, pageSize), params)
        }

        /**
         * Car and other browsers send only a station id (or a voice query);
         * the app itself sends a full item with an address.
         */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> = future {
            mediaItems.mapNotNull { item ->
                resolve(item).also {
                    if (it == null) Log.w(TAG, "could not resolve '${item.mediaId}' from ${controller.packageName}")
                }
            }.toMutableList()
        }
    }

    private suspend fun resolve(item: MediaItem): MediaItem? {
        if (item.localConfiguration != null) return item
        if (item.mediaId.isNotBlank()) {
            lookup.byId(item.mediaId)?.let { return StationMediaItems.build(this, it) }
        }
        val query = item.requestMetadata.searchQuery
        if (!query.isNullOrBlank()) {
            lookup.search(query).firstOrNull()?.let { return StationMediaItems.build(this, it) }
        }
        return null
    }

    private fun browseItem(station: RadioStation): MediaItem =
        StationMediaItems.build(this, station, BrowseActions.forStation(station.id in favoriteIds))

    private fun paged(items: List<MediaItem>, page: Int, pageSize: Int): ImmutableList<MediaItem> {
        if (pageSize <= 0 || pageSize == Int.MAX_VALUE) return ImmutableList.copyOf(items)
        val from = (page * pageSize).coerceAtMost(items.size)
        val to = (from + pageSize).coerceAtMost(items.size)
        return ImmutableList.copyOf(items.subList(from, to))
    }

    private fun <T> future(block: suspend () -> T): ListenableFuture<T> {
        val result = SettableFuture.create<T>()
        scope.launch {
            try {
                result.set(block())
            } catch (error: Throwable) {
                Log.w(TAG, "library request failed", error)
                result.setException(error)
            }
        }
        return result
    }

    companion object {
        /** Session extra carrying "Artist – Title" to the controllers. */
        const val EXTRA_NOW_PLAYING_TRACK = "fi.aalto.radio.NOW_PLAYING_TRACK"

        private const val TRACK_TAG = "AALTO_TRACK"
        private const val TAG = "AALTO_AUTO"
        private const val ACTION_TOGGLE_FAVORITE = "fi.aalto.radio.TOGGLE_FAVORITE"
        private const val ROOT_ID = "aalto_root"
        private const val MINE_ID = "aalto_mine"
        private const val RECENT_ID = "aalto_recent"
        private const val POPULAR_ID = "aalto_popular"
        private const val COUNTRIES_ID = "aalto_countries"
        private const val COUNTRY_PREFIX = "aalto_country_"
    }
}
