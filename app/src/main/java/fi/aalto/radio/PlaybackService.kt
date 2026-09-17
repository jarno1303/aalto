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
import androidx.media3.session.LibraryResult
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
import fi.aalto.radio.playback.AaltoSessionPlayer
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

    private val searchResults = mutableMapOf<String, List<RadioStation>>()

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
        exo.addListener(widgetAndResumeListener)

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaLibrarySession.Builder(this, player, LibraryCallback())
            .setSessionActivity(openApp)
            .build()

        scope.launch {
            AaltoAppContainer.stationRepository(this@PlaybackService)
                .observeFavoriteOrder()
                .collectLatest { ids -> presets = lookup.byIds(ids) }
        }
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaLibrarySession? {
        return mediaSession
    }

    override fun onDestroy() {
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
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            mediaItem?.let { LastStationStore.save(this@PlaybackService, it) }
            refreshWidget()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) = refreshWidget()

        override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) = refreshWidget()
    }

    private fun refreshWidget() {
        val player = sessionPlayer ?: return
        val item = player.currentMediaItem
        val stationId = item?.mediaId
        val stationName = item?.mediaMetadata?.title?.toString()
        val track = player.mediaMetadata.title?.toString()?.takeIf { it != stationName }
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
                    StationMediaItems.folder(RECENT_ID, getString(R.string.search_recent)),
                    StationMediaItems.folder(POPULAR_ID, getString(R.string.home_popular))
                )
                MINE_ID -> lookup.favorites().map { StationMediaItems.build(this@PlaybackService, it) }
                RECENT_ID -> lookup.recents().map { StationMediaItems.build(this@PlaybackService, it) }
                POPULAR_ID -> lookup.popular().map { StationMediaItems.build(this@PlaybackService, it) }
                else -> emptyList()
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
            val items = results.map { StationMediaItems.build(this@PlaybackService, it) }
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
            mediaItems.mapNotNull { item -> resolve(item) }.toMutableList()
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

    private companion object {
        const val TAG = "AALTO_AUTO"
        const val ROOT_ID = "aalto_root"
        const val MINE_ID = "aalto_mine"
        const val RECENT_ID = "aalto_recent"
        const val POPULAR_ID = "aalto_popular"
    }
}
