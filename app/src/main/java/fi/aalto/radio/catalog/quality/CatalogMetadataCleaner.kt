package fi.aalto.radio.catalog.quality

data class CleanedStationMetadata(
    val displayName: String,
    val dedupeName: String,
    val suspiciousName: Boolean,
    val technicalSuffixRemoved: Boolean
)

object CatalogMetadataCleaner {
    private val technicalSuffix = Regex(
        "(?i)\\s+(?:\\d{2,4}\\s*k(?:bps)?|aac(?:\\+)?|mp3|opus|flac)\\s*$"
    )
    private val streamSuffix = Regex("(?i)\\s*[-|]\\s*(?:online|live)\\s+stream\\s*$")
    private val urlLike = Regex("(?i)^(?:https?://|www\\.|[a-z0-9.-]+\\.(?:com|net|org)(?:/|$))")

    fun clean(value: String): CleanedStationMetadata {
        val collapsed = value.trim().replace(Regex("\\s+"), " ")
        val withoutStreamSuffix = collapsed.replace(streamSuffix, "").trim()
        val withoutTechnicalSuffix = withoutStreamSuffix.replace(technicalSuffix, "").trim()
        val displayName = withoutTechnicalSuffix.ifBlank { collapsed }
        val dedupeName = displayName.lowercase()
            .replace(Regex("[^\\p{L}\\p{Nd}]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
        val suspicious = displayName.length < 2 ||
            displayName.matches(Regex("(?i)^(test|stream|radio stream|unknown|station)$")) ||
            urlLike.containsMatchIn(displayName) ||
            displayName.count { it.isLetterOrDigit() } < displayName.length / 2
        return CleanedStationMetadata(
            displayName = displayName,
            dedupeName = dedupeName,
            suspiciousName = suspicious,
            technicalSuffixRemoved = displayName != collapsed
        )
    }

    fun compactDedupeName(value: String): String {
        return clean(value).dedupeName.replace(Regex("[^\\p{L}\\p{Nd}]"), "")
    }
}
