package fi.aalto.radio.history

/**
 * Turns whatever a station puts in its stream metadata into a line worth
 * showing and storing — or nothing at all.
 *
 * Stations are wildly inconsistent here: some send "Artist - Title", some the
 * station name over and over, some an advert marker, some a URL. Everything in
 * this file is pure, so the rules can be tested without a player.
 */
internal object TrackTitle {

    /**
     * The line shown while playing: "Artist – Title", or just the title when
     * the artist adds nothing. Null when the metadata says nothing useful.
     */
    fun format(
        title: String?,
        artist: String?,
        stationTitle: String?,
        stationDetails: String?
    ): String? {
        val cleanTitle = title?.trim()
            ?.takeIf { it.length >= 2 && !it.equals(stationTitle, ignoreCase = true) }
            ?.takeUnless { it.startsWith("http", ignoreCase = true) }
            ?: return null
        val cleanArtist = artist?.trim()
            ?.takeIf {
                it.isNotEmpty() && it != "Aalto" &&
                    !it.equals(stationTitle, ignoreCase = true) &&
                    !it.equals(stationDetails, ignoreCase = true)
            }
        return if (cleanArtist != null && !cleanTitle.contains(cleanArtist, ignoreCase = true)) {
            "$cleanArtist – $cleanTitle"
        } else {
            cleanTitle
        }
    }

    /**
     * The same line, but strict enough to keep in a history the user reads
     * later: no adverts, no station idents, no URLs, no single letters.
     * Returns null for anything that should not be stored.
     */
    fun forHistory(line: String?, stationName: String?): String? {
        val collapsed = line?.replace(WHITESPACE, " ")?.trim() ?: return null
        val withoutPrefix = PREFIXES.fold(collapsed) { text, prefix ->
            if (text.startsWith(prefix, ignoreCase = true)) {
                text.removeRange(0, prefix.length).trim().trimStart('-', '–', ':').trim()
            } else {
                text
            }
        }
        val trimmed = withoutPrefix.trim('"', '\'', '-', '–', ' ')
        if (trimmed.length < MIN_LENGTH || trimmed.length > MAX_LENGTH) return null
        if (trimmed.startsWith("http", ignoreCase = true)) return null
        if (stationName != null && trimmed.equals(stationName.trim(), ignoreCase = true)) return null
        val lowercase = trimmed.lowercase()
        if (JUNK.any { lowercase.contains(it) }) return null
        // "-", "***", "..." and other separators stations send between songs.
        if (trimmed.none { it.isLetterOrDigit() }) return null
        return trimmed
    }

    /** True when this is the same song that was just stored for this station. */
    fun isRepeat(previousStationId: String?, previousTitle: String?, stationId: String, title: String): Boolean =
        previousStationId == stationId && previousTitle.equals(title, ignoreCase = true)

    private val WHITESPACE = Regex("\\s+")
    private const val MIN_LENGTH = 3
    private const val MAX_LENGTH = 120

    private val PREFIXES = listOf(
        "now playing", "nyt soi", "soi nyt", "live:", "on air", "current song", "np:"
    )

    private val JUNK = listOf(
        "advert", "adverti", "commercial", "mainos", "reklam", "jingle",
        "station id", "unknown artist", "unknown title", "no title",
        "www.", ".com/", ".fi/"
    )
}
