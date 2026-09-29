package fi.aalto.radio

import fi.aalto.radio.playback.PlaylistResolver
import fi.aalto.radio.playback.StreamCandidates
import fi.aalto.radio.playback.StreamHealthMemory
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

    @Test
    fun recentlyFailedAddressMovesBehindHealthyFallbacks() {
        StreamHealthMemory.clearForTests()
        val candidates = listOf(
            "https://one.example/live",
            "https://two.example/live",
            "https://three.example/live"
        )
        StreamHealthMemory.markFailure("station", candidates[0], nowMs = 1_000L)

        assertEquals(
            listOf(candidates[1], candidates[2], candidates[0]),
            StreamHealthMemory.order("station", candidates, nowMs = 2_000L)
        )
    }

    @Test
    fun successfulAddressLeavesCooldownImmediately() {
        StreamHealthMemory.clearForTests()
        val candidates = listOf("https://one.example/live", "https://two.example/live")
        StreamHealthMemory.markFailure("station", candidates[0], nowMs = 1_000L)
        StreamHealthMemory.markSuccess("station", candidates[0])

        assertEquals(candidates, StreamHealthMemory.order("station", candidates, nowMs = 2_000L))
    }

    @Test
    fun expiredFailureDoesNotChangeNormalOrder() {
        StreamHealthMemory.clearForTests()
        val candidates = listOf("https://one.example/live", "https://two.example/live")
        StreamHealthMemory.markFailure("station", candidates[0], nowMs = 1_000L)

        assertEquals(
            candidates,
            StreamHealthMemory.order("station", candidates, nowMs = 11L * 60_000L)
        )
    }

    @Test
    fun allCoolingAddressesKeepDeterministicOrder() {
        StreamHealthMemory.clearForTests()
        val candidates = listOf("https://one.example/live", "https://two.example/live")
        candidates.forEach { StreamHealthMemory.markFailure("station", it, nowMs = 1_000L) }

        assertEquals(candidates, StreamHealthMemory.order("station", candidates, nowMs = 2_000L))
    }
}
