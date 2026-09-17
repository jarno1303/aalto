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
    private const val CONNECT_TIMEOUT_MS = 2_000
    private const val READ_TIMEOUT_MS = 2_500
    private const val MAX_BYTES = 512 * 1024
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
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(url).openConnection() as? HttpURLConnection ?: return null
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            if (connection.responseCode !in 200..299) return null
            val bytes = connection.inputStream.use(::readBytesLimited) ?: return null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
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
