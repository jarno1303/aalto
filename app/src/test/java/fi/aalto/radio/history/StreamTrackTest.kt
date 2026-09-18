package fi.aalto.radio.history

import androidx.media3.common.Metadata
import androidx.media3.extractor.metadata.icy.IcyInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamTrackTest {

    @Test
    fun `reads the icy stream title`() {
        val metadata = Metadata(icy("Abba - Waterloo"))
        assertEquals("Abba - Waterloo", StreamTrack.from(metadata)?.title)
    }

    @Test
    fun `ignores an empty icy title`() {
        assertNull(StreamTrack.from(Metadata(icy(""))))
    }

    @Test
    fun `ignores metadata with nothing in it`() {
        assertNull(StreamTrack.from(Metadata()))
    }

    @Test
    fun `an icy title becomes a history line`() {
        val metadata = Metadata(icy("Now Playing: Abba - Waterloo"))
        val announcement = StreamTrack.from(metadata)
        val line = TrackTitle.format(
            title = announcement?.title,
            artist = announcement?.artist,
            stationTitle = "Radio Nova",
            stationDetails = "Pop · Helsinki"
        )
        assertEquals("Abba - Waterloo", TrackTitle.forHistory(line, "Radio Nova"))
    }

    /** Media3's IcyInfo carries the raw bytes first; the test only needs the title. */
    private fun icy(title: String) = IcyInfo(ByteArray(0), title, "https://example.fi")
}
