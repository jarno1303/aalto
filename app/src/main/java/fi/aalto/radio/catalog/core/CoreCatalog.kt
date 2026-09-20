package fi.aalto.radio.catalog.core

import org.json.JSONObject

/**
 * Aallon kuratoitu ydin: kasin tarkistetut asemat, joiden osoitteet tulevat
 * lahettajan omalta palvelimelta ja joiden tunniste ei liiku.
 *
 * Radio Browser jaa pitkaksi hannaksi. Sielta tuleva asema saa tunnisteensa
 * laatumoottorin ryhmatiivisteesta, joka muuttuu aina kun joku muokkaa
 * merkintaa. Ytimen asemilla tunniste tulee tiedostosta, joten suosikki
 * pysyy suosikkina.
 *
 * Tiedoston kentat ovat suomeksi, koska sen tuottaa scripts/kokoa_katalogi.py
 * ja sita luetaan kasin. Muoto on versioitu: jos tiedoston versio on uudempi
 * kuin [CoreCatalogParser.SUPPORTED_VERSION], tiedosto ohitetaan kokonaan.
 * Nain uusi muoto ei riko vanhaa asennusta.
 */
data class CoreCatalogStation(
    /** Pysyva tunniste. Tama paatyy suosikin avaimeksi. */
    val id: String,
    /** Aallon oma tunniste, myos silloin kun [id] on peritty. */
    val ownId: String,
    val name: String,
    val broadcaster: String?,
    /** Paras osoite ensin; loput ovat varalla. */
    val streamUrls: List<String>,
    /** Radio Browserin uuid:t, joiden tiedetaan tarkoittavan tata asemaa. */
    val radioBrowserIds: List<String>,
    val order: Int
)

data class CoreCatalog(
    val version: Int,
    val updated: String,
    val countryCode: String,
    val stations: List<CoreCatalogStation>
)

object CoreCatalogParser {

    /** Suurin muotoversio, jonka tama sovellusversio ymmartaa. */
    const val SUPPORTED_VERSION = 1

    /**
     * Palauttaa null jos tiedosto on rikki tai liian uusi. Kutsujan kuuluu
     * silloin jatkaa ilman kuratoitua ydinta: radio ei saa jaada soimatta
     * katalogin takia.
     */
    fun parse(json: String): CoreCatalog? {
        val juuri = runCatching { JSONObject(json) }.getOrNull() ?: return null

        val versio = juuri.optInt("versio", -1)
        if (versio < 1 || versio > SUPPORTED_VERSION) return null

        val maa = juuri.optString("maa").trim().uppercase()
        if (!maa.matches(Regex("[A-Z]{2}"))) return null

        val taulukko = juuri.optJSONArray("asemat") ?: return null
        val asemat = buildList(taulukko.length()) {
            for (i in 0 until taulukko.length()) {
                val rivi = taulukko.optJSONObject(i) ?: continue
                lueAsema(rivi)?.let(::add)
            }
        }
        if (asemat.isEmpty()) return null

        return CoreCatalog(
            version = versio,
            updated = juuri.optString("paivitetty").trim(),
            countryCode = maa,
            stations = asemat.sortedBy { it.order }
        )
    }

    /** Yksittainen rikkinainen rivi ohitetaan, koko tiedostoa ei hylata. */
    private fun lueAsema(rivi: JSONObject): CoreCatalogStation? {
        val id = rivi.optString("id").trim().takeIf { it.isNotBlank() } ?: return null
        val nimi = rivi.optString("nimi").trim().takeIf { it.length >= 2 } ?: return null

        val osoitteet = lueLista(rivi, "osoitteet")
            .filter { it.startsWith("http://", true) || it.startsWith("https://", true) }
        if (osoitteet.isEmpty()) return null

        return CoreCatalogStation(
            id = id,
            ownId = rivi.optString("omaId").trim().takeIf { it.isNotBlank() } ?: id,
            name = nimi,
            broadcaster = rivi.optString("lahettaja").trim().takeIf { it.isNotBlank() },
            streamUrls = osoitteet,
            radioBrowserIds = lueLista(rivi, "radioBrowserIds"),
            order = rivi.optInt("jarjestys", Int.MAX_VALUE)
        )
    }

    private fun lueLista(rivi: JSONObject, avain: String): List<String> {
        val taulukko = rivi.optJSONArray(avain) ?: return emptyList()
        return buildList(taulukko.length()) {
            for (i in 0 until taulukko.length()) {
                taulukko.optString(i).trim().takeIf { it.isNotBlank() }?.let(::add)
            }
        }.distinct()
    }
}
