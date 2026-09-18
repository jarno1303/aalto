package fi.aalto.radio

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Surface
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fi.aalto.radio.catalog.CatalogStation

private const val ALL_CATEGORIES = "Kaikki"
private const val SKELETON_ROWS = 4

@Composable
internal fun SearchScreen(
    paddingValues: PaddingValues,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    stations: List<RadioStation>,
    recentStations: List<RadioStation>,
    radioCountryCode: String,
    onRadioCountryChange: (String) -> Unit,
    selectedStation: RadioStation,
    isPlaying: Boolean,
    favoriteIds: Set<String>,
    catalogStations: List<CatalogStation>,
    catalogLoading: Boolean,
    catalogError: String?,
    onStationClick: (RadioStation) -> Unit,
    onStationFavoriteClick: (RadioStation) -> Unit
) {
    val screenContext = LocalContext.current
    var ownCountries by remember { mutableStateOf(OwnCountriesPreference.get(screenContext)) }
    var choosingCountries by remember { mutableStateOf(false) }
    var categoryFilter by rememberSaveable { mutableStateOf(ALL_CATEGORIES) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val searchableStations = remember(stations, catalogStations, selectedStation) {
        stationsForRadioList(
            stations = stations + catalogStations.mapNotNull { it.toPlayableRadioStationOrNull() },
            selectedStation = selectedStation
        ).distinctBy { it.stableId }
    }
    val countryCodes = (listOf("FI", "DE", "SE", "NO", "GB", "US") +
        searchableStations.map { it.countryCode.uppercase() })
        .filter { it.isNotBlank() }
        .distinct()
    val activeCountryCode = radioCountryCode.takeIf { it in countryCodes } ?: countryCodes.firstOrNull().orEmpty()
    // The playing country is always visible as a chip, even when it is not one
    // of the user's own (chosen in the car, or accepted from the travel bar).
    val chipCountries = (ownCountries + activeCountryCode)
        .filter { it.isNotBlank() }
        .distinct()
    val activeCategory = DiscoveryCategories.firstOrNull { it.label == categoryFilter }
    val listState = rememberLazyListState()
    val resultStations = remember(
        searchableStations,
        activeCountryCode,
        categoryFilter,
        searchQuery
    ) {
        searchableStations
            .filter { it.countryCode.equals(activeCountryCode, ignoreCase = true) }
            .filter { station -> activeCategory?.matches?.invoke(station) ?: true }
            .filter { searchQuery.isBlank() || stationMatchesQuery(it, searchQuery) }
            .distinctBy { it.stableId }
    }
    val isBrowsing = searchQuery.isBlank() && categoryFilter == ALL_CATEGORIES
    val visibleRecents = if (isBrowsing) recentStations.distinctBy { it.stableId }.take(5) else emptyList()
    val showSkeleton = catalogLoading && resultStations.isEmpty()

    fun hideKeyboard() {
        keyboardController?.hide()
        focusManager.clearFocus()
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentPadding = PaddingValues(
            start = AaltoScreenHorizontalPadding,
            end = AaltoScreenHorizontalPadding,
            top = AaltoScreenTopPadding,
            bottom = AaltoScreenBottomPadding
        ),
        verticalArrangement = Arrangement.spacedBy(AaltoRowSpacing)
    ) {
        item {
            Text(
                text = stringResource(R.string.tab_search),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge
            )
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.search_placeholder)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.search_clear),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { hideKeyboard() }),
                shape = RoundedCornerShape(AaltoSurfaceRadius),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    disabledContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = AaltoBlue,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
        }

        // The phone knows which country it is in; it is told, not obeyed.
        item {
            val context = LocalContext.current
            val whereIAm = remember(context) { DeviceCountry.network(context) }
            var declined by rememberSaveable(whereIAm) {
                mutableStateOf(whereIAm == null || TravelSuggestion.declined(context, whereIAm))
            }
            if (!declined && whereIAm != null && whereIAm != activeCountryCode) {
                TravelSuggestionRow(
                    countryCode = whereIAm,
                    onShow = {
                        declined = true
                        onRadioCountryChange(whereIAm)
                    },
                    onDecline = {
                        declined = true
                        TravelSuggestion.decline(context, whereIAm)
                    }
                )
            }
        }

        // One tap per country instead of a menu and a fresh search.
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(AaltoSpaceS),
                verticalAlignment = Alignment.CenterVertically
            ) {
                chipCountries.forEach { countryCode ->
                    FilterChip(
                        selected = countryCode == activeCountryCode,
                        onClick = {
                            onRadioCountryChange(countryCode)
                            hideKeyboard()
                        },
                        label = {
                            Text(
                                text = countryName(countryCode),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
                FilterChip(
                    selected = false,
                    onClick = { choosingCountries = true },
                    label = { Text(stringResource(R.string.countries_edit), maxLines = 1) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Public,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(AaltoSpaceS),
                verticalAlignment = Alignment.CenterVertically
            ) {
                (listOf(ALL_CATEGORIES) + DiscoveryCategories.map { it.label }).forEach { label ->
                    FilterChip(
                        selected = categoryFilter == label,
                        onClick = {
                            categoryFilter = label
                            hideKeyboard()
                        },
                        label = { Text(label, maxLines = 1) }
                    )
                }
            }
        }

        if (visibleRecents.isNotEmpty()) {
            item {
                ListTitle(stringResource(R.string.search_recent))
            }
            items(
                items = visibleRecents,
                key = { station -> "recent-${station.stableId}" },
                contentType = { "station-row" }
            ) { station ->
                StationRow(
                    station = station,
                    isSelected = station.stableId == selectedStation.stableId,
                    isPlaying = isPlaying,
                    isFavorite = station.stableId in favoriteIds,
                    onClick = {
                        hideKeyboard()
                        onStationClick(station)
                    },
                    onFavoriteClick = { onStationFavoriteClick(station) }
                )
            }
        }

        item {
            ListTitle(
                stringResource(if (isBrowsing) R.string.search_stations else R.string.search_results)
            )
        }

        if (showSkeleton) {
            items(SKELETON_ROWS) { StationRowSkeleton() }
        }

        items(
            items = resultStations,
            key = { station -> station.stableId },
            contentType = { "station-row" }
        ) { station ->
            stationTrace("search_row", station)
            StationRow(
                station = station,
                isSelected = station.stableId == selectedStation.stableId,
                isPlaying = isPlaying,
                isFavorite = station.stableId in favoriteIds,
                onClick = {
                    stationTrace("search_click", station)
                    hideKeyboard()
                    onStationClick(station)
                },
                onFavoriteClick = { onStationFavoriteClick(station) }
            )
        }

        if (resultStations.isEmpty() && !catalogLoading) {
            item {
                Text(
                    text = stringResource(R.string.search_no_results),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = AaltoSpaceL)
                )
            }
        }
    }

    if (choosingCountries) {
        CountryPickerDialog(
            selected = ownCountries,
            onSelectedChange = { updated ->
                ownCountries = updated
                OwnCountriesPreference.set(screenContext, updated)
                // A country that is no longer on the list should not stay open.
                if (activeCountryCode !in updated) {
                    updated.firstOrNull()?.let(onRadioCountryChange)
                }
            },
            onDismiss = { choosingCountries = false }
        )
    }
}

@Composable
private fun ListTitle(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = AaltoSpaceS)
    )
}

/** Placeholder row shown while the catalog loads, instead of a bare text line. */
@Composable
private fun StationRowSkeleton() {
    val placeholder = MaterialTheme.colorScheme.surfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(78.dp)
            .padding(horizontal = AaltoSpaceS, vertical = AaltoSpaceS),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(AaltoLogoRadius))
                .background(placeholder)
        )
        Spacer(modifier = Modifier.width(AaltoSpaceM))
        Column(verticalArrangement = Arrangement.spacedBy(AaltoSpaceS)) {
            Box(
                modifier = Modifier
                    .width(160.dp)
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(placeholder)
            )
            Box(
                modifier = Modifier
                    .width(96.dp)
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(placeholder)
            )
        }
    }
}

/** Shown once per country: a suggestion, never a silent switch. */
@Composable
private fun TravelSuggestionRow(
    countryCode: String,
    onShow: () -> Unit,
    onDecline: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AaltoSpaceXs)
    ) {
        Column(modifier = Modifier.padding(AaltoSpaceM)) {
            Text(
                text = stringResource(R.string.travel_hint, countryName(countryCode)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(AaltoSpaceS)) {
                TextButton(onClick = onShow) {
                    Text(stringResource(R.string.travel_hint_show))
                }
                TextButton(onClick = onDecline) {
                    Text(stringResource(R.string.travel_hint_decline))
                }
            }
        }
    }
}

/**
 * Which countries get a chip. A plain list of checkboxes: nothing is merged,
 * so each country keeps its own most-listened order.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountryPickerDialog(
    selected: List<String>,
    onSelectedChange: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val codes = remember(selected) {
        (selected + browsableCountryCodes).distinct()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.countries_title)) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                items(items = codes, key = { it }) { code ->
                    val isOn = code in selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable(role = Role.Checkbox) {
                                val updated = if (isOn) selected - code else selected + code
                                // At least one country, or the row would be empty.
                                if (updated.isNotEmpty()) onSelectedChange(updated)
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = isOn, onCheckedChange = null)
                        Spacer(modifier = Modifier.width(AaltoSpaceS))
                        Text(
                            text = countryName(code),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.alarm_done))
            }
        }
    )
}
