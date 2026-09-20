package fi.aalto.radio.playback

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimeshiftBufferTest {

    private fun buffer(capacity: Long): TimeshiftBuffer {
        val file = File.createTempFile("timeshift", ".buf").apply { deleteOnExit() }
        return TimeshiftBuffer(file, clock = { 0L }).also { it.reset(capacity) }
    }

    private fun bytes(from: Int, count: Int) = ByteArray(count) { (from + it).toByte() }

    @Test
    fun readsBackWhatWasWrittenAcrossTheRingEdge() {
        val ring = buffer(capacity = 10)
        val writer = ring.beginWriter("mp3")
        ring.write(writer, bytes(0, 8), 0, 8)
        ring.write(writer, bytes(8, 6), 0, 6) // wraps: 14 written, 10 kept
        assertEquals(14L, ring.head)
        assertEquals(4L, ring.tail)

        val out = ByteArray(10)
        var read = 0
        while (read < 10) {
            val n = ring.read(4L + read, out, read, 10 - read, waitMs = 0)
            read += n
        }
        assertArrayEquals(bytes(4, 10), out)
    }

    @Test
    fun overwrittenAudioIsReportedTooOld() {
        val ring = buffer(capacity = 4)
        val writer = ring.beginWriter("aac")
        ring.write(writer, bytes(0, 10), 0, 10)
        assertEquals(TimeshiftBuffer.TOO_OLD, ring.read(2L, ByteArray(4), 0, 4, waitMs = 0))
    }

    @Test
    fun aNewConnectionSilencesTheOldOne() {
        val ring = buffer(capacity = 100)
        val old = ring.beginWriter("mp3")
        ring.write(old, bytes(0, 5), 0, 5)
        val new = ring.beginWriter("mp3")
        ring.write(old, bytes(0, 5), 0, 5) // late bytes from the closed connection
        ring.write(new, bytes(0, 3), 0, 3)
        assertEquals(8L, ring.head)
    }

    @Test
    fun titlesKeepTheirPlace() {
        val ring = buffer(capacity = 100)
        val writer = ring.beginWriter("mp3")
        ring.write(writer, bytes(0, 10), 0, 10)
        ring.title(writer, "StreamTitle='A';".toByteArray())
        ring.write(writer, bytes(0, 10), 0, 10)
        ring.title(writer, "StreamTitle='B';".toByteArray())

        assertNull(ring.titleAt(5))
        assertEquals("StreamTitle='A';", String(ring.titleAt(15)!!))
        assertEquals(1, ring.titlesIn(10, 20).size)
    }

    @Test
    fun onlyCuttableFormatsAreKept() {
        assertEquals("mp3", CuttableStreams.formatFor("audio/mpeg"))
        assertEquals("aac", CuttableStreams.formatFor("audio/aacp; charset=x"))
        assertNull(CuttableStreams.formatFor("application/ogg"))
        assertNull(CuttableStreams.formatFor("application/vnd.apple.mpegurl"))
        assertNull(CuttableStreams.formatFor(null))
    }

    private fun random(seed: Long, count: Int) = ByteArray(count).also { java.util.Random(seed).nextBytes(it) }

    private fun readAll(ring: TimeshiftBuffer, from: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(4096)
        var position = from
        while (position < ring.head) {
            val n = ring.read(position, chunk, 0, chunk.size, waitMs = 0)
            out.write(chunk, 0, n)
            position += n
        }
        return out.toByteArray()
    }

    @Test
    fun aReconnectBurstIsNotKeptTwice() {
        val stream = random(1, 100_000)
        val ring = buffer(capacity = 1_000_000)
        val live = ring.beginWriter("mp3")
        ring.write(live, stream, 0, 60_000)
        // The new connection starts 20 000 bytes back (the server's burst).
        val recorder = ring.beginWriter("mp3")
        var i = 40_000
        while (i < stream.size) {
            val n = minOf(3_000, stream.size - i)
            ring.write(recorder, stream, i, n)
            i += n
        }
        assertEquals(100_000L, ring.head)
        assertArrayEquals(stream, readAll(ring, 0))
        assertEquals(20_000L, ring.lastSeamDroppedBytes)
    }

    @Test
    fun withoutAnOverlapEverythingIsKeptAfterAWhile() {
        var now = 0L
        val file = File.createTempFile("timeshift", ".buf").apply { deleteOnExit() }
        val ring = TimeshiftBuffer(file, clock = { now }).also { it.reset(1_000_000) }
        val live = ring.beginWriter("aac")
        ring.write(live, random(2, 50_000), 0, 50_000)
        val recorder = ring.beginWriter("aac")
        ring.write(recorder, random(3, 20_000), 0, 20_000)
        assertEquals(50_000L, ring.head) // still looking for the overlap
        now = 10_000L
        ring.write(recorder, random(4, 1_000), 0, 1_000)
        assertEquals(71_000L, ring.head)
    }
}
