package fi.aalto.radio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fi.aalto.radio.playback.CuratedStations
import fi.aalto.radio.playback.StationLookup

/** The picker's stations for one country, and whether the catalog answered. */
internal class OnboardingStations(
    val stations: List<RadioStation>,
    val loading: Boolean,
    /** The catalog was out of reach: Aalto's own built-in stations are shown. */
    val offline: Boolean
)

@Composable
internal fun rememberOnboardingStations(countryCode: String, retryKey: Int): OnboardingStations {
    val context = LocalContext.current
    var state by remember { mutableStateOf(OnboardingStations(emptyList(), loading = true, offline = false)) }
    LaunchedEffect(countryCode, retryKey) {
        state = OnboardingStations(state.stations, loading = true, offline = false)
        val popular = runCatching {
            StationLookup(context).popular(limit = 40, countryCode = countryCode)
        }.getOrDefault(emptyList())
        val fromCatalog = popular.any { StationCatalog.stationById(it.id) == null }
        val shown = if (fromCatalog) {
            popular
        } else {
            // No network, or nothing listed for the country: never a blank
            // screen. Aalto's own stations always play.
            CuratedStations.sort("FI", StationCatalog.allStations)
        }
        state = OnboardingStations(
            stations = shown.distinctByListing().take(FirstLaunchDecision.PICKER_SIZE),
            loading = false,
            offline = !fromCatalog
        )
    }
    return state
}

/**
 * First launch: pick the stations you listen to. No account wall, no
 * permission prompts, no tour. Tapping a station plays it and picks it, so
 * the radio is already on when the user reaches the home screen.
 */
@Composable
internal fun OnboardingScreen(
    countryCode: String,
    onCountryChange: (String) -> Unit,
    content: OnboardingStations,
    onRetry: () -> Unit,
    picks: List<String>,
    onStationTap: (RadioStation) -> Unit,
    playingStation: RadioStation?,
    isPlaying: Boolean,
    isConnecting: Boolean,
    onPlayPause: () -> Unit,
    onDone: () -> Unit,
    onSkip: () -> Unit,
    onFindMore: () -> Unit,
    onSignIn: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(horizontal = AaltoSpaceL, vertical = AaltoSpaceS),
                horizontalArrangement = Arrangement.spacedBy(AaltoSpaceS),
                verticalArrangement = Arrangement.spacedBy(AaltoSpaceXs),
                modifier = Modifier.weight(1f)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    OnboardingHeader(
                        countryCode = countryCode,
                        onCountryChange = onCountryChange,
                        onSkip = onSkip
                    )
                }

                if (content.offline && !content.loading) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        OfflineNotice(onRetry = onRetry)
                    }
                }

                if (content.loading && content.stations.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(240.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = AaltoBlue, strokeWidth = 3.dp)
                        }
                    }
                } else {
                    items(items = content.stations, key = { it.stableId }) { station ->
                        OnboardingStationTile(
                            station = station,
                            picked = station.id in picks,
                            playing = station.id == playingStation?.id && (isPlaying || isConnecting),
                            onTap = { onStationTap(station) }
                        )
                    }
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    TextButton(
                        onClick = onFindMore,
                        modifier = Modifier.padding(top = AaltoSpaceXs)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(AaltoSpaceS))
                        Text(stringResource(R.string.onboarding_find_more))
                    }
                }
            }

            OnboardingFooter(
                pickCount = picks.size,
                playingStation = playingStation,
                isPlaying = isPlaying,
                isConnecting = isConnecting,
                onPlayPause = onPlayPause,
                onDone = onDone,
                onSignIn = onSignIn
            )
        }
    }
}

@Composable
private fun OnboardingHeader(
    countryCode: String,
    onCountryChange: (String) -> Unit,
    onSkip: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = AaltoBlue,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onSkip) {
                Text(
                    text = stringResource(R.string.onboarding_skip),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(AaltoSpaceL))
        Text(
            text = stringResource(R.string.onboarding_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(AaltoSpaceS))
        Text(
            text = stringResource(R.string.onboarding_promise),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(R.string.onboarding_instruction),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = AaltoSpaceXs)
        )

        Spacer(modifier = Modifier.height(AaltoSpaceL))
        CountrySelector(countryCode = countryCode, onCountryChange = onCountryChange)
        Spacer(modifier = Modifier.height(AaltoSpaceM))
    }
}

@Composable
private fun CountrySelector(countryCode: String, onCountryChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                .clickable(
                    onClickLabel = stringResource(R.string.onboarding_change_country),
                    role = Role.Button
                ) { open = true }
                .heightIn(min = 40.dp)
                .padding(horizontal = AaltoSpaceM),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CountryFlag(countryCode)
            Spacer(modifier = Modifier.width(AaltoSpaceS))
            Text(
                text = countryName(countryCode),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(AaltoSpaceXs))
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
    if (open) {
        CountryGridSheet(
            ownCountries = listOf(countryCode),
            selected = countryCode,
            onPick = { code ->
                open = false
                if (code != countryCode) onCountryChange(code)
            },
            onDismiss = { open = false }
        )
    }
}

@Composable
private fun OfflineNotice(onRetry: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(AaltoSurfaceRadius),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = AaltoSpaceS)
    ) {
        Row(
            modifier = Modifier.padding(start = AaltoSpaceM, end = AaltoSpaceXs, top = AaltoSpaceXs, bottom = AaltoSpaceXs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.onboarding_offline),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onRetry) { Text(stringResource(R.string.onboarding_retry)) }
        }
    }
}

@Composable
private fun OnboardingStationTile(
    station: RadioStation,
    picked: Boolean,
    playing: Boolean,
    onTap: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AaltoSurfaceRadius))
            .toggleable(value = picked, role = Role.Checkbox, onValueChange = { onTap() })
            .padding(AaltoSpaceXs)
    ) {
        val logoSize = maxWidth
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(logoSize)) {
                val tileShape = RoundedCornerShape(logoSize * 0.22f)
                Box(
                    modifier = Modifier
                        .size(logoSize)
                        .clip(tileShape)
                        .then(
                            if (picked) {
                                Modifier
                                    .border(3.dp, AaltoBlue, tileShape)
                                    .padding(5.dp)
                            } else {
                                Modifier
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val inner = if (picked) logoSize - 10.dp else logoSize
                    StationLogo(station = station, size = inner, cornerRadius = inner * 0.18f)
                }
                // The check says "this is one of mine"; it sits on the corner,
                // clear of the logo itself.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (picked) AaltoBlue else MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                        .border(
                            width = 1.dp,
                            color = if (picked) AaltoBlue else MaterialTheme.colorScheme.outline,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (picked) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(AaltoSpaceXs))
            StationTileLabel(
                name = station.name,
                isSelected = playing,
                availableWidth = logoSize
            )
        }
    }
}

@Composable
private fun OnboardingFooter(
    pickCount: Int,
    playingStation: RadioStation?,
    isPlaying: Boolean,
    isConnecting: Boolean,
    onPlayPause: () -> Unit,
    onDone: () -> Unit,
    onSignIn: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = AaltoSpaceL, vertical = AaltoSpaceS),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (playingStation != null) {
                // What the preview is playing, and a way to stop or resume it.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StationLogo(station = playingStation, size = 32.dp, cornerRadius = 8.dp)
                    Spacer(modifier = Modifier.width(AaltoSpaceM))
                    Text(
                        text = playingStation.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onPlayPause) {
                        Icon(
                            imageVector = if (isPlaying || isConnecting) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = stringResource(
                                if (isPlaying || isConnecting) R.string.action_pause else R.string.action_play
                            ),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Button(
                onClick = onDone,
                enabled = pickCount > 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
            ) {
                val resources = LocalContext.current.resources
                Text(
                    text = if (pickCount > 0) {
                        resources.getQuantityString(R.plurals.onboarding_done, pickCount, pickCount)
                    } else {
                        stringResource(R.string.onboarding_pick_some)
                    },
                    style = MaterialTheme.typography.titleSmall
                )
            }
            TextButton(onClick = onSignIn) {
                Text(
                    text = stringResource(R.string.onboarding_sign_in),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
