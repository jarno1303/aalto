package fi.aalto.radio

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class StationLogoResolverTest {
    @Test
    fun logoCacheKeyBindsBothStationUuidAndLogoUrl() {
        val first = stationLogoCacheKey("uuid-a", "https://logo.example/a.png")

        assertNotEquals(first, stationLogoCacheKey("uuid-b", "https://logo.example/a.png"))
        assertNotEquals(first, stationLogoCacheKey("uuid-a", "https://logo.example/b.png"))
        assertEquals(first, stationLogoCacheKey("uuid-a", " https://logo.example/a.png "))
    }
}
