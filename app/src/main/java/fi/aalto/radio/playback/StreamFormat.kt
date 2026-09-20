package fi.aalto.radio.playback

import android.content.ComponentName
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import fi.aalto.radio.PlaybackService

/** What the stream that is playing actually is, as the decoder sees it. */
internal data class StreamFormat(
    val mimeType: String?,
    val codecs: String?,
    val bitrateKbps: Int?,
    val sampleRateHz: Int?
)

internal object StreamQuality {

    /**
     * "AAC+ · 64 kbps", "MP3 · 128 kbps" or "AAC · 48 kHz". The measured
     * stream wins; the directory's word is used only for what the stream does
     * not tell (radio streams rarely announce their bitrate). Null when
     * nothing is known, so no badge is shown rather than a guess.
     */
    fun label(
        measured: StreamFormat?,
        declaredCodec: String?,
        declaredBitrateKbps: Int?
    ): String? {
        val measuredCodec = codecName(measured?.mimeType, measured?.codecs)
        val listedCodec = declaredCodecName(declaredCodec)
        val codec = measuredCodec ?: listedCodec
        // The directory's bitrate describes its stream; it is only trusted
        // when that stream is the same kind as the one playing.
        val listedMatches = measuredCodec == null || listedCodec == null ||
            listedCodec.removeSuffix("+") == measuredCodec.removeSuffix("+")
        val kbps = measured?.bitrateKbps?.takeIf { it > 0 }
            ?: declaredBitrateKbps?.takeIf { it in 8..1_500 && listedMatches }
        val detail = when {
            kbps != null -> "$kbps kbps"
            measured?.sampleRateHz != null && measured.sampleRateHz > 0 -> kiloHertz(measured.sampleRateHz)
            else -> null
        }
        return listOfNotNull(codec, detail).takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    fun codecName(mimeType: String?, codecs: String?): String? {
        val heAac = codecs?.lowercase()?.let { it.startsWith("mp4a.40.5") || it.startsWith("mp4a.40.29") } == true
        return when (mimeType) {
            MimeTypes.AUDIO_AAC -> if (heAac) "AAC+" else "AAC"
            MimeTypes.AUDIO_MPEG, MimeTypes.AUDIO_MPEG_L2 -> "MP3"
            MimeTypes.AUDIO_OPUS -> "Opus"
            MimeTypes.AUDIO_VORBIS -> "Vorbis"
            MimeTypes.AUDIO_FLAC -> "FLAC"
            MimeTypes.AUDIO_AC3 -> "AC-3"
            null -> null
            else -> null
        }
    }

    fun declaredCodecName(declared: String?): String? {
        val value = declared?.trim()?.uppercase() ?: return null
        return when {
            value.isBlank() || value == "UNKNOWN" -> null
            value.contains("AAC+") || value.contains("HE-AAC") || value.contains("AACP") -> "AAC+"
            value.contains("AAC") -> "AAC"
            value.contains("MP3") || value == "MPEG" -> "MP3"
            value.contains("OPUS") -> "Opus"
            value.contains("OGG") || value.contains("VORBIS") -> "Vorbis"
            value.contains("FLAC") -> "FLAC"
            else -> null
        }
    }

    private fun kiloHertz(hz: Int): String {
        val khz = hz / 1000.0
        val text = if (hz % 1000 == 0) "${hz / 1000}" else String.format(java.util.Locale.getDefault(), "%.1f", khz)
        return "$text kHz"
    }

    fun fromTracks(tracks: Tracks): StreamFormat? {
        val group = tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected } ?: return null
        val index = (0 until group.length).firstOrNull { group.isTrackSelected(it) } ?: 0
        val format = group.getTrackFormat(index)
        val bitrate = listOf(format.averageBitrate, format.bitrate, format.peakBitrate)
            .firstOrNull { it != Format.NO_VALUE && it > 0 }
        return StreamFormat(
            mimeType = format.sampleMimeType,
            codecs = format.codecs,
            bitrateKbps = bitrate?.let { (it + 500) / 1000 },
            sampleRateHz = format.sampleRate.takeIf { it != Format.NO_VALUE && it > 0 }
        )
    }
}

/**
 * Reads the playing stream's format through a read-only controller of its
 * own, so the player itself is not touched. Never controls playback.
 */
@Composable
internal fun rememberStreamFormat(): StreamFormat? {
    val context = LocalContext.current
    var format by remember { mutableStateOf<StreamFormat?>(null) }
    DisposableEffect(context) {
        val appContext = context.applicationContext
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                format = StreamQuality.fromTracks(tracks)
            }
        }
        future.addListener({
            runCatching { future.get() }.getOrNull()?.let { controller ->
                controller.addListener(listener)
                format = StreamQuality.fromTracks(controller.currentTracks)
            }
        }, ContextCompat.getMainExecutor(appContext))
        onDispose {
            if (future.isDone) runCatching { future.get().removeListener(listener) }
            MediaController.releaseFuture(future)
        }
    }
    return format
}
