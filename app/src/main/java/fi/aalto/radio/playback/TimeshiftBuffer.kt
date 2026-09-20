package fi.aalto.radio.playback

import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.Charset
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import kotlin.math.max
import kotlin.math.min

/**
 * Splits an Icecast/Shoutcast stream into audio and its in-band song titles
 * ("ICY" metadata: every [metaint] audio bytes, one length byte and that
 * many 16-byte blocks of text). With [metaint] 0 everything is audio.
 * Pure logic: tested on the JVM.
 */
internal class IcyStripper(private val metaint: Int) {
    private var audioLeft = metaint
    private var meta: ByteArray? = null
    private var metaPos = 0

    fun feed(
        data: ByteArray,
        offset: Int,
        length: Int,
        audio: (ByteArray, Int, Int) -> Unit,
        metadata: (ByteArray) -> Unit
    ) {
        if (metaint <= 0) {
            if (length > 0) audio(data, offset, length)
            return
        }
        var i = offset
        val end = offset + length
        while (i < end) {
            val pending = meta
            when {
                audioLeft > 0 -> {
                    val n = min(audioLeft, end - i)
                    audio(data, i, n)
                    audioLeft -= n
                    i += n
                }
                pending == null -> {
                    val size = (data[i].toInt() and 0xFF) * 16
                    i++
                    if (size == 0) {
                        audioLeft = metaint
                    } else {
                        meta = ByteArray(size)
                        metaPos = 0
                    }
                }
                else -> {
                    val n = min(pending.size - metaPos, end - i)
                    System.arraycopy(data, i, pending, metaPos, n)
                    metaPos += n
                    i += n
                    if (metaPos == pending.size) {
                        metadata(pending)
                        meta = null
                        audioLeft = metaint
                    }
                }
            }
        }
    }
}

/**
 * Streams that can be started from any byte (the decoder finds the next
 * frame): MP3 and ADTS AAC. Ogg, FLAC and HLS cannot, so they are not kept.
 */
internal object CuttableStreams {
    fun formatFor(contentType: String?): String? {
        val type = contentType?.substringBefore(';')?.trim()?.lowercase() ?: return null
        return when (type) {
            "audio/mpeg", "audio/mp3", "audio/mpeg3", "audio/x-mpeg" -> "mp3"
            "audio/aac", "audio/aacp", "audio/x-aac", "audio/aac-adts", "audio/x-aacp" -> "aac"
            else -> null
        }
    }
}

/** "StreamTitle='Artist - Song';StreamUrl='';" as title and url. */
internal object IcyText {
    private val FIELD = Regex("(.+?)='(.*?)';", RegexOption.DOT_MATCHES_ALL)

    fun parse(raw: ByteArray): Pair<String?, String?> {
        val text = decode(raw).trimEnd('\u0000')
        var title: String? = null
        var url: String? = null
        FIELD.findAll(text).forEach { match ->
            when (match.groupValues[1].trim().lowercase()) {
                "streamtitle" -> title = match.groupValues[2]
                "streamurl" -> url = match.groupValues[2]
            }
        }
        return title to url
    }

    private fun decode(raw: ByteArray): String = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .decode(java.nio.ByteBuffer.wrap(raw))
            .toString()
    } catch (_: CharacterCodingException) {
        String(raw, Charset.forName("ISO-8859-1"))
    }
}

/**
 * The last minutes of the playing station, as audio bytes in a ring file.
 *
 * Offsets are absolute (bytes since the station started), so the player can
 * ask for "from here" while new audio keeps arriving. Only one writer at a
 * time: each new connection gets a new writer id and an older one's late
 * bytes are ignored. Song titles are kept with the offset they arrived at.
 *
 * Every method is safe to call from any thread.
 */
internal class TimeshiftBuffer(
    private val file: File,
    private val clock: () -> Long = { android.os.SystemClock.elapsedRealtime() }
) {
    private val lock = Object()
    private var raf: RandomAccessFile? = null
    private var capacity = 0L
    private var writerId = 0L
    private var nextWriterId = 1L
    private var rateMarkMs = 0L
    private var rateMarkBytes = 0L
    private var rateWriterStartMs = 0L
    private var estimatedByteRate = DEFAULT_BYTE_RATE
    private val titles = ArrayDeque<Pair<Long, ByteArray>>()
    private var aligner: SeamAligner? = null

    /** Bytes the last new connection repeated and were dropped; for the log. */
    @Volatile
    var lastSeamDroppedBytes = -1L
        private set

    /** Bytes written since the station started; the live edge. */
    @Volatile
    var head = 0L
        private set

    /** "mp3" or "aac"; null when this stream cannot be kept. */
    @Volatile
    var format: String? = null
        private set

    val tail: Long get() = synchronized(lock) { max(0L, head - capacity) }

    /** Audio bytes per second, measured once the connect burst has passed. */
    val byteRate: Double get() = synchronized(lock) { estimatedByteRate }

    /** Empties the buffer for a new station. */
    fun reset(capacityBytes: Long) {
        synchronized(lock) {
            runCatching { raf?.close() }
            raf = null
            capacity = capacityBytes
            head = 0L
            writerId = nextWriterId++
            format = null
            titles.clear()
            estimatedByteRate = DEFAULT_BYTE_RATE
            runCatching { if (file.exists()) file.delete() }
            lock.notifyAll()
        }
    }

    /** A new connection begins to write; earlier connections stop counting. */
    fun beginWriter(streamFormat: String): Long {
        synchronized(lock) {
            if (format != null && format != streamFormat) {
                // Another kind of stream for the same station: start over.
                val keep = capacity
                runCatching { raf?.close() }
                raf = null
                head = 0L
                titles.clear()
                capacity = keep
            }
            format = streamFormat
            writerId = nextWriterId++
            rateWriterStartMs = now()
            rateMarkMs = 0L
            // A new connection starts with a burst of audio the ring already
            // has (servers send the last seconds at once). Find where the ring
            // ends in the new data and continue from there, so nothing repeats.
            aligner = anchor()?.let { SeamAligner(it, alignLimitBytes()) }
            lastSeamDroppedBytes = -1L
            return writerId
        }
    }

    fun endWriters() {
        synchronized(lock) { writerId = nextWriterId++ }
    }

    fun write(id: Long, data: ByteArray, offset: Int, length: Int) {
        if (length <= 0) return
        synchronized(lock) {
            if (id != writerId || capacity <= 0) return
            val align = aligner
            if (align == null) {
                append(data, offset, length)
            } else {
                align.feed(data, offset, length) { d, o, l -> append(d, o, l) }
                // The repeat arrives in the first seconds. Not found by then:
                // this connection does not overlap; keep everything.
                if (!align.done && now() - rateWriterStartMs > ALIGN_TIMEOUT_MS) {
                    align.giveUp { d, o, l -> append(d, o, l) }
                }
                if (align.done) {
                    lastSeamDroppedBytes = align.dropped
                    aligner = null
                }
            }
        }
    }

    /** The newest bytes in the ring, to be found again in a new connection. */
    private fun anchor(): ByteArray? {
        val file = raf ?: return null
        if (head < ANCHOR_BYTES || capacity < ANCHOR_BYTES) return null
        return runCatching {
            val anchor = ByteArray(ANCHOR_BYTES)
            var read = 0
            while (read < ANCHOR_BYTES) {
                val offset = head - ANCHOR_BYTES + read
                val position = offset % capacity
                val n = min((capacity - position).toInt(), ANCHOR_BYTES - read)
                file.seek(position)
                file.readFully(anchor, read, n)
                read += n
            }
            anchor
        }.getOrNull()
    }

    /** How much new data is searched before giving up: about a minute. */
    private fun alignLimitBytes(): Int =
        (estimatedByteRate * 60).toInt().coerceIn(512 * 1024, 4 * 1024 * 1024)

    private fun append(data: ByteArray, offset: Int, length: Int) {
        if (length <= 0) return
        run {
            val file = raf ?: RandomAccessFile(this.file, "rw").also { raf = it }
            var written = 0
            while (written < length) {
                val position = (head % capacity)
                val n = min((capacity - position).toInt(), length - written)
                file.seek(position)
                file.write(data, offset + written, n)
                written += n
                head += n
            }
            updateRate()
            while (titles.isNotEmpty() && titles.first().first < head - capacity) titles.removeFirst()
            lock.notifyAll()
        }
    }

    fun title(id: Long, raw: ByteArray) {
        synchronized(lock) {
            if (id != writerId) return
            // A new connection repeats the song that is on: keep it once.
            if (titles.lastOrNull()?.second?.contentEquals(raw) == true) return
            titles.addLast(head to raw)
        }
    }

    /**
     * Reads audio from [offset]. Waits up to [waitMs] for new audio at the
     * live edge. Returns the byte count, 0 when nothing came in time, or
     * [TOO_OLD] when [offset] has already been overwritten.
     */
    fun read(offset: Long, target: ByteArray, targetOffset: Int, length: Int, waitMs: Long): Int {
        synchronized(lock) {
            if (offset < max(0L, head - capacity)) return TOO_OLD
            if (offset >= head && waitMs > 0) lock.wait(waitMs)
            if (offset < max(0L, head - capacity)) return TOO_OLD
            val available = head - offset
            if (available <= 0) return 0
            val file = raf ?: return 0
            val position = offset % capacity
            val n = min(min(available, (capacity - position)), length.toLong()).toInt()
            file.seek(position)
            file.readFully(target, targetOffset, n)
            return n
        }
    }

    /** Titles that arrived in [from, to). */
    fun titlesIn(from: Long, to: Long): List<ByteArray> = synchronized(lock) {
        titles.filter { it.first in from until to }.map { it.second }
    }

    /** The song that was on at [offset]. */
    fun titleAt(offset: Long): ByteArray? = synchronized(lock) {
        titles.lastOrNull { it.first <= offset }?.second
    }

    private fun updateRate() {
        val t = now()
        // Servers send a burst of past audio on connect: skip its first seconds.
        if (t - rateWriterStartMs < BURST_MS) return
        if (rateMarkMs == 0L) {
            rateMarkMs = t
            rateMarkBytes = head
            return
        }
        val elapsed = t - rateMarkMs
        if (elapsed >= MIN_RATE_WINDOW_MS) {
            val measured = (head - rateMarkBytes) * 1000.0 / elapsed
            if (measured in 1_000.0..80_000.0) estimatedByteRate = measured
        }
    }

    private fun now(): Long = clock()

    companion object {
        const val TOO_OLD = -2
        /** About a second of audio: long enough not to match by chance. */
        const val ANCHOR_BYTES = 16 * 1024
        private const val ALIGN_TIMEOUT_MS = 8_000L
        /** 128 kbps until measured. */
        const val DEFAULT_BYTE_RATE = 16_000.0
        private const val BURST_MS = 8_000L
        private const val MIN_RATE_WINDOW_MS = 10_000L
    }
}

/**
 * Finds [anchor] (the end of what is already kept) in a new connection's
 * data, streaming (Knuth–Morris–Pratt, so a long run of identical silent
 * frames costs no more than any other audio). Everything up to and including
 * the anchor is a repeat and is dropped; what follows is emitted. If the
 * anchor is not found within [limit] bytes the connection is not a
 * continuation after all, and everything held back is emitted as is.
 */
internal class SeamAligner(private val anchor: ByteArray, private val limit: Int) {
    private val fail = IntArray(anchor.size)
    private var matched = 0
    private val held = java.io.ByteArrayOutputStream()

    var done = false
        private set

    /** Bytes dropped as a repeat; −1 while searching or when nothing matched. */
    var dropped = -1L
        private set

    init {
        var k = 0
        for (i in 1 until anchor.size) {
            while (k > 0 && anchor[i] != anchor[k]) k = fail[k - 1]
            if (anchor[i] == anchor[k]) k++
            fail[i] = k
        }
    }

    fun feed(data: ByteArray, offset: Int, length: Int, emit: (ByteArray, Int, Int) -> Unit) {
        if (done) {
            emit(data, offset, length)
            return
        }
        val end = offset + length
        for (i in offset until end) {
            val b = data[i]
            while (matched > 0 && anchor[matched] != b) matched = fail[matched - 1]
            if (anchor[matched] == b) matched++
            if (matched == anchor.size) {
                done = true
                dropped = held.size().toLong() + (i + 1 - offset)
                held.reset()
                if (i + 1 < end) emit(data, i + 1, end - i - 1)
                return
            }
        }
        held.write(data, offset, length)
        if (held.size() > limit) giveUp(emit)
    }

    /** No repeat found: everything held back is new audio after all. */
    fun giveUp(emit: (ByteArray, Int, Int) -> Unit) {
        if (done) return
        done = true
        val all = held.toByteArray()
        held.reset()
        if (all.isNotEmpty()) emit(all, 0, all.size)
    }
}
