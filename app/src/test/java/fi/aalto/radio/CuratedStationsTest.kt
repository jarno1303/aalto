package fi.aalto.radio

import fi.aalto.radio.playback.CuratedStations
import org.junit.Assert.assertEquals
import org.junit.Test

class CuratedStationsTest {

    private fun station(name: String) = RadioStation(
        id = name,
        name = name,
        description = "",
        initials = name.take(2),
        logoColorArgb = 0xFF000000,
        streamUrl = "https://example.com/$name",
        countryCode = "FI",
        tags = emptyList(),
        category = ""
    )

    @Test
    fun wellKnownStationsComeFirstInListeningOrder() {
        val input = listOf(
            "Pikku Radio",
            "Radio Rock",
            "Yle Radio Suomi",
            "Radio Suomipop"
        ).map(::station)

        assertEquals(
            listOf("Yle Radio Suomi", "Radio Suomipop", "Radio Rock", "Pikku Radio"),
            CuratedStations.sort("FI", input).map { it.name }
        )
    }

    @Test
    fun regionalVariantsFollowTheirMainStation() {
        val input = listOf("Radio Helsinki", "Iskelmä Oikea Asema", "Iskelmä").map(::station)
        assertEquals(
            listOf("Iskelmä", "Radio Helsinki", "Iskelmä Oikea Asema"),
            CuratedStations.sort("FI", input).map { it.name }
        )
    }

    @Test
    fun otherCountriesKeepCatalogOrder() {
        val input = listOf("NPO Radio 2", "NPO Radio 1").map(::station)
        assertEquals(
            listOf("NPO Radio 2", "NPO Radio 1"),
            CuratedStations.sort("NL", input).map { it.name }
        )
    }
}
