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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.heightIn
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
    ownCountries: List<String>,
    onEditCountries: () -> Unit,
    selectedStation: RadioStation,
    isPlaying: Boolean,
    favoriteIds: Set<String>,
    catalogStations: List<CatalogStation>,
    catalogLoading: Boolean,
    catalogError: String?,
    onStationClick: (RadioStation) -> Unit,
    onStationFavoriteClick: (RadioStation) -> Unit
) {
    var countryMenuExpanded by remember { mutableStateOf(false) }
    var categoryFilter by rememberSaveable { mutableStateOf(ALL_CATEGORIES) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val searchableStations = remember(stations, catalogStations, selectedStation) {
        stationsForRadioList(
            stations = stations + catalogStations.mapNotNull { it.toPlayableRadioStationOrNull() },
            selectedStation = selectedStation
        ).distinctByListing()
    }
    val countryCodes = (listOf("FI", "DE", "SE", "NO", "GB", "US") +
        searchableStations.map { it.countryCode.uppercase() })
        .filter { it.isNotBlank() }
        .distinct()
    val activeCountryCode = radioCountryCode.takeIf { it in countryCodes } ?: countryCodes.firstOrNull().orEmpty()
    // The playing country is always visible as a chip, even when it is not one
    // of the user's own (chosen in the car, or accepted from the travel bar).
    val menuCountries = (ownCountries + activeCountryCode)
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

        // The browsing screen stays as it was: one country line. Which
        // countries are on offer is chosen once, in Settings.
        item {
            Box {
                TextButton(onClick = { countryMenuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Outlined.Public,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(AaltoSpaceS))
                    Text(
                        text = stringResource(R.string.search_country, countryName(activeCountryCode)),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelLarge
                    )
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                DropdownMenu(
                    expanded = countryMenuExpanded,
                    onDismissRequest = { countryMenuExpanded = false }
                ) {
                    menuCountries.forEach { countryCode ->
                        DropdownMenuItem(
                            text = { Text(countryName(countryCode)) },
                            onClick = {
                                onRadioCountryChange(countryCode)
                                countryMenuExpanded = false
                                hideKeyboard()
                            }
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.countries_edit)) },
                        onClick = {
                            countryMenuExpanded = false
                            onEditCountries()
                        }
                    )
                }
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
