package fi.aalto.radio.history

import androidx.media3.common.Metadata
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.extractor.metadata.id3.TextInformationFrame

/**
 * What the stream itself says is playing.
 *
 * This has to be read from the raw [Metadata] events, not from the player's
 * combined MediaMetadata: the MediaItem's own title (the station name) takes
 * precedence there, so the song never appears in it.
 *
 * Internet radio sends this two ways: ICY (icecast, which is nearly all of
 * them) puts "Artist - Title" in one string, and HLS sends ID3 frames.
 */
internal object StreamTrack {

    data class Announcement(val title: String?, val artist: String?)

    fun from(metadata: Metadata): Announcement? {
        var title: String? = null
        var artist: String? = null
        for (index in 0 until metadata.length()) {
            when (val entry = metadata.get(index)) {
                is IcyInfo -> entry.title?.takeIf { it.isNotBlank() }?.let { title = it }
                is TextInformationFrame -> {
                    val value = entry.values.firstOrNull()?.takeIf { it.isNotBlank() }
                    when (entry.id) {
                        "TIT2", "TT2" -> value?.let { title = it }
                        "TPE1", "TP1" -> value?.let { artist = it }
                    }
                }
            }
        }
        if (title == null && artist == null) return null
        return Announcement(title = title, artist = artist)
    }
}
