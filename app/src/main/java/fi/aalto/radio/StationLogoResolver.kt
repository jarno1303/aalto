package fi.aalto.radio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONArray
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/** Resolves a station logo without putting disk or network work on the UI path. */
object StationLogoResolver {
    private const val CONNECT_TIMEOUT_MS = 4_000
    private const val READ_TIMEOUT_MS = 6_000
    private const val MAX_BYTES = 1024 * 1024
    private const val MAX_REDIRECTS = 4
    private const val TARGET_LOGO_PX = 384
    private const val USER_AGENT = "AaltoRadio/0.1 (Android; station logos)"
    private const val CACHE_DIRECTORY = "station-logos"

    private val memoryCache = ConcurrentHashMap<String, ImageBitmap>()
    private val failedKeys = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    suspend fun resolve(
        context: Context,
        stationUuid: String,
        logoUrl: String?
    ): ImageBitmap? = resolve(context, stationUuid, listOfNotNull(logoUrl))

    suspend fun resolve(
        context: Context,
        stationUuid: String,
        logoUrls: List<String>
    ): ImageBitmap? = withContext(Dispatchers.IO) {
        logoUrls.asSequence()
            .mapNotNull(::normalizeUrl)
            .distinct()
            .mapNotNull { normalizedUrl ->
                val key = stationLogoCacheKey(stationUuid, normalizedUrl)
                memoryCache[key]?.let { return@mapNotNull it }
                if (key in failedKeys) return@mapNotNull null

                val cacheFile = cacheFile(context, key)
                decodeBitmap(cacheFile)?.let { bitmap ->
                    val image = bitmap.asImageBitmap()
                    memoryCache[key] = image
                    return@mapNotNull image
                }

                download(normalizedUrl)?.let { bitmap ->
                    val imageBitmap = bitmap.asImageBitmap()
                    memoryCache[key] = imageBitmap
                    writeCacheAtomically(cacheFile, bitmap)
                    imageBitmap
                } ?: run {
                    failedKeys += key
                    null
                }
            }
            .firstOrNull()
    }

    private const val LOOKUP_PREFS = "aalto_logo_lookup"
    private const val LOOKUP_RETRY_MS = 7L * 24 * 60 * 60 * 1000
    private const val LOOKUP_BASE_URL = "https://all.api.radio-browser.info"

    /**
     * Finds a logo URL for a station that has none (the built-in stations),
     * by asking Radio Browser for the same station name. The answer is kept in
     * SharedPreferences; a miss is retried after a week.
     */
    suspend fun lookupLogoUrls(context: Context, station: RadioStation): List<String> = withContext(Dispatchers.IO) {
        val prefs = context.applicationContext.getSharedPreferences(LOOKUP_PREFS, Context.MODE_PRIVATE)
        val key = "logos2:${station.id}"
        prefs.getString(key, null)?.let { stored ->
            if (stored.startsWith("miss:")) {
                val at = stored.removePrefix("miss:").toLongOrNull() ?: 0L
                if (System.currentTimeMillis() - at < LOOKUP_RETRY_MS) return@withContext emptyList()
            } else {
                return@withContext stored.split('\n').filter { it.isNotBlank() }
            }
        }

        // Network errors are not stored, so an offline start does not block the lookup for a week.
        val result = runCatching { searchLogo(station) }
        if (result.isFailure) return@withContext emptyList()
        val found = result.getOrNull().orEmpty()
        prefs.edit()
            .putString(key, if (found.isEmpty()) "miss:${System.currentTimeMillis()}" else found.joinToString("\n"))
            .apply()
        found
    }

    private fun searchLogo(station: RadioStation): List<String> {
        val wanted = normalizeName(station.name)
        if (wanted.isEmpty()) return emptyList()
        val url = LOOKUP_BASE_URL + "/json/stations/search" +
            "?name=" + URLEncoder.encode(station.name, "UTF-8") +
            "&countrycode=" + URLEncoder.encode(station.countryCode, "UTF-8") +
            "&order=votes&reverse=true&hidebroken=true&limit=20"
        var connection: HttpURLConnection? = null
        try {
            connection = URL(url).openConnection() as? HttpURLConnection ?: return emptyList()
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode !in 200..299) error("Radio Browser HTTP ${connection.responseCode}")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val array = JSONArray(body)
            // Several candidates: some favicons are SVG or broken, so the
            // resolver tries them in order until one decodes.
            val exact = mutableListOf<String>()
            val prefix = mutableListOf<String>()
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val favicon = item.optString("favicon").trim()
                if (normalizeUrl(favicon) == null) continue
                if (favicon.substringBefore('?').endsWith(".svg", ignoreCase = true)) continue
                val name = normalizeName(item.optString("name"))
                when {
                    name == wanted -> exact += favicon
                    name.startsWith(wanted) -> prefix += favicon
                }
            }
            return (exact + prefix).distinct().take(5)
        } finally {
            connection?.disconnect()
        }
    }

    private fun normalizeName(value: String): String =
        value.lowercase().filter { it.isLetterOrDigit() }

    private fun normalizeUrl(value: String?): String? {
        val normalized = value?.trim().orEmpty()
        return normalized.takeIf { it.startsWith("https://") || it.startsWith("http://") }
    }

    private fun cacheFile(context: Context, key: String): File {
        return File(File(context.cacheDir, CACHE_DIRECTORY), "$key.png")
    }

    private fun decodeBitmap(file: File): Bitmap? {
        return if (file.isFile) BitmapFactory.decodeFile(file.absolutePath) else null
    }

    private fun download(url: String): Bitmap? {
        var currentUrl = url
        // HttpURLConnection does not follow http <-> https redirects by itself,
        // and many station favicons live behind exactly such a redirect.
        repeat(MAX_REDIRECTS + 1) {
            var connection: HttpURLConnection? = null
            try {
                connection = URL(currentUrl).openConnection() as? HttpURLConnection ?: return null
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("User-Agent", USER_AGENT)
                connection.setRequestProperty("Accept", "image/*")
                val code = connection.responseCode
                if (code in 300..399) {
                    val location = connection.getHeaderField("Location") ?: return null
                    currentUrl = URL(URL(currentUrl), location).toString()
                    return@repeat
                }
                if (code !in 200..299) return null
                val bytes = connection.inputStream.use(::readBytesLimited) ?: return null
                return decodeScaled(bytes)
            } catch (_: Exception) {
                return null
            } finally {
                connection?.disconnect()
            }
        }
        return null
    }

    private fun decodeScaled(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= TARGET_LOGO_PX &&
            bounds.outHeight / (sample * 2) >= TARGET_LOGO_PX
        ) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        // 1x1 or tiny tracking pixels are not logos; show initials instead.
        return bitmap.takeIf { it.width >= 16 && it.height >= 16 }
    }

    private fun readBytesLimited(input: InputStream): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > MAX_BYTES) return null
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun writeCacheAtomically(file: File, bitmap: Bitmap) {
        runCatching {
            file.parentFile?.mkdirs()
            val temporary = File(file.parentFile, "${file.name}.tmp")
            temporary.outputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
            if (!temporary.renameTo(file)) temporary.delete()
        }
    }
}

internal fun stationLogoCacheKey(stationUuid: String, logoUrl: String): String {
    val input = "${stationUuid.trim()}\u0000${logoUrl.trim()}"
    val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
    return digest.joinToString("") { byte -> "%02x".format(byte) }
}
