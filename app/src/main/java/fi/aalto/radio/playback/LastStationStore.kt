package fi.aalto.radio.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

/**
 * The last station that played, so the widget (and a later "play") can start
 * it without the app being open. Tiny, so SharedPreferences are enough.
 */
internal object LastStationStore {
    private const val PREFS = "aalto_last_station"
    private const val KEY_ID = "id"
    private const val KEY_NAME = "name"
    private const val KEY_URL = "url"
    private const val KEY_STREAMS = "streams"

    fun save(context: Context, item: MediaItem) {
        val url = item.localConfiguration?.uri?.toString() ?: return
        val name = item.mediaMetadata.title?.toString()
        val streams = StreamCandidates.fromItem(item)
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_ID, item.mediaId)
            .putString(KEY_NAME, name)
            .putString(KEY_URL, url)
            .putString(KEY_STREAMS, streams.joinToString("\n"))
            .apply()
    }

    fun name(context: Context): String? =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_NAME, null)

    fun load(context: Context): MediaItem? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val id = prefs.getString(KEY_ID, null) ?: return null
        val url = prefs.getString(KEY_URL, null) ?: return null
        val name = prefs.getString(KEY_NAME, null) ?: "Aalto Radio"
        val streams = prefs.getString(KEY_STREAMS, null)
            ?.split('\n')
            ?.filter { it.isNotBlank() }
            .orEmpty()
        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(name)
                    .setArtist("Aalto")
                    .setArtworkUri(StationMediaItems.logoUri(id))
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
                    .setExtras(StreamCandidates.extras(streams))
                    .build()
            )
            .build()
    }
}
