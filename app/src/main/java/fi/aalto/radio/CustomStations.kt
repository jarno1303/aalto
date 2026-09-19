package fi.aalto.radio

import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The user's own stations: any stream address they paste (Aalto Plus, see
 * docs/AALTO_PLUS.md). Stored and synced exactly like a catalog station; only
 * the id prefix tells them apart.
 */
internal object CustomStations {

    const val ID_PREFIX = "custom-"

    fun isCustom(stationId: String): Boolean = stationId.startsWith(ID_PREFIX)

    fun newId(): String = ID_PREFIX + UUID.randomUUID().toString()

    /** "stream.example.fi/radio" or "http://…": an http(s) address, or null. */
    fun normalizeUrl(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
        val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
        val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme != "http" && scheme != "https") return null
        if (uri.host.isNullOrBlank()) return null
        return withScheme
    }

    /** A name when the user gave none: the stream's own, else the host. */
    fun defaultName(url: String, announcedName: String?): String {
        announcedName?.trim()?.takeIf { it.isNotBlank() && it.length <= 60 }?.let { return it }
        val host = runCatching { URI(url).host }.getOrNull().orEmpty().removePrefix("www.")
        return host.ifBlank { url }
    }

    fun station(id: String, name: String, url: String): RadioStation {
        val initials = name.split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .ifBlank { "A" }
        val host = runCatching { URI(url).host }.getOrNull().orEmpty().removePrefix("www.")
        return RadioStation(
            id = id,
            name = name,
            description = host,
            initials = initials,
            logoColorArgb = 0xFF1769FF,
            streamUrl = url,
            preferredStreamUrl = url,
            countryCode = "",
            tags = emptyList(),
            category = ""
        )
    }

    /**
     * Whether a response looks like radio: audio, an HLS or pls / m3u playlist,
     * or a generic byte stream from a Shoutcast / Icecast server.
     */
    fun looksLikeStream(contentType: String?, url: String, hasIcyHeaders: Boolean): Boolean {
        if (hasIcyHeaders) return true
        val type = contentType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT).orEmpty()
        if (type.startsWith("audio/")) return true
        if (type in STREAM_TYPES) return true
        val path = runCatching { URI(url).path }.getOrNull().orEmpty().lowercase(Locale.ROOT)
        return PLAYLIST_ENDINGS.any { path.endsWith(it) } &&
            (type.isEmpty() || type == "application/octet-stream" || type.startsWith("text/plain"))
    }

    private val STREAM_TYPES = setOf(
        "application/ogg",
        "application/vnd.apple.mpegurl",
        "application/x-mpegurl",
        "audio/x-mpegurl",
        "audio/x-scpls",
        "application/pls+xml",
        "video/mp2t"
    )
    private val PLAYLIST_ENDINGS = listOf(".m3u8", ".m3u", ".pls")
}

internal sealed class StreamProbeResult {
    data class Ok(val announcedName: String?) : StreamProbeResult()
    data object NotRadio : StreamProbeResult()
    data object Unreachable : StreamProbeResult()
}

/**
 * Checks an address before it is saved, so a typo never becomes a station
 * that fails in the car. Reads the headers and a few bytes, not the stream.
 */
internal object StreamProbe {

    suspend fun check(url: String, timeoutMs: Int = 7_000): StreamProbeResult = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                instanceFollowRedirects = true
                setRequestProperty("Icy-MetaData", "1")
                setRequestProperty("User-Agent", "Aalto/1 (Android radio)")
            }
            try {
                val code = connection.responseCode
                if (code !in 200..299) return@runCatching StreamProbeResult.NotRadio
                val finalUrl = connection.url.toString()
                val icyName = connection.getHeaderField("icy-name")
                val hasIcy = icyName != null || connection.getHeaderField("icy-br") != null
                val gotBytes = connection.inputStream.use { it.read(ByteArray(512)) > 0 }
                if (gotBytes && CustomStations.looksLikeStream(connection.contentType, finalUrl, hasIcy)) {
                    StreamProbeResult.Ok(icyName)
                } else {
                    StreamProbeResult.NotRadio
                }
            } finally {
                connection.disconnect()
            }
        }.getOrElse { StreamProbeResult.Unreachable }
    }
}
