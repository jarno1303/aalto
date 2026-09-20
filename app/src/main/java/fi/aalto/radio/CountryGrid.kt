package fi.aalto.radio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.Collator
import java.util.Locale

/** Codes with no people and no radio (Antarctica, uninhabited islands). */
private val NoRadioCountries = setOf("AQ", "BV", "HM", "TF", "UM", "GS", "IO", "PN", "CX", "CC", "NF")

/** Every country, in the phone's language and alphabetical order. */
internal fun worldCountryCodes(): List<String> {
    val collator = Collator.getInstance(Locale.getDefault())
    return Locale.getISOCountries()
        .filter { it !in NoRadioCountries && countryFlag(it) != null }
        .sortedWith { a, b -> collator.compare(countryName(a), countryName(b)) }
}

/** Matches the name in the phone's language, in English, or the code itself. */
internal fun countryMatches(code: String, query: String): Boolean {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return true
    return countryName(code).lowercase().contains(q) ||
        Locale("", code).getDisplayCountry(Locale.ENGLISH).lowercase().contains(q) ||
        code.lowercase() == q
}

/**
 * The world's radio as a grid of flags: the user's own countries first, then
 * every country. Choosing one opens its stations; nothing is added anywhere.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CountryGridSheet(
    ownCountries: List<String>,
    selected: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val world = remember { worldCountryCodes() }
    var query by rememberSaveable { mutableStateOf("") }
    val own = ownCountries.filter { countryMatches(it, query) }
    val all = world.filter { countryMatches(it, query) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        MatchSheetNavigationBar()
        Column(
            modifier = Modifier
                .fillMaxHeight(0.92f)
                .navigationBarsPadding()
        ) {
            Text(
                text = stringResource(R.string.countries_world_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = AaltoSpaceXl)
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.countries_search)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = RoundedCornerShape(AaltoSurfaceRadius),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AaltoBlue,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AaltoSpaceL, vertical = AaltoSpaceS)
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 84.dp),
                contentPadding = PaddingValues(start = AaltoSpaceM, end = AaltoSpaceM, bottom = AaltoSpaceL),
                verticalArrangement = Arrangement.spacedBy(AaltoSpaceXs),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (own.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        GridSectionTitle(stringResource(R.string.countries_own))
                    }
                    items(items = own, key = { "own-$it" }) { code ->
                        FlagTile(code = code, selected = code == selected, onClick = { onPick(code) })
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    GridSectionTitle(stringResource(R.string.countries_all))
                }
                items(items = all, key = { it }) { code ->
                    FlagTile(code = code, selected = code == selected, onClick = { onPick(code) })
                }
                if (all.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = stringResource(R.string.search_no_results),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(AaltoSpaceM)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GridSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = AaltoSpaceXs, top = AaltoSpaceM, bottom = AaltoSpaceXs)
    )
}

@Composable
private fun FlagTile(code: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(AaltoSurfaceRadius))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(AaltoSpaceXs)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.25f)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(if (selected) Modifier.border(2.dp, AaltoBlue, shape) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            CountryFlag(code, size = 34.sp)
        }
        Spacer(modifier = Modifier.height(AaltoSpaceXs))
        Text(
            text = countryName(code),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) AaltoBlue else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
