package fi.aalto.radio

import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
    onNext: (() -> Unit)?
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
                top = AaltoScreenTopPadding,
                bottom = AaltoScreenBottomPadding
            )
    ) {
        val wideLandscape = maxWidth > maxHeight && maxWidth >= 600.dp

        val nowPlaying: @Composable (Modifier) -> Unit = { modifier ->
            NowPlaying(
                station = selectedStation,
                isPlaying = isPlaying,
                isFavorite = selectedStation.stableId in favoriteIds,
                isConnecting = isConnecting,
                playbackError = playbackError,
                onPlayPause = onPlayPause,
                onRetry = onRetry,
                onFavorite = onFavorite,
                onNightScreen = onNightScreen,
                onPrevious = onPrevious,
                onNext = onNext,
                modifier = modifier
            )
        }

        if (wideLandscape) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(AaltoSpaceXl)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    TopBar(onOpenSettings)
                    nowPlaying(Modifier.fillMaxWidth().weight(1f))
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    SectionHeader(
                        title = shelfTitle,
                        action = stringResource(R.string.tab_search),
                        onAction = onFind
                    )
                    if (!showOwnStations) {
                        OwnStationsHint()
                    }
                    Spacer(modifier = Modifier.height(AaltoSpaceXs))
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 120.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = AaltoSpaceS),
                        horizontalArrangement = Arrangement.spacedBy(AaltoSpaceS),
                        verticalArrangement = Arrangement.spacedBy(AaltoSpaceS)
                    ) {
                        gridItems(
                            items = shelfStations,
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
                                width = 120.dp
                            )
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(AaltoSpaceS)
            ) {
                TopBar(onOpenSettings)

                nowPlaying(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )

                SectionHeader(
                    title = shelfTitle,
                    action = stringResource(R.string.tab_search),
                    onAction = onFind
                )

                if (!showOwnStations) {
                    OwnStationsHint()
                }

                val carouselState = rememberLazyListState()
                val snapFlingBehavior = rememberSnapFlingBehavior(carouselState)
                LazyRow(
                    state = carouselState,
                    flingBehavior = snapFlingBehavior,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = AaltoSpaceXs, vertical = AaltoSpaceXs),
                    horizontalArrangement = Arrangement.spacedBy(AaltoSpaceS)
                ) {
                    items(
                        items = shelfStations,
                        key = { it.stableId },
                        contentType = { "station-card" }
                    ) { station ->
                        StationCard(
                            station = station,
                            isSelected = station.stableId == selectedStation.stableId,
                            isPlaying = isPlaying,
                            isFavorite = station.stableId in favoriteIds,
                            onClick = { onStationClick(station) },
                            onFavoriteClick = { onStationFavoriteClick(station) }
                        )
                    }
                }
            }
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
