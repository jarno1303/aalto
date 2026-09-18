package fi.aalto.radio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.material3.TextButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.grid.items as gridItems

/**
 * Radio home: Now Playing and the user's own stations ("Omat asemat").
 *
 * Omat asemat is the user's quiet space (AGENTS.md §4). Popular stations are
 * shown only while the user has no favorites yet.
 *
 * Wide landscape screens (car head units, tablets) show Now Playing and the
 * station grid side by side.
 */
@Composable
internal fun RadioScreen(
    paddingValues: PaddingValues,
    selectedStation: RadioStation,
    isPlaying: Boolean,
    isConnecting: Boolean,
    playbackError: String?,
    favoriteIds: Set<String>,
    favoriteStations: List<RadioStation>,
    popularStations: List<RadioStation>,
    onPlayPause: () -> Unit,
    onRetry: () -> Unit,
    onFavorite: () -> Unit,
    onFind: () -> Unit,
    onStationClick: (RadioStation) -> Unit,
    onStationFavoriteClick: (RadioStation) -> Unit,
    onOpenSettings: () -> Unit,
    onNightScreen: () -> Unit,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
    trackTitle: String? = null,
    onEditOwnStations: (() -> Unit)? = null,
    onOpenAlarm: (() -> Unit)? = null,
    alarmLabel: String? = null,
    onRemoveOwnStation: ((RadioStation) -> Unit)? = null,
    onOpenHistory: (() -> Unit)? = null
) {
    val showOwnStations = favoriteStations.isNotEmpty()
    val shelfStations = (if (showOwnStations) favoriteStations else popularStations)
        .distinctBy { it.stableId }
    val shelfTitle = stringResource(if (showOwnStations) R.string.home_mine else R.string.home_popular)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(
                start = AaltoScreenHorizontalPadding,
                end = AaltoScreenHorizontalPadding,
                top = AaltoSpaceS,
                bottom = AaltoSpaceS
            )
    ) {
        // Landscape is the phone in a car holder or on a desk, not only a
        // tablet: two panes as soon as there is width for them.
        val landscape = maxWidth > maxHeight && maxWidth >= 480.dp
        val context = LocalContext.current
        val gridGap = AaltoSpaceS
        val gridState = rememberLazyGridState()
        var listVisible by rememberSaveable { mutableStateOf(StationListPreference.get(context)) }
        var hintSeen by rememberSaveable { mutableStateOf(HomeHintPreference.seen(context)) }
        val canRemoveOwn = showOwnStations && onRemoveOwnStation != null

        val removeStation: ((RadioStation) -> Unit)? = if (canRemoveOwn) {
            { station ->
                if (!hintSeen) {
                    hintSeen = true
                    HomeHintPreference.markSeen(context)
                }
                onRemoveOwnStation?.invoke(station)
            }
        } else {
            null
        }

        // One Now Playing for both orientations: two layouts is how the
        // landscape one fell behind in the first place.
        val nowPlayingCard: @Composable (Modifier, Boolean) -> Unit = { modifier, expanded ->
            NowPlayingCard(
                station = selectedStation,
                isPlaying = isPlaying,
                isFavorite = selectedStation.stableId in favoriteIds,
                isConnecting = isConnecting,
                playbackError = playbackError,
                onPlayPause = onPlayPause,
                onRetry = onRetry,
                onFavorite = onFavorite,
                onNightScreen = onNightScreen,
                modifier = modifier,
                onPrevious = onPrevious,
                onNext = onNext,
                trackTitle = trackTitle,
                expanded = expanded,
                onOpenHistory = onOpenHistory
            )
        }

        if (landscape) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(AaltoSpaceL)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    TopBar(onOpenSettings, onOpenAlarm, alarmLabel)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        // Expanded: the pane has the width, and the list lives
                        // in the other one.
                        nowPlayingCard(Modifier.fillMaxWidth(), true)
                    }
                }

                val paneWidth = (maxWidth - AaltoSpaceL) / 2
                val columns = ((paneWidth + gridGap) / (96.dp + gridGap)).toInt().coerceIn(2, 5)
                val cellWidth = (paneWidth - gridGap * (columns - 1)) / columns
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    SectionHeader(
                        title = shelfTitle,
                        action = if (showOwnStations && onEditOwnStations != null) {
                            stringResource(R.string.action_edit_own_stations)
                        } else {
                            stringResource(R.string.tab_search)
                        },
                        onAction = if (showOwnStations && onEditOwnStations != null) {
                            onEditOwnStations
                        } else {
                            onFind
                        }
                    )
                    if (!showOwnStations) {
                        OwnStationsHint()
                    }
                    OwnStationsGrid(
                        stations = shelfStations,
                        selectedStation = selectedStation,
                        isPlaying = isPlaying,
                        favoriteIds = favoriteIds,
                        showFavoriteButton = !showOwnStations,
                        columns = columns,
                        cellWidth = cellWidth,
                        gap = gridGap,
                        state = gridState,
                        onStationClick = onStationClick,
                        onStationFavoriteClick = onStationFavoriteClick,
                        onLongClick = removeStation,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
            }
        } else {
            // Portrait: compact Now Playing card on top, own stations as a
            // scrollable grid below. Uses the height for stations instead of
            // empty space around one big logo.
            // Four or more tiles per row: at least two full rows stay visible.
            val columns = ((maxWidth + gridGap) / (84.dp + gridGap)).toInt().coerceIn(4, 7)
            val cellWidth = (maxWidth - gridGap * (columns - 1)) / columns

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(AaltoSpaceXs)
            ) {
                TopBar(onOpenSettings, onOpenAlarm, alarmLabel)

                nowPlayingCard(Modifier.fillMaxWidth(), !listVisible)

                // The list can be folded away for a calm screen; the choice is remembered.
                StationListHeader(
                    title = shelfTitle,
                    listVisible = listVisible,
                    onToggle = {
                        listVisible = !listVisible
                        StationListPreference.set(context, listVisible)
                    },
                    action = if (showOwnStations && onEditOwnStations != null) {
                        stringResource(R.string.action_edit_own_stations)
                    } else {
                        null
                    },
                    onAction = if (showOwnStations) onEditOwnStations else null
                )

                if (!listVisible) {
                    Spacer(modifier = Modifier.weight(1f))
                } else {
                    if (!showOwnStations) {
                        OwnStationsHint()
                    }

                    // Shown once: long press is invisible until someone says so.
                    if (canRemoveOwn && !hintSeen && shelfStations.size >= 2) {
                        LongPressHint(
                            onDismiss = {
                                hintSeen = true
                                HomeHintPreference.markSeen(context)
                            }
                        )
                    }

                    OwnStationsGrid(
                        stations = shelfStations,
                        selectedStation = selectedStation,
                        isPlaying = isPlaying,
                        favoriteIds = favoriteIds,
                        showFavoriteButton = !showOwnStations,
                        columns = columns,
                        cellWidth = cellWidth,
                        gap = gridGap,
                        state = gridState,
                        onStationClick = onStationClick,
                        onStationFavoriteClick = onStationFavoriteClick,
                        onLongClick = removeStation,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * The user's own stations as tiles. Shared by both orientations, so an
 * affordance added in one is never missing from the other.
 */
@Composable
private fun OwnStationsGrid(
    stations: List<RadioStation>,
    selectedStation: RadioStation,
    isPlaying: Boolean,
    favoriteIds: Set<String>,
    showFavoriteButton: Boolean,
    columns: Int,
    cellWidth: Dp,
    gap: Dp,
    state: LazyGridState,
    onStationClick: (RadioStation) -> Unit,
    onStationFavoriteClick: (RadioStation) -> Unit,
    onLongClick: ((RadioStation) -> Unit)?,
    modifier: Modifier = Modifier
) {
    // Keep the playing station in view.
    LaunchedEffect(selectedStation.stableId, stations.size) {
        val index = stations.indexOfFirst { it.stableId == selectedStation.stableId }
        if (index >= 0) runCatching { state.animateScrollToItem(index) }
    }

    LazyVerticalGrid(
        state = state,
        columns = GridCells.Fixed(columns),
        modifier = modifier,
        contentPadding = PaddingValues(top = AaltoSpaceXs, bottom = AaltoSpaceS),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalArrangement = Arrangement.spacedBy(gap)
    ) {
        gridItems(
            items = stations,
            key = { it.stableId },
            contentType = { "station-card" }
        ) { station ->
            StationCard(
                station = station,
                isSelected = station.stableId == selectedStation.stableId,
                isPlaying = isPlaying,
                isFavorite = station.stableId in favoriteIds,
                onClick = { onStationClick(station) },
                onFavoriteClick = { onStationFavoriteClick(station) },
                width = cellWidth,
                showFavoriteButton = showFavoriteButton,
                // Long press removes an own station (with undo).
                onLongClick = onLongClick?.let { remove -> { remove(station) } }
            )
        }
    }
}

/** One-time tip about removing a station by long press. Dismissible. */
@Composable
private fun LongPressHint(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AaltoSpaceXs)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = AaltoSpaceS, vertical = AaltoSpaceXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AaltoSpaceXs)
    ) {
        Text(
            text = stringResource(R.string.home_hint_long_press),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onDismiss) {
            Text(text = stringResource(R.string.home_hint_dismiss))
        }
    }
}

@Composable
private fun OwnStationsHint() {
    Text(
        text = stringResource(R.string.home_popular_hint),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(start = AaltoSpaceXs)
    )
}

/** Header of the own-stations list, with a chevron that folds the list away. */
@Composable
private fun StationListHeader(
    title: String,
    listVisible: Boolean,
    onToggle: () -> Unit,
    action: String?,
    onAction: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = AaltoSpaceXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(AaltoSpaceS))
                .clickable(
                    onClickLabel = stringResource(
                        if (listVisible) R.string.home_hide_list else R.string.home_show_list
                    ),
                    role = Role.Button,
                    onClick = onToggle
                )
                .padding(start = AaltoSpaceXs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Icon(
                imageVector = if (listVisible) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 2.dp)
            )
        }
        if (action != null && onAction != null) {
            Box(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(AaltoSpaceS))
                    .clickable(onClickLabel = action, role = Role.Button, onClick = onAction)
                    .padding(horizontal = AaltoSpaceS),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = action,
                    color = AaltoBlue,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1
                )
            }
        }
    }
}
