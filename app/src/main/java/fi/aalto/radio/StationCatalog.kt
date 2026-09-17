package fi.aalto.radio

data class RadioStation(
    val id: String,
    val radioBrowserStationUuid: String? = null,
    val name: String,
    val description: String,
    val initials: String,
    val logoColorArgb: Long,
    val streamUrl: String,
    val preferredStreamUrl: String = streamUrl,
    val faviconUrl: String? = null,
    val countryCode: String,
    val tags: List<String>,
    val category: String,
    val lastKnownWorkingStreamUrl: String? = streamUrl,
    val streamHealthStatus: String? = null,
    val languages: List<String> = emptyList(),
    val location: String? = null,
    val logoCandidates: List<String> = emptyList(),
    val streamAlternatives: List<String> = emptyList()
) {
    val logoUrl: String?
        get() = faviconUrl

    val stableId: String
        get() = id

    val logoUrls: List<String>
        get() = (listOfNotNull(logoUrl) + logoCandidates).distinct()
}

object StationCatalog {
    const val DEFAULT_STATION_ID = "yle-klassinen"

    val allStations = listOf(
        RadioStation(
            id = "yle-klassinen",
            name = "Yle Klassinen",
            description = "Klassista musiikkia - suora lahetys",
            initials = "YLE",
            logoColorArgb = 0xFFE5B84E,
            streamUrl = "https://icecast.live.yle.fi/radio/YleKlassinen/icecast.audio",
            countryCode = "FI",
            tags = listOf("klassinen", "kulttuuri"),
            category = "Klassinen & Kulttuuri"
        ),
        RadioStation(
            id = "ylex",
            name = "YleX",
            description = "Uutta musiikkia ja nuorisokulttuuria",
            initials = "YLEX",
            logoColorArgb = 0xFF2769A5,
            streamUrl = "https://icecast.live.yle.fi/radio/YleX/icecast.audio",
            countryCode = "FI",
            tags = listOf("pop", "nuoret"),
            category = "Pop & Nuoret"
        ),
        RadioStation(
            id = "yle-radio-suomi",
            name = "Yle Radio Suomi",
            description = "Uutiset, alueelliset ohjelmat ja musiikki",
            initials = "YLE",
            logoColorArgb = 0xFF168463,
            streamUrl = "https://icecast.live.yle.fi/radio/YleRS/icecast.audio",
            countryCode = "FI",
            tags = listOf("puhe", "uutiset", "alueellinen"),
            category = "Puhe & Viihde"
        ),
        RadioStation(
            id = "yle-radio-1",
            name = "Yle Radio 1",
            description = "Kulttuuria, puhetta, tiedetta ja klassista",
            initials = "YLE 1",
            logoColorArgb = 0xFF163653,
            streamUrl = "https://icecast.live.yle.fi/radio/YleRadio1/icecast.audio",
            countryCode = "FI",
            tags = listOf("klassinen", "kulttuuri", "puhe"),
            category = "Klassinen & Kulttuuri"
        ),
        RadioStation(
            id = "yle-puhe",
            name = "Yle Puhe",
            description = "Ajankohtaisohjelmia, draamaa ja urheilua",
            initials = "PUHE",
            logoColorArgb = 0xFFD35400,
            streamUrl = "https://icecast.live.yle.fi/radio/YlePuhe/icecast.audio",
            countryCode = "FI",
            tags = listOf("puhe", "urheilu"),
            category = "Puhe & Viihde"
        ),
        RadioStation(
            id = "yle-vega",
            name = "Yle Vega",
            description = "Aktuellt och kultur pa svenska",
            initials = "VEGA",
            logoColorArgb = 0xFF2980B9,
            streamUrl = "https://icecast.live.yle.fi/radio/YleVega/icecast.audio",
            countryCode = "FI",
            tags = listOf("puhe", "kulttuuri", "svenska"),
            category = "Puhe & Viihde"
        ),
        RadioStation(
            id = "radio-helsinki",
            name = "Radio Helsinki",
            description = "Riippumatonta musiikkia ja kaupunkikulttuuria",
            initials = "HEL",
            logoColorArgb = 0xFF111111,
            streamUrl = "https://stream.radiohelsinki.fi/",
            countryCode = "FI",
            tags = listOf("vaihtoehto", "kaupunki", "musiikki"),
            category = "Vaihtoehto"
        ),
        RadioStation(
            id = "radio-rock",
            name = "Radio Rock",
            description = "Rockia ja kotimaisia puheenaiheita",
            initials = "ROCK",
            logoColorArgb = 0xFF383A40,
            streamUrl = "https://aud-stream-radiorock.nm-elemental.nelonenmedia.fi/playlist.m3u8",
            countryCode = "FI",
            tags = listOf("rock"),
            category = "Rock"
        ),
        RadioStation(
            id = "radio-suomipop",
            name = "Radio Suomipop",
            description = "Suomalaista poppia ja viihdetta",
            initials = "POP",
            logoColorArgb = 0xFFE34F8C,
            streamUrl = "https://aud-stream-suomipop.nm-elemental.nelonenmedia.fi/playlist.m3u8",
            countryCode = "FI",
            tags = listOf("pop", "viihde", "suomi"),
            category = "Pop & Viihde"
        )
    )

    fun stationById(stationId: String): RadioStation? {
        return allStations.firstOrNull { it.id == stationId }
    }

    fun search(query: String): List<RadioStation> {
        val terms = query.trim()
            .lowercase()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

        if (terms.isEmpty()) {
            return allStations
        }

        return allStations.filter { station ->
            val searchableText = buildString {
                append(station.name.lowercase())
                append(' ')
                append(station.description.lowercase())
                append(' ')
                append(station.category.lowercase())
                append(' ')
                append(station.countryCode.lowercase())
                append(' ')
                append(station.tags.joinToString(separator = " ").lowercase())
            }

            terms.all { searchableText.contains(it) }
        }
    }
}
