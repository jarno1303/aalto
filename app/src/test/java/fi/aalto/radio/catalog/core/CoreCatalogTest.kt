package fi.aalto.radio.catalog.core

import fi.aalto.radio.catalog.CatalogSource
import fi.aalto.radio.catalog.CatalogStation
import fi.aalto.radio.catalog.quality.CatalogQualityEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CoreCatalogTest {

    private fun tiedosto(
        versio: Int = 1,
        maa: String = "FI",
        asemat: String = ASEMA_YLEX
    ) = """{"versio":$versio,"paivitetty":"2026-09-20","maa":"$maa","asemat":[$asemat]}"""

    @Test
    fun `lukee aseman kentat`() {
        val katalogi = CoreCatalogParser.parse(tiedosto())
        assertNotNull(katalogi)
        val asema = katalogi!!.stations.single()
        assertEquals("8f7ecd3e", asema.id)
        assertEquals("fi-yle-ylex", asema.ownId)
        assertEquals("YleX", asema.name)
        assertEquals("Yle", asema.broadcaster)
        assertEquals(2, asema.streamUrls.size)
        assertEquals(listOf("8f7ecd3e", "3381aa18"), asema.radioBrowserIds)
    }

    @Test
    fun `hylkaa liian uuden muodon jotta vanha asennus ei riko`() {
        assertNull(CoreCatalogParser.parse(tiedosto(versio = CoreCatalogParser.SUPPORTED_VERSION + 1)))
    }

    @Test
    fun `hylkaa rikkinaisen tiedoston`() {
        assertNull(CoreCatalogParser.parse("ei tata voi jasentaa"))
        assertNull(CoreCatalogParser.parse(tiedosto(maa = "Suomi")))
        assertNull(CoreCatalogParser.parse("""{"versio":1,"maa":"FI"}"""))
    }

    @Test
    fun `ohittaa yksittaisen rikkinaisen aseman mutta pitaa muut`() {
        val rikki = """{"nimi":"Ei tunnistetta","osoitteet":["https://a.fi/x"]}"""
        val osoitteeton = """{"id":"x","nimi":"Ei osoitetta","osoitteet":[]}"""
        val katalogi = CoreCatalogParser.parse(tiedosto(asemat = "$rikki,$ASEMA_YLEX,$osoitteeton"))
        assertEquals(listOf("YleX"), katalogi!!.stations.map { it.name })
    }

    @Test
    fun `jarjestaa asemat jarjestysnumeron mukaan`() {
        val toinen = """{"id":"b","nimi":"Toinen","osoitteet":["https://b.fi/x"],"jarjestys":50}"""
        val katalogi = CoreCatalogParser.parse(tiedosto(asemat = "$ASEMA_YLEX,$toinen"))
        assertEquals(listOf("Toinen", "YleX"), katalogi!!.stations.map { it.name })
    }

    @Test
    fun `kuratoitu tunniste sailyy vaikka kaksoiskappale ilmestyy`() {
        val ydin = CoreCatalogParser.parse(tiedosto())!!.toCatalogStations()
        val moottori = CatalogQualityEngine()

        val yksin = moottori.curate(ydin).stations.single()
        assertEquals("8f7ecd3e", yksin.station.stableId)

        // Radio Browseriin ilmestyy sama asema toisella nimella ja omalla
        // uuid:lla. Ennen korjausta ryhman tiiviste - ja siis suosikin avain -
        // olisi muuttunut tassa.
        val kaksoiskappale = radioBrowserAsema(
            id = "3381aa18",
            nimi = "YleX 128k",
            osoite = "http://icecast.live.yle.fi/radio/YleX/icecast.audio"
        )
        val yhdessa = moottori.curate(ydin + kaksoiskappale).stations.single()
        assertEquals("8f7ecd3e", yhdessa.station.stableId)
        assertEquals("YleX", yhdessa.displayName)
    }

    @Test
    fun `kuratoitu osoite voittaa kun palvelin on sama`() {
        val ydin = CoreCatalogParser.parse(tiedosto())!!.toCatalogStations()
        // Sama palvelin, eri polku: laatumoottori yhdistaa nama
        // stream-host-avaimella.
        val vanhentunut = radioBrowserAsema(
            id = "3381aa18",
            nimi = "YleX",
            osoite = "http://icecast.live.yle.fi/radio/YleXVanha/icecast.audio"
        )
        val tulos = CatalogQualityEngine().curate(ydin + vanhentunut).stations.single()
        assertEquals(
            "https://icecast.live.yle.fi/radio/YleX/icecast.audio",
            tulos.station.preferredStreamUrl
        )
        assertTrue(
            tulos.station.streamAlternatives
                .contains("http://icecast.live.yle.fi/radio/YleXVanha/icecast.audio")
        )
    }

    @Ignore(
        "Odottaa radioBrowserIds-pohjaista yhdistamista. Laatumoottori yhdistaa " +
            "asemat vain jaetun palvelimen, kotisivun tai lahettajan kautta, joten " +
            "kuratoitu asema ja sen Radio Browser -vastine jaavat eri ryhmiin kun " +
            "osoite on eri palvelimella - Radio Nova on juuri tallainen. Katso " +
            "CURRENT_STATE."
    )
    @Test
    fun `kuratoitu asema yhdistyy Radio Browseriin eri palvelimella`() {
        val ydin = CoreCatalogParser.parse(tiedosto())!!.toCatalogStations()
        val toisaalla = radioBrowserAsema(
            id = "3381aa18",
            nimi = "YleX",
            osoite = "https://toinen.example.com/ylex"
        )
        // 3381aa18 on ytimen radioBrowserIds-listassa, joten naiden pitaisi
        // olla sama asema - ei kahta riviä listassa.
        val tulos = CatalogQualityEngine().curate(ydin + toisaalla).stations
        assertEquals(1, tulos.size)
        assertEquals("8f7ecd3e", tulos.single().station.stableId)
    }

    private fun radioBrowserAsema(id: String, nimi: String, osoite: String) = listOf(
        CatalogStation(
            source = CatalogSource.RADIO_BROWSER,
            sourceStationId = id,
            canonicalName = nimi,
            streamUrl = osoite,
            resolvedStreamUrl = osoite,
            homepageUrl = null,
            logoUrl = null,
            countryCode = "FI",
            countryName = "Finland",
            region = null,
            languages = emptyList(),
            rawTags = emptyList(),
            codec = "AAC",
            bitrateKbps = 128,
            votes = 1,
            clickCount = 1,
            clickTrend = 0,
            lastCheckOk = true,
            lastCheckAt = null,
            latitude = null,
            longitude = null
        )
    )

    private companion object {
        const val ASEMA_YLEX = """{
            "id":"8f7ecd3e",
            "omaId":"fi-yle-ylex",
            "nimi":"YleX",
            "lahettaja":"Yle",
            "osoitteet":[
                "https://icecast.live.yle.fi/radio/YleX/icecast.audio",
                "http://icecast.live.yle.fi/radio/YleX/icecast.audio"
            ],
            "radioBrowserIds":["8f7ecd3e","3381aa18"],
            "jarjestys":100
        }"""
    }
}
