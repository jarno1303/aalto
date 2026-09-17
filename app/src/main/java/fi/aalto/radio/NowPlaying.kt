package fi.aalto.radio

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Now Playing hero: logo, name, state and the main play/pause control.
 *
 * Previous/next move through the user's own stations (preset mental model,
 * AGENTS.md §13). They are hidden when there are fewer than two presets.
 * A horizontal swipe on the logo does the same.
 */
@Composable
internal fun NowPlaying(
    station: RadioStation,
    isPlaying: Boolean,
    isFavorite: Boolean,
    isConnecting: Boolean,
    playbackError: String?,
    onPlayPause: () -> Unit,
    onRetry: () -> Unit,
    onFavorite: () -> Unit,
    onNightScreen: () -> Unit,
    modifier: Modifier = Modifier,
    onPrevious: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    trackTitle: String? = null
) {
    stationTrace("ui_current_state", station)
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(
                onClick = onFavorite,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = stringResource(
                        if (isFavorite) R.string.favorite_remove else R.string.favorite_add
                    ),
                    tint = if (isFavorite) AaltoBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
            NightScreenTrigger(onNightScreen = onNightScreen)
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            val logoSize = minOf(148.dp, maxWidth * 0.44f, maxHeight * 0.40f).coerceAtLeast(96.dp)
            val playSize = if (maxHeight < 300.dp) 72.dp else 84.dp
            val compact = maxHeight < 300.dp

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (onPrevious != null && onNext != null) {
                    SwipeableStation(
                        onPrevious = onPrevious,
                        onNext = onNext
                    ) {
                        NowPlayingLogo(
                            station = station,
                            isPlaying = isPlaying,
                            logoSize = logoSize
                        )
                    }
                } else {
                    NowPlayingLogo(
                        station = station,
                        isPlaying = isPlaying,
                        logoSize = logoSize
                    )
                }

                Spacer(modifier = Modifier.height(if (compact) AaltoSpaceL else AaltoSpaceXl))

                Text(
                    text = station.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = AaltoSpaceL)
                )

                Spacer(modifier = Modifier.height(AaltoSpaceS))

                Text(
                    text = trackTitle?.takeIf { isPlaying && playbackError == null }
                        ?: playbackStateText(
                            isPlaying = isPlaying,
                            isConnecting = isConnecting,
                            hasError = playbackError != null
                        ),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (playbackError != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (playbackError != null) FontWeight.Medium else FontWeight.Normal
                )

                if (playbackError != null) {
                    TextButton(
                        onClick = onRetry,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(AaltoSpaceS))
                        Text(stringResource(R.string.action_retry))
                    }
                    Spacer(modifier = Modifier.height(AaltoSpaceM))
                } else {
                    Spacer(modifier = Modifier.height(if (compact) AaltoSpaceXl else AaltoSpaceXxl))
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AaltoSpaceXl)
                ) {
                    SkipButton(
                        visible = onPrevious != null && onNext != null,
                        isNext = false,
                        onClick = { onPrevious?.invoke() }
                    )
                    NowPlayingPlayPauseButton(
                        isPlaying = isPlaying,
                        isConnecting = isConnecting,
                        playSize = playSize,
                        onClick = onPlayPause
                    )
                    SkipButton(
                        visible = onPrevious != null && onNext != null,
                        isNext = true,
                        onClick = { onNext?.invoke() }
                    )
                }
            }
        }
    }
}

@Composable
private fun SkipButton(
    visible: Boolean,
    isNext: Boolean,
    onClick: () -> Unit
) {
    // Keeps its space when hidden so the play button stays centred.
    Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
        if (visible) {
            IconButton(onClick = onClick, modifier = Modifier.size(56.dp)) {
                Icon(
                    imageVector = if (isNext) Icons.Filled.SkipNext else Icons.Filled.SkipPrevious,
                    contentDescription = stringResource(
                        if (isNext) R.string.action_next_station else R.string.action_previous_station
                    ),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(30.dp)
                )
            }
        }
    }
}

/**
 * Horizontal swipe on the logo moves to the previous/next own station.
 * The logo follows the finger slightly and springs back.
 */
@Composable
private fun SwipeableStation(
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    content: @Composable () -> Unit
) {
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val thresholdPx = with(LocalDensity.current) { 72.dp.toPx() }
    val latestPrevious by rememberUpdatedState(onPrevious)
    val latestNext by rememberUpdatedState(onNext)

    Box(
        modifier = Modifier
            .graphicsLayer {
                translationX = offsetX.value
                alpha = 1f - (abs(offsetX.value) / (thresholdPx * 4f)).coerceIn(0f, 0.4f)
            }
            .pointerInput(Unit) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        when {
                            total <= -thresholdPx -> {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                latestNext()
                            }
                            total >= thresholdPx -> {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                latestPrevious()
                            }
                        }
                        scope.launch { offsetX.animateTo(0f, tween(durationMillis = 160)) }
                    },
                    onDragCancel = {
                        scope.launch { offsetX.animateTo(0f, tween(durationMillis = 160)) }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        total += dragAmount
                        scope.launch { offsetX.snapTo(total * 0.35f) }
                    }
                )
            }
    ) {
        content()
    }
}

/**
 * Station logo with a soft, static halo while playing. No endless pulsing:
 * the halo fades in once, which keeps the screen calm in a car and saves power.
 */
@Composable
internal fun NowPlayingLogo(
    station: RadioStation,
    isPlaying: Boolean,
    logoSize: Dp,
    circular: Boolean = false,
    showHalo: Boolean = true
) {
    val cornerRadius = logoSize * 0.19f
    val haloShape = if (circular) CircleShape else RoundedCornerShape(cornerRadius * 1.06f)
    val haloAlpha by animateFloatAsState(
        targetValue = if (isPlaying && showHalo) 0.14f else 0f,
        animationSpec = tween(durationMillis = 320),
        label = "nowPlayingHaloAlpha"
    )

    Box(
        modifier = Modifier.size(if (showHalo) logoSize * 1.14f else logoSize),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(logoSize * 1.06f)
                .graphicsLayer { alpha = haloAlpha }
                .clip(haloShape)
                .background(AaltoBlue)
        )
        StationLogo(
            station = station,
            size = logoSize,
            cornerRadius = cornerRadius,
            circular = circular
        )
    }
}

@Composable
internal fun NowPlayingPlayPauseButton(
    isPlaying: Boolean,
    isConnecting: Boolean,
    playSize: Dp,
    onClick: () -> Unit
) {
    val showPause = isPlaying || isConnecting
    val actionLabel = stringResource(if (showPause) R.string.action_pause else R.string.action_play)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "nowPlayingPlayScale"
    )

    Box(
        modifier = Modifier
            .size(playSize)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(AaltoBlue)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClickLabel = actionLabel,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isConnecting) {
            CircularProgressIndicator(
                color = Color.White.copy(alpha = 0.9f),
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(playSize * 0.78f)
            )
        }
        Icon(
            imageVector = if (showPause) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = actionLabel,
            tint = Color.White,
            modifier = Modifier
                .size(if (isConnecting) playSize * 0.36f else playSize * 0.5f)
                .padding(start = if (showPause) 0.dp else 3.dp)
        )
    }
}

@Composable
private fun SelectedMark() {
    Icon(
        imageVector = Icons.Filled.Check,
        contentDescription = null,
        tint = AaltoBlue,
        modifier = Modifier.size(18.dp)
    )
}

@Composable
internal fun NightScreenTrigger(
    onNightScreen: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val hapticFeedback = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.975f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "nightScreenTriggerScale"
    )
    val iconAlpha by animateFloatAsState(
        targetValue = if (pressed) 1f else 0.82f,
        animationSpec = tween(durationMillis = 120),
        label = "nightScreenTriggerIconAlpha"
    )

    IconButton(
        onClick = {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onNightScreen()
        },
        modifier = Modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        interactionSource = interactionSource
    ) {
        Icon(
            imageVector = Icons.Outlined.DarkMode,
            contentDescription = stringResource(R.string.night_screen_open),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = iconAlpha),
            modifier = Modifier.size(22.dp)
        )
    }
}

/**
 * Compact Now Playing card for the portrait home screen.
 *
 * Logo on white on the left; name, what is on (song title when the stream
 * sends one) and station details on the right; favorite in the corner.
 * One control row: sleep timer, previous / play / next, Night Screen.
 */
@Composable
internal fun NowPlayingCard(
    station: RadioStation,
    isPlaying: Boolean,
    isFavorite: Boolean,
    isConnecting: Boolean,
    playbackError: String?,
    onPlayPause: () -> Unit,
    onRetry: () -> Unit,
    onFavorite: () -> Unit,
    onNightScreen: () -> Unit,
    modifier: Modifier = Modifier,
    onPrevious: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    trackTitle: String? = null
) {
    stationTrace("ui_current_state", station)
    val canSkip = onPrevious != null && onNext != null
    val sleepMinutes = rememberSleepTimerMinutes()
    val showTrack = trackTitle != null && isPlaying && playbackError == null

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(
                start = AaltoSpaceL,
                end = AaltoSpaceL,
                top = AaltoSpaceM,
                bottom = AaltoSpaceS
            )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val logo: @Composable () -> Unit = {
                    NowPlayingLogo(station = station, isPlaying = isPlaying, logoSize = 64.dp, showHalo = false)
                }
                if (canSkip) {
                    SwipeableStation(onPrevious = onPrevious!!, onNext = onNext!!, content = logo)
                } else {
                    logo()
                }

                Spacer(modifier = Modifier.width(AaltoSpaceM))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = station.name,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isPlaying && playbackError == null) {
                            NowPlayingIndicator(size = 16.dp)
                            Spacer(modifier = Modifier.width(AaltoSpaceXs))
                        }
                        Text(
                            text = if (showTrack) {
                                trackTitle!!
                            } else {
                                playbackStateText(
                                    isPlaying = isPlaying,
                                    isConnecting = isConnecting,
                                    hasError = playbackError != null
                                )
                            },
                            color = when {
                                playbackError != null -> MaterialTheme.colorScheme.error
                                showTrack -> MaterialTheme.colorScheme.onSurface
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (showTrack) FontWeight.Medium else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    val details = when {
                        sleepMinutes != null -> stringResource(R.string.sleep_timer_remaining, sleepMinutes)
                        else -> stationMetadataLine(station).ifBlank { station.description }
                    }
                    if (details.isNotBlank() && playbackError == null) {
                        Text(
                            text = details,
                            color = if (sleepMinutes != null) AaltoBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onFavorite,
                    modifier = Modifier
                        .size(48.dp)
                        .offset(x = AaltoSpaceS)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = stringResource(
                            if (isFavorite) R.string.favorite_remove else R.string.favorite_add
                        ),
                        tint = if (isFavorite) AaltoBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            if (playbackError != null) {
                TextButton(
                    onClick = onRetry,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(AaltoSpaceS))
                    Text(stringResource(R.string.action_retry))
                }
            } else {
                Spacer(modifier = Modifier.height(AaltoSpaceS))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SleepTimerButton(active = sleepMinutes != null)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AaltoSpaceS)
                ) {
                    SkipButton(visible = canSkip, isNext = false, onClick = { onPrevious?.invoke() })
                    NowPlayingPlayPauseButton(
                        isPlaying = isPlaying,
                        isConnecting = isConnecting,
                        playSize = 56.dp,
                        onClick = onPlayPause
                    )
                    SkipButton(visible = canSkip, isNext = true, onClick = { onNext?.invoke() })
                }
                NightScreenTrigger(onNightScreen = onNightScreen)
            }
        }
    }
}

/** Minutes left on the sleep timer, refreshed every few seconds; null when off. */
@Composable
internal fun rememberSleepTimerMinutes(): Int? {
    val endsAt = SleepTimer.endsAtElapsedMs
    val minutes by produceState<Int?>(initialValue = SleepTimer.remainingMinutes(), endsAt) {
        while (true) {
            value = SleepTimer.remainingMinutes()
            if (endsAt == null) break
            delay(5_000)
        }
    }
    return if (endsAt == null) null else minutes
}

@Composable
internal fun SleepTimerButton(
    active: Boolean,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }

    Box {
        IconButton(
            onClick = { menuOpen = true },
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Timer,
                contentDescription = stringResource(R.string.sleep_timer),
                tint = if (active) AaltoBlue else tint,
                modifier = Modifier.size(24.dp)
            )
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false }
        ) {
            Text(
                text = stringResource(R.string.sleep_timer),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = AaltoSpaceL, vertical = AaltoSpaceS)
            )
            // Always offered, so the timer can be switched off at any time.
            DropdownMenuItem(
                text = { Text(stringResource(R.string.sleep_timer_off)) },
                trailingIcon = if (!active) { { SelectedMark() } } else null,
                onClick = {
                    SleepTimer.cancel()
                    menuOpen = false
                }
            )
            SleepTimer.choicesMinutes.forEach { minutes ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sleep_timer_minutes, minutes)) },
                    trailingIcon = if (active && SleepTimer.selectedMinutes == minutes) {
                        { SelectedMark() }
                    } else {
                        null
                    },
                    onClick = {
                        SleepTimer.start(context, minutes)
                        menuOpen = false
                    }
                )
            }
        }
    }
}
