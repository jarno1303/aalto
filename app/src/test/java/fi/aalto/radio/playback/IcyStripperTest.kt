package fi.aalto.radio.playback

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class IcyStripperTest {

    /** audio(4) meta("StreamTitle='Hi';" padded to 32) audio(4) empty-meta audio(2) */
    private fun stream(): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(1, 2, 3, 4))
        val text = "StreamTitle='Hi';".toByteArray()
        val meta = ByteArray(32).also { System.arraycopy(text, 0, it, 0, text.size) }
        out.write(2)
        out.write(meta)
        out.write(byteArrayOf(5, 6, 7, 8))
        out.write(0)
        out.write(byteArrayOf(9, 10))
        return out.toByteArray()
    }

    private fun run(chunk: Int): Pair<ByteArray, List<String>> {
        val stripper = IcyStripper(metaint = 4)
        val audio = ByteArrayOutputStream()
        val titles = mutableListOf<String>()
        val data = stream()
        var i = 0
        while (i < data.size) {
            val n = minOf(chunk, data.size - i)
            stripper.feed(data, i, n, { d, o, l -> audio.write(d, o, l) }) { raw ->
                titles += IcyText.parse(raw).first.orEmpty()
            }
            i += n
        }
        return audio.toByteArray() to titles
    }

    @Test
    fun audioAndTitlesAreSeparatedWhateverTheChunkSize() {
        for (chunk in listOf(1, 3, 7, 1000)) {
            val (audio, titles) = run(chunk)
            assertArrayEquals("chunk $chunk", byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), audio)
            assertEquals("chunk $chunk", listOf("Hi"), titles)
        }
    }

    @Test
    fun withoutMetadataEverythingIsAudio() {
        val out = ByteArrayOutputStream()
        IcyStripper(0).feed(byteArrayOf(1, 2, 3), 0, 3, { d, o, l -> out.write(d, o, l) }) { }
        assertArrayEquals(byteArrayOf(1, 2, 3), out.toByteArray())
    }

    @Test
    fun latin1TitlesAreRead() {
        val raw = "StreamTitle='Kärpäset';".toByteArray(Charsets.ISO_8859_1)
        assertEquals("Kärpäset", IcyText.parse(raw).first)
    }
}
