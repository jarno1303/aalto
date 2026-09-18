package fi.aalto.radio.history

import androidx.media3.common.Metadata
import androidx.media3.extractor.metadata.icy.IcyInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamTrackTest {

    @Test
    fun `reads the icy stream title`() {
        val metadata = Metadata(IcyInfo("Abba - Waterloo", null))
        assertEquals("Abba - Waterloo", StreamTrack.from(metadata)?.title)
    }

    @Test
    fun `ignores an empty icy title`() {
        assertNull(StreamTrack.from(Metadata(IcyInfo("", null))))
        assertNull(StreamTrack.from(Metadata(IcyInfo(null, "https://example.fi"))))
    }

    @Test
    fun `an icy title becomes a history line`() {
        val metadata = Metadata(IcyInfo("Now Playing: Abba - Waterloo", null))
        val announcement = StreamTrack.from(metadata)
        val line = TrackTitle.format(
            title = announcement?.title,
            artist = announcement?.artist,
            stationTitle = "Radio Nova",
            stationDetails = "Pop · Helsinki"
        )
        assertEquals("Abba - Waterloo", TrackTitle.forHistory(line, "Radio Nova"))
    }
}
