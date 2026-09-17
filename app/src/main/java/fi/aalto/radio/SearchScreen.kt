package fi.aalto.radio

import android.os.Bundle
import android.util.Log
import fi.aalto.radio.catalog.CatalogFreshness
import fi.aalto.radio.catalog.CatalogReadResult
import fi.aalto.radio.catalog.CatalogStation
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.compose.foundation.isSystemInDarkTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URI
import java.util.Locale

@Composable
internal fun SearchScreen(
    paddingValues: PaddingValues,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    stations: List<RadioStation>,
    radioCountryCode: String,
    onRadioCountryChange: (String) -> Unit,
    selectedStation: RadioStation,
    favoriteIds: Set<String>,
    catalogStations: List<CatalogStation>,
    catalogLoading: Boolean,
    catalogError: String?,
    onStationClick: (RadioStation) -> Unit,
    onStationFavoriteClick: (RadioStation) -> Unit
) {
    var countryMenuExpanded by remember { mutableStateOf(false) }
    var categoryFilter by rememberSaveable { mutableStateOf("Kaikki") }

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
                fontSize = 21.sp,
                fontWeight = FontWeight.SemiBold
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
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                singleLine = true,
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

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AaltoSpaceXs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    TextButton(onClick = { countryMenuExpanded = true }) {
                        Text(
                            text = countryName(activeCountryCode),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = countryMenuExpanded,
                        onDismissRequest = { countryMenuExpanded = false }
                    ) {
                        countryCodes.forEach { countryCode ->
                            DropdownMenuItem(
                                text = { Text(countryName(countryCode)) },
                                onClick = {
                                    onRadioCountryChange(countryCode)
                                    countryMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        if (catalogLoading) {
            item {
                Text(
                    text = stringResource(R.string.catalog_loading),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }

        if (catalogError != null && catalogStations.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.catalog_unavailable),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
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
                (listOf("Kaikki") + DiscoveryCategories.map { it.label }).forEach { label ->
                    FilterChip(
                        selected = categoryFilter == label,
                        onClick = { categoryFilter = label },
                        label = { Text(label, maxLines = 1) }
                    )
                }
            }
        }

        item {
            Text(
                text = stringResource(
                    if (searchQuery.isBlank() && categoryFilter == "Kaikki") R.string.search_stations else R.string.search_results
                ),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = AaltoSpaceS)
            )
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
                isFavorite = station.stableId in favoriteIds,
                onClick = {
                    stationTrace("search_click", station)
                    onStationClick(station)
                },
                onFavoriteClick = { onStationFavoriteClick(station) }
            )
        }

        if (resultStations.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.search_no_results),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = AaltoSpaceL)
                )
            }
        }
    }
}
