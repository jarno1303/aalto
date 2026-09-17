package fi.aalto.radio.playback

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import fi.aalto.radio.StationLogoResolver
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.FileNotFoundException

/**
 * Serves station logos as content://fi.aalto.radio.logos/<station id>, the
 * form Android Auto, the media notification and other apps can load.
 * Read-only; it only ever returns logo images.
 */
class AaltoLogoProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = "image/png"

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Read only")
        val context = context ?: throw FileNotFoundException("No context")
        val stationId = uri.lastPathSegment?.takeIf { it.isNotBlank() }
            ?: throw FileNotFoundException("No station")
        // Binder thread: blocking is fine here, but never for long.
        val file = runBlocking {
            val station = withTimeoutOrNull(LOOKUP_TIMEOUT_MS) { StationLookup(context).byId(stationId) }
                ?: return@runBlocking null
            val logo = withTimeoutOrNull(LOOKUP_TIMEOUT_MS) { StationLogoResolver.resolveFile(context, station) }
            // Car screens crop to a square: pad wide logos, and never show an
            // empty tile - use the station's own coloured initials instead.
            logo?.let { LogoTiles.square(context, it) } ?: LogoTiles.initials(context, station)
        } ?: throw FileNotFoundException("No logo for $stationId")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    private companion object {
        const val LOOKUP_TIMEOUT_MS = 8_000L
    }
}
