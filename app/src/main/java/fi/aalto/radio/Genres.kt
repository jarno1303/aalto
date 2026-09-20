package fi.aalto.radio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Genres as families: a main genre with its subgenres under it (Rock: metal,
 * punk, classic rock…), so a long flat list of chips is never needed. The
 * main genres are the same ones the search chips have always shown.
 *
 * Tags are matched as words ([stationTags] splits "classic rock" into
 * "classic" and "rock"; letters outside a–z end a word, so "iskelmä" is
 * matched as "iskelm").
 */
internal data class GenreGroup(
    val main: DiscoveryCategory,
    val subgenres: List<DiscoveryCategory>,
    val tint: Color
)

private fun anyWord(label: String, vararg words: String): DiscoveryCategory {
    val set = words.toSet()
    return DiscoveryCategory(label) { station -> stationTags(station).any { it in set } }
}

/** Every word of one of the phrases, e.g. "classic" and "rock". */
private fun anyPhrase(label: String, vararg phrases: String): DiscoveryCategory {
    val wordSets = phrases.map { phrase -> phrase.split(' ').toSet() }
    return DiscoveryCategory(label) { station ->
        val tags = stationTags(station)
        wordSets.any { words -> tags.containsAll(words) }
    }
}

private fun mainGenre(label: String): DiscoveryCategory = DiscoveryCategories.first { it.label == label }

internal val DecadesGenre = anyWord(
    "Vuosikymmenet",
    "50s", "60s", "70s", "80s", "90s", "00s", "2000s", "oldies", "retro"
)

internal val GenreGroups: List<GenreGroup> = listOf(
    GenreGroup(
        mainGenre("Pop"),
        listOf(
            anyWord("Hitit", "top40", "charts", "hits", "hit", "chr"),
            anyWord("Iskelmä & schlager", "iskelm", "schlager", "schlagers"),
            anyWord("Indie", "indie")
        ),
        Color(0xFF1769FF)
    ),
    GenreGroup(
        mainGenre("Rock"),
        listOf(
            anyPhrase("Klassinen rock", "classic rock", "classicrock"),
            anyWord("Metalli", "metal", "heavymetal", "hardrock"),
            anyWord("Alternatiivinen", "alternative", "grunge"),
            anyWord("Punk", "punk")
        ),
        Color(0xFFD64545)
    ),
    GenreGroup(
        DecadesGenre,
        listOf(
            anyWord("60-luku", "60s", "sixties"),
            anyWord("70-luku", "70s", "seventies"),
            anyWord("80-luku", "80s", "eighties"),
            anyWord("90-luku", "90s", "nineties"),
            anyWord("2000-luku", "00s", "2000s"),
            anyWord("Oldies", "oldies", "retro")
        ),
        Color(0xFFB7791F)
    ),
    GenreGroup(
        mainGenre("Dance & elektroninen"),
        listOf(
            anyWord("House", "house"),
            anyWord("Techno", "techno"),
            anyWord("Trance", "trance"),
            anyPhrase("Drum & bass", "drum bass", "dnb", "drumandbass"),
            anyWord("Chillout & lounge", "chillout", "chill", "lounge", "ambient")
        ),
        Color(0xFF7E57C2)
    ),
    GenreGroup(
        mainGenre("Hip-hop & R&B"),
        listOf(
            anyPhrase("Hip-hop", "hip hop", "hiphop", "rap"),
            anyWord("R&B", "rnb", "r&b"),
            anyWord("Reggae", "reggae", "dancehall")
        ),
        Color(0xFFE67E22)
    ),
    GenreGroup(
        mainGenre("Uutiset & puhe"),
        listOf(
            anyWord("Uutiset", "news", "uutiset"),
            anyWord("Puhe", "talk", "spoken", "puhe"),
            anyWord("Urheilu", "sport", "sports", "urheilu"),
            anyWord("Hengellinen", "christian", "religious", "gospel", "worship")
        ),
        Color(0xFF4A6378)
    ),
    GenreGroup(
        mainGenre("Klassinen"),
        listOf(
            anyWord("Ooppera", "opera", "ooppera"),
            anyWord("Elokuvamusiikki", "soundtrack", "soundtracks", "film", "movie")
        ),
        Color(0xFF8D6E63)
    ),
    GenreGroup(
        mainGenre("Jazz & soul"),
        listOf(
            anyWord("Jazz", "jazz"),
            anyWord("Blues", "blues"),
            anyWord("Soul & funk", "soul", "funk", "motown")
        ),
        Color(0xFF00897B)
    ),
    GenreGroup(
        mainGenre("Country & folk"),
        listOf(
            anyWord("Country", "country"),
            anyWord("Folk", "folk", "kansanmusiikki"),
            anyWord("Latin", "latin", "salsa", "reggaeton"),
            anyWord("Maailmanmusiikki", "world", "worldmusic")
        ),
        Color(0xFF689F38)
    ),
    GenreGroup(mainGenre("Lapset & perhe"), emptyList(), Color(0xFFEC6FA0)),
    GenreGroup(mainGenre("Muut"), emptyList(), Color(0xFF68727D))
)

/** Every genre that can be chosen, main and sub, by its label. */
internal val AllGenres: List<DiscoveryCategory> =
    (GenreGroups.flatMap { listOf(it.main) + it.subgenres } + DiscoveryCategories).distinctBy { it.label }

/** The genre families: one card per family, subgenres as chips inside. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun GenreSheet(
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onClear: () -> Unit,
    matchCount: Int,
    onDismiss: () -> Unit
) {
    val surface = MaterialTheme.colorScheme.surface
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        MatchSheetNavigationBar()
        Column(modifier = Modifier.navigationBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = AaltoSpaceXl, end = AaltoSpaceM),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.genres_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (selected.isNotEmpty()) {
                    TextButton(onClick = onClear) { Text(stringResource(R.string.genres_clear)) }
                }
            }
            LazyColumn(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(horizontal = AaltoSpaceL),
                verticalArrangement = Arrangement.spacedBy(AaltoSpaceS)
            ) {
                items(items = GenreGroups, key = { it.main.label }) { group ->
                    val mainOn = group.main.label in selected
                    Surface(
                        color = group.tint.copy(alpha = 0.12f).compositeOver(surface),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(bottom = if (group.subgenres.isEmpty()) 0.dp else AaltoSpaceM)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .clickable(role = Role.Checkbox) { onToggle(group.main.label) }
                                    .heightIn(min = 52.dp)
                                    .padding(horizontal = AaltoSpaceL),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = group.main.label,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                if (mainOn) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = group.tint,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            if (group.subgenres.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(AaltoSpaceS),
                                    modifier = Modifier.padding(horizontal = AaltoSpaceL)
                                ) {
                                    group.subgenres.forEach { genre ->
                                        val on = genre.label in selected
                                        FilterChip(
                                            selected = on,
                                            onClick = { onToggle(genre.label) },
                                            label = { Text(genre.label, maxLines = 1) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                containerColor = surface,
                                                selectedContainerColor = group.tint,
                                                selectedLabelColor = Color.White
                                            ),
                                            border = FilterChipDefaults.filterChipBorder(
                                                enabled = true,
                                                selected = on,
                                                borderColor = Color.Transparent,
                                                selectedBorderColor = Color.Transparent
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(AaltoSpaceS)) }
            }
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AaltoSpaceL, vertical = AaltoSpaceS)
                    .heightIn(min = 52.dp)
            ) {
                val resources = LocalContext.current.resources
                Text(
                    text = if (selected.isEmpty()) {
                        stringResource(R.string.genres_show_all)
                    } else {
                        resources.getQuantityString(R.plurals.genres_show_matches, matchCount, matchCount)
                    }
                )
            }
        }
    }
}
