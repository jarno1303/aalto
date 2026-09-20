package fi.aalto.radio

import java.util.Locale
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CountryGridTest {
    private lateinit var saved: Locale

    @Before
    fun setUp() {
        saved = Locale.getDefault()
        Locale.setDefault(Locale("fi", "FI"))
    }

    @After
    fun tearDown() {
        Locale.setDefault(saved)
    }

    @Test
    fun worldHasRadioCountriesOnly() {
        val world = worldCountryCodes()
        assertTrue("FI" in world && "PT" in world && "BR" in world)
        assertFalse("AQ" in world)
        assertTrue(world.size > 200)
    }

    @Test
    fun searchInOwnLanguageEnglishOrCode() {
        assertTrue(countryMatches("DE", "saksa"))
        assertTrue(countryMatches("DE", "germ"))
        assertTrue(countryMatches("DE", "de"))
        assertFalse(countryMatches("DE", "ruotsi"))
    }
}
