package fi.aalto.radio

import fi.aalto.radio.playback.PlaylistResolver
import fi.aalto.radio.playback.StreamCandidates
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamFallbackTest {

    private val station = RadioStation(
        id = "test",
        name = "Test",
        description = "",
        initials = "T",
        logoColorArgb = 0xFF000000,
        streamUrl = "https://a.example/stream",
        preferredStreamUrl = "https://b.example/stream",
        countryCode = "FI",
        tags = emptyList(),
        category = "",
        lastKnownWorkingStreamUrl = "https://a.example/stream",
        streamAlternatives = listOf("https://c.example/stream", "ftp://bad", " https://b.example/stream ")
    )

    @Test
    fun rememberedAddressComesFirstWithoutDuplicates() {
        assertEquals(
            listOf(
                "https://c.example/stream",
                "https://b.example/stream",
                "https://a.example/stream"
            ),
            StreamCandidates.forStation(station, remembered = "https://c.example/stream")
        )
    }

    @Test
    fun withoutMemoryPreferredComesFirst() {
        assertEquals(
            "https://b.example/stream",
            StreamCandidates.forStation(station, remembered = null).first()
        )
    }

    @Test
    fun parsesPlsPlaylist() {
        val pls = """
            [playlist]
            NumberOfEntries=2
            File1=http://one.example:8000/live
            Title1=One
            File2=https://two.example/live
        """.trimIndent()
        assertEquals(
            listOf("http://one.example:8000/live", "https://two.example/live"),
            PlaylistResolver.parse(pls)
        )
    }

    @Test
    fun parsesM3uPlaylist() {
        val m3u = "#EXTM3U\n#EXTINF:-1,Radio\nhttps://stream.example/radio.mp3\n"
        assertEquals(listOf("https://stream.example/radio.mp3"), PlaylistResolver.parse(m3u))
    }
}
