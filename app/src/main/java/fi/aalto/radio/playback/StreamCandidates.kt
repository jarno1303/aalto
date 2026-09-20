package fi.aalto.radio.playback

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import fi.aalto.radio.RadioStation
import java.net.HttpURLConnection
import java.net.URL

/**
 * Stream addresses for one station, best first. The list travels with the
 * MediaItem (metadata extras), so the player can move to the next address by
 * itself when one fails (AGENTS.md §15: "Aalto works").
 */
internal object StreamCandidates {
    const val EXTRA_STREAMS = "fi.aalto.radio.streams"

    fun forStation(station: RadioStation, remembered: String?): List<String> =
        (listOfNotNull(
            remembered,
            station.preferredStreamUrl,
            station.lastKnownWorkingStreamUrl,
            station.streamUrl
        ) + station.streamAlternatives)
            .map { it.trim() }
            .filter { it.startsWith("http://") || it.startsWith("https://") }
            .distinct()

    fun fromItem(item: MediaItem): List<String> {
        // A rewound station plays from the ring buffer: never a stream address.
        val current = item.localConfiguration?.uri?.toString()?.takeUnless { it.startsWith("aalto-timeshift:") }
        val extras = item.mediaMetadata.extras?.getStringArrayList(EXTRA_STREAMS).orEmpty()
        return (listOfNotNull(current) + extras).distinct()
    }

    fun extras(candidates: List<String>): Bundle =
        Bundle().apply { putStringArrayList(EXTRA_STREAMS, ArrayList(candidates)) }

    /** .pls / .m3u are playlists that point to the real stream (.m3u8 is HLS and plays as is). */
    fun isPlaylistUrl(url: String): Boolean {
        val path = runCatching { Uri.parse(url).path.orEmpty() }.getOrDefault("").lowercase()
        return path.endsWith(".pls") || path.endsWith(".m3u")
    }
}

/** Remembers the address that last worked for each station. */
internal object StreamMemory {
    private const val PREFS = "aalto_stream_memory"

    fun get(context: Context, stationId: String): String? =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(stationId, null)

    /** Forget it, e.g. after the user gave the station a new address. */
    fun clear(context: Context, stationId: String) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(stationId).apply()
    }

    fun put(context: Context, stationId: String, url: String) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(stationId, null) != url) {
            prefs.edit().putString(stationId, url).apply()
        }
    }
}

/** Opens .pls / .m3u playlists and returns the stream addresses inside. */
internal object PlaylistResolver {
    private const val MAX_BYTES = 64 * 1024

    fun resolve(url: String): List<String> {
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "AaltoRadio/0.1 (Android)")
            if (connection.responseCode !in 200..299) return emptyList()
            val text = connection.inputStream.use { input ->
                val bytes = input.readNBytesCompat(MAX_BYTES)
                String(bytes, Charsets.UTF_8)
            }
            parse(text)
        } catch (_: Exception) {
            emptyList()
        } finally {
            connection?.disconnect()
        }
    }

    fun parse(text: String): List<String> =
        text.lineSequence()
            .map { it.trim() }
            .mapNotNull { line ->
                when {
                    line.startsWith("#") -> null
                    line.contains("=") && line.substringBefore("=").lowercase().startsWith("file") ->
                        line.substringAfter("=").trim()
                    else -> line
                }
            }
            .filter { it.startsWith("http://") || it.startsWith("https://") }
            .distinct()
            .toList()

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val buffer = ByteArray(limit)
        var total = 0
        while (total < limit) {
            val count = read(buffer, total, limit - total)
            if (count < 0) break
            total += count
        }
        return buffer.copyOf(total)
    }
}

/** One place that turns a station into a playable MediaItem (app, car, widget, alarm). */
internal object StationMediaItems {
    const val LOGO_AUTHORITY = "fi.aalto.radio.logos"

    fun logoUri(stationId: String): Uri =
        Uri.Builder()
            .scheme("content")
            .authority(LOGO_AUTHORITY)
            .appendPath(stationId)
            .build()

    /**
     * @param browseActions ids of car "browse actions" (e.g. the heart) shown
     * on this station's tile; null for items that are only played.
     */
    fun build(context: Context, station: RadioStation, browseActions: List<String>? = null): MediaItem {
        val candidates = StreamCandidates.forStation(station, StreamMemory.get(context, station.id))
        val url = candidates.firstOrNull() ?: station.preferredStreamUrl
        return MediaItem.Builder()
            .setMediaId(station.id)
            .setUri(url)
            .setMediaMetadata(metadata(station, candidates, browseActions))
            .build()
    }

    /** Second line on the car screen and notification: "pop, nuoret · Helsinki, Suomi". */
    fun subtitle(station: RadioStation): String =
        fi.aalto.radio.stationMetadataLine(station).ifBlank { station.description }.ifBlank { "Aalto" }

    fun metadata(
        station: RadioStation,
        candidates: List<String>,
        browseActions: List<String>? = null
    ): MediaMetadata =
        MediaMetadata.Builder()
            .setTitle(station.name)
            .setArtist(subtitle(station))
            .setStation(station.name)
            .setSubtitle(subtitle(station))
            .setArtworkUri(logoUri(station.id))
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
            .setExtras(
                StreamCandidates.extras(candidates).apply {
                    if (browseActions != null) {
                        putStringArrayList(BrowseActions.KEY_ITEM_ACTION_IDS, ArrayList(browseActions))
                    }
                }
            )
            .build()

    fun folder(id: String, title: String): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS)
                    .build()
            )
            .build()
}
