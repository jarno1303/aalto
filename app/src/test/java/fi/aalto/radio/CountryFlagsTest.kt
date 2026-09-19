package fi.aalto.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CountryFlagsTest {
    @Test
    fun countryCodesBecomeFlags() {
        assertEquals("🇫🇮", countryFlag("FI"))
        assertEquals("🇩🇪", countryFlag(" de "))
    }

    @Test
    fun anythingElseHasNoFlag() {
        assertNull(countryFlag(""))
        assertNull(countryFlag("FIN"))
        assertNull(countryFlag("1A"))
    }
}
