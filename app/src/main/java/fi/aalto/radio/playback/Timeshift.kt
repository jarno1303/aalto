package fi.aalto.radio.playback

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener
import fi.aalto.radio.plus.Plus
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import kotlin.math.max
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** What the app shows about rewinding. */
internal data class TimeshiftState(
    /** This station can be rewound (an MP3 or AAC stream, some audio kept). */
    val available: Boolean = false,
    /** How far behind live the listener is; 0 when live. */
    val behindMs: Long = 0L,
    /** How far back one can go right now. */
    val maxBackMs: Long = 0L,
    /** At the free limit: going further back is Aalto Plus. */
    val atFreeLimit: Boolean = false
) {
    val live: Boolean get() = behindMs <= 0L
}

/**
 * Rewinding live radio: "what did the host just say?".
 *
 * While a station plays live, the bytes the player reads anyway are also
 * written to a ring file ([RecordingDataSource]); nothing extra is
 * downloaded and the live path is unchanged (if keeping fails, keeping just
 * stops). Rewinding swaps the playing address for the ring
 * ([TimeshiftReaderDataSource]) and keeps the station coming in with a
 * connection of its own ([TimeshiftRecorder]); "Suora" swaps the real
 * address back. Any error while rewound falls back to the live stream
 * through the player's normal fallback.
 *
 * 30 seconds are free; Aalto Plus keeps 30 minutes (docs/AALTO_PLUS.md),
 * checked once here.
 */
@androidx.annotation.OptIn(UnstableApi::class)
internal object Timeshift {
    const val SCHEME = "aalto-timeshift"
    const val FREE_SECONDS = 30
    const val PLUS_SECONDS = 30 * 60
    /** The player's own read-ahead and the connect burst need room too. */
    private const val MARGIN_SECONDS = 60
    /** Upper bound for the ring: 384 kbps. */
    private const val MAX_BYTE_RATE = 48_000L
    private const val TAG = "AALTO_TIMESHIFT"

    /** How the player is reached: supplied by the playback service. */
    interface Host {
        val player: Player
        fun currentUri(): String?
        fun replaceUri(uri: String)
    }

    private val main = Handler(Looper.getMainLooper())
    private var host: Host? = null
    @Volatile
    private var buffer: TimeshiftBuffer? = null
    private var recorder: TimeshiftRecorder? = null
    private var liveUri: String? = null
    private var maxSeconds = FREE_SECONDS
    private var plus = false

    @Volatile
    internal var readerPosition = 0L
    private var lastLoggedSeam = -1L
    /** How far behind live the listener is, in time. */
    private var behindMs = 0L
    private var lastTickMs = 0L

    /** Song titles met while playing from the ring. Main thread. */
    var titleListener: ((ByteArray) -> Unit)? = null

    private val _state = MutableStateFlow(TimeshiftState())
    val state: StateFlow<TimeshiftState> = _state

    fun buffer(context: Context): TimeshiftBuffer =
        buffer ?: TimeshiftBuffer(File(context.cacheDir, "timeshift.buf")).also { buffer = it }

    fun attach(context: Context, host: Host) {
        this.host = host
        buffer(context)
    }

    fun detach() {
        stopRecorder()
        host = null
        _state.value = TimeshiftState()
    }

    fun isTimeshiftUri(uri: String?): Boolean = uri?.startsWith("$SCHEME:") == true

    /** A different station started: forget the old one. Main thread. */
    fun stationChanged(context: Context) {
        stopRecorder()
        liveUri = null
        plus = Plus.access(context).isActive()
        maxSeconds = if (plus) PLUS_SECONDS else FREE_SECONDS
        buffer(context).reset((maxSeconds + MARGIN_SECONDS) * MAX_BYTE_RATE)
        behindMs = 0L
        _state.value = TimeshiftState()
    }

    /** Where the listener is in the ring, allowing for the player's read-ahead. */
    private fun heardOffset(buf: TimeshiftBuffer, player: Player, rewound: Boolean): Long {
        val aheadMs = (player.bufferedPosition - player.currentPosition).coerceIn(0L, 60_000L)
        val top = if (rewound) readerPosition else buf.head
        return max(0L, top - (aheadMs * buf.byteRate / 1000.0).toLong())
    }

    private fun oldestAllowed(buf: TimeshiftBuffer): Long =
        max(buf.tail, buf.head - (maxSeconds * buf.byteRate).toLong())

    /** Back [seconds], as far as the buffer (and the free limit) allows. Main thread. */
    fun back(context: Context, seconds: Int) {
        val h = host ?: return
        val buf = buffer(context)
        if (buf.format == null) return
        val current = h.currentUri() ?: return
        val rewound = isTimeshiftUri(current)
        val already = if (rewound) behindMs else 0L
        // Never further back than the limit, counted in time, not bytes.
        val allowedMs = (maxSeconds * 1000L - already).coerceAtLeast(0L)
        val wantedMs = minOf(seconds * 1000L, allowedMs)
        if (wantedMs < 1_000L) return
        val heard = heardOffset(buf, h.player, rewound)
        val target = max(buf.tail, heard - (wantedMs / 1000.0 * buf.byteRate).toLong())
        val stepMs = ((heard - target) / buf.byteRate * 1000).toLong()
        if (stepMs < 1_000L) return // less than a second kept: nothing to do
        if (!rewound) {
            liveUri = current
            startRecorder(current)
        }
        behindMs = already + stepMs
        lastTickMs = android.os.SystemClock.elapsedRealtime()
        playFrom(h, buf, target)
        publish(buf)
    }

    /** Forward [seconds]; reaching the live edge goes live. Main thread. */
    fun forward(context: Context, seconds: Int) {
        val h = host ?: return
        val buf = buffer(context)
        if (!isTimeshiftUri(h.currentUri())) return
        val remainingMs = behindMs - seconds * 1000L
        if (remainingMs < 3_000L) {
            live()
            return
        }
        val heard = heardOffset(buf, h.player, rewound = true)
        val target = heard + (seconds * buf.byteRate).toLong()
        if (target >= buf.head - 3 * buf.byteRate) {
            live()
            return
        }
        behindMs = remainingMs
        playFrom(h, buf, target)
        publish(buf)
    }

    /** Back to the live broadcast. Main thread. */
    fun live() {
        val h = host ?: return
        val uri = liveUri
        stopRecorder()
        if (uri != null && isTimeshiftUri(h.currentUri())) h.replaceUri(uri)
        liveUri = null
        behindMs = 0L
        buffer?.let { publish(it) }
    }

    private fun playFrom(h: Host, buf: TimeshiftBuffer, offset: Long) {
        readerPosition = offset
        val uri = Uri.Builder()
            .scheme(SCHEME)
            .authority("ring")
            .appendPath("stream.${buf.format}")
            .appendQueryParameter("from", offset.toString())
            .appendQueryParameter("n", System.nanoTime().toString())
            .build()
            .toString()
        h.replaceUri(uri)
        // Show the song that was on at that moment.
        buf.titleAt(offset)?.let { raw -> titleListener?.invoke(raw) }
    }

    private fun startRecorder(url: String) {
        stopRecorder()
        val buf = buffer ?: return
        recorder = TimeshiftRecorder(url, buf).also { it.start() }
    }

    private fun stopRecorder() {
        recorder?.shutdown()
        recorder = null
    }

    /** Refreshes [state]; called every second by the service. Main thread. */
    fun tick(context: Context) {
        val h = host ?: return
        val buf = buffer(context)
        val rewound = isTimeshiftUri(h.currentUri())
        if (!rewound && recorder != null) {
            // The player went back to the live address by itself (an error
            // while rewound): stop the extra connection.
            stopRecorder()
            liveUri = null
        }
        val rate = buf.byteRate
        val seam = buf.lastSeamDroppedBytes
        if (seam != lastLoggedSeam) {
            lastLoggedSeam = seam
            if (seam >= 0) log("seam: dropped ${seam} repeated bytes (${"%.1f".format(seam / rate)} s)")
        }
        // How far behind is counted in time: while the radio plays, live and
        // listener move together and the gap stays put; it grows only while
        // playback stands still (buffering). Counting bytes instead made the
        // number jump with every chunk the server sends.
        val now = android.os.SystemClock.elapsedRealtime()
        if (rewound) {
            // Only a stall adds to the gap: after a pause playback resumes live anyway.
            if (!h.player.isPlaying && h.player.playWhenReady && lastTickMs > 0L) behindMs += now - lastTickMs
        } else {
            behindMs = 0L
        }
        lastTickMs = now
        publish(buf)
    }

    private fun publish(buf: TimeshiftBuffer) {
        val rewound = isTimeshiftUri(host?.currentUri())
        val kept = ((buf.head - oldestAllowed(buf)) / buf.byteRate * 1000).toLong()
        // Whole seconds, so the screen changes only when the number does.
        val behind = if (rewound) (((behindMs + 500) / 1000) * 1000).coerceAtLeast(1_000L) else 0L
        val maxBack = if (rewound) (maxSeconds * 1000L - behindMs).coerceAtLeast(0L) else 0L
        _state.value = TimeshiftState(
            available = buf.format != null && kept >= 5_000L,
            behindMs = behind,
            maxBackMs = maxBack,
            atFreeLimit = !plus && rewound && maxBack < 1_000L
        )
    }

    internal fun postTitle(raw: ByteArray) {
        main.post { titleListener?.invoke(raw) }
    }

    fun formatFor(contentType: String?): String? = CuttableStreams.formatFor(contentType)

    internal fun header(headers: Map<String, List<String>>, name: String): String? =
        headers.entries.firstOrNull { it.key?.equals(name, ignoreCase = true) == true }?.value?.firstOrNull()

    internal fun log(message: String) {
        Log.d(TAG, message)
    }
}

/**
 * The player's data source for everything: ring addresses read the ring,
 * anything else goes to the normal source, with live MP3/AAC streams also
 * written to the ring on the way.
 */
@androidx.annotation.OptIn(UnstableApi::class)
internal class TimeshiftDataSourceFactory(
    private val context: Context,
    private val upstream: DataSource.Factory
) : DataSource.Factory {
    override fun createDataSource(): DataSource = RoutingDataSource(context, upstream.createDataSource())
}

@androidx.annotation.OptIn(UnstableApi::class)
private class RoutingDataSource(
    private val context: Context,
    private val upstream: DataSource
) : DataSource {
    private var active: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val source = if (dataSpec.uri.scheme == Timeshift.SCHEME) {
            TimeshiftReaderDataSource(Timeshift.buffer(context))
        } else {
            RecordingDataSource(upstream, Timeshift.buffer(context))
        }
        active = source
        return source.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        active?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT

    override fun getUri(): Uri? = active?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = active?.responseHeaders ?: emptyMap()

    override fun close() {
        try {
            active?.close()
        } finally {
            active = null
        }
    }
}

/** Passes the live stream through untouched, keeping a copy in the ring. */
@androidx.annotation.OptIn(UnstableApi::class)
private class RecordingDataSource(
    private val upstream: DataSource,
    private val ring: TimeshiftBuffer
) : DataSource {
    private var writer = 0L
    private var stripper: IcyStripper? = null

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val length = upstream.open(dataSpec)
        stripper = null
        runCatching {
            // Live streams only: no known length, from the start, cuttable format.
            if (length == C.LENGTH_UNSET.toLong() && dataSpec.position == 0L) {
                val headers = upstream.responseHeaders
                val format = Timeshift.formatFor(Timeshift.header(headers, "Content-Type"))
                if (format != null) {
                    val metaint = Timeshift.header(headers, "icy-metaint")?.trim()?.toIntOrNull() ?: 0
                    writer = ring.beginWriter(format)
                    stripper = IcyStripper(metaint)
                }
            }
        }
        return length
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val n = upstream.read(buffer, offset, length)
        val strip = stripper
        if (n > 0 && strip != null) {
            try {
                strip.feed(
                    buffer, offset, n,
                    audio = { data, o, l -> ring.write(writer, data, o, l) },
                    metadata = { raw -> ring.title(writer, raw) }
                )
            } catch (error: Exception) {
                // Keeping a copy must never cost the live radio anything.
                stripper = null
                Timeshift.log("recording stopped: $error")
            }
        }
        return n
    }

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() = upstream.close()
}

/** Plays from the ring, waiting at the live edge like a live stream does. */
@androidx.annotation.OptIn(UnstableApi::class)
private class TimeshiftReaderDataSource(private val ring: TimeshiftBuffer) : DataSource {
    private var uri: Uri? = null
    private var position = 0L

    override fun addTransferListener(transferListener: TransferListener) = Unit

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        val from = dataSpec.uri.getQueryParameter("from")?.toLongOrNull() ?: ring.head
        position = max(from, ring.tail)
        Timeshift.readerPosition = position
        return C.LENGTH_UNSET.toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        var waited = 0L
        while (true) {
            val n = try {
                ring.read(position, buffer, offset, length, WAIT_MS)
            } catch (_: InterruptedException) {
                throw InterruptedIOException("timeshift read interrupted")
            }
            when {
                n > 0 -> {
                    ring.titlesIn(position, position + n).forEach(Timeshift::postTitle)
                    position += n
                    Timeshift.readerPosition = position
                    return n
                }
                n == TimeshiftBuffer.TOO_OLD -> position = ring.tail
                else -> {
                    waited += WAIT_MS
                    if (Thread.currentThread().isInterrupted) throw InterruptedIOException()
                    // Nothing new for a long time: let the player fall back to live.
                    if (waited >= STARVED_MS) throw IOException("timeshift starved")
                }
            }
        }
    }

    override fun getUri(): Uri? = uri

    override fun close() = Unit

    private companion object {
        const val WAIT_MS = 250L
        const val STARVED_MS = 20_000L
    }
}

/**
 * Keeps the station coming into the ring while the listener is rewound (the
 * player is reading the ring then, not the network). One connection, like
 * the player's own; reconnects after a network blip.
 */
@androidx.annotation.OptIn(UnstableApi::class)
private class TimeshiftRecorder(
    private val url: String,
    private val ring: TimeshiftBuffer
) : Thread("aalto-timeshift-recorder") {
    @Volatile
    private var running = true
    @Volatile
    private var source: DataSource? = null

    init {
        isDaemon = true
    }

    fun shutdown() {
        running = false
        ring.endWriters()
        runCatching { source?.close() }
        interrupt()
    }

    override fun run() {
        val bytes = ByteArray(16 * 1024)
        var failures = 0
        while (running && failures < MAX_FAILURES) {
            val http = DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(10_000)
                .setReadTimeoutMs(15_000)
                .setDefaultRequestProperties(mapOf("Icy-MetaData" to "1"))
                .createDataSource()
            source = http
            try {
                http.open(DataSpec(Uri.parse(url)))
                val headers = http.responseHeaders
                val format = Timeshift.formatFor(Timeshift.header(headers, "Content-Type")) ?: run {
                    Timeshift.log("recorder: not a cuttable stream")
                    return
                }
                val metaint = Timeshift.header(headers, "icy-metaint")?.trim()?.toIntOrNull() ?: 0
                val writer = ring.beginWriter(format)
                val stripper = IcyStripper(metaint)
                failures = 0
                while (running) {
                    val n = http.read(bytes, 0, bytes.size)
                    if (n == C.RESULT_END_OF_INPUT) break
                    if (n > 0) {
                        stripper.feed(
                            bytes, 0, n,
                            audio = { data, o, l -> ring.write(writer, data, o, l) },
                            metadata = { raw -> ring.title(writer, raw) }
                        )
                    }
                }
            } catch (error: Exception) {
                if (!running) return
                failures++
                Timeshift.log("recorder: $error, retry $failures")
                try {
                    Thread.sleep(RETRY_MS)
                } catch (_: InterruptedException) {
                    return
                }
            } finally {
                runCatching { http.close() }
            }
        }
    }

    private companion object {
        const val MAX_FAILURES = 10
        const val RETRY_MS = 2_000L
    }
}
