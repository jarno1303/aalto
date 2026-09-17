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
