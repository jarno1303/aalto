package fi.aalto.radio.catalog

import fi.aalto.radio.catalog.radiobrowser.DefaultRadioBrowserBaseUrlProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioBrowserServersTest {

    @Test
    fun lookedUpServersComeFirstThenKnownOnesWithoutDuplicates() {
        val order = DefaultRadioBrowserBaseUrlProvider.serverOrder(listOf("de1.api.radio-browser.info."))
        assertEquals("https://de1.api.radio-browser.info", order.first())
        assertEquals(order.distinct(), order)
        assertEquals(DefaultRadioBrowserBaseUrlProvider.KNOWN_SERVERS.size, order.size)
    }

    @Test
    fun unrelatedNamesAndTheRoundRobinNameAreDropped() {
        val order = DefaultRadioBrowserBaseUrlProvider.serverOrder(
            listOf("static.example.net", "all.api.radio-browser.info")
        )
        assertFalse(order.any { "example" in it || "all.api" in it })
    }

    @Test
    fun failedLookupStillGivesEveryKnownServer() = runTest {
        val provider = DefaultRadioBrowserBaseUrlProvider(lookup = { error("no DNS") })
        val urls = provider.baseUrls()
        assertEquals(DefaultRadioBrowserBaseUrlProvider.KNOWN_SERVERS.size, urls.size)
        assertTrue(urls.all { it.startsWith("https://") && it.endsWith(".api.radio-browser.info") })
    }
}
