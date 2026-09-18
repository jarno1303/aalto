package fi.aalto.radio

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.History
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs


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
        // The station changes with a short settle, the same idea as the small
        // player's crossfade but sized for the big logo: the new logo arrives
        // instead of replacing the old one between two frames.
        AnimatedContent(
            targetState = station,
            transitionSpec = {
                (
                    fadeIn(animationSpec = tween(durationMillis = 220)) +
                        scaleIn(initialScale = 0.90f, animationSpec = tween(durationMillis = 260))
                    ).togetherWith(
                    fadeOut(animationSpec = tween(durationMillis = 160)) +
                        scaleOut(targetScale = 1.06f, animationSpec = tween(durationMillis = 220))
                )
            },
            label = "nowPlayingStation"
        ) { current ->
            StationLogo(
                station = current,
                size = logoSize,
                cornerRadius = cornerRadius,
                circular = circular
            )
        }
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
    trackTitle: String? = null,
    expanded: Boolean = false,
    onOpenHistory: (() -> Unit)? = null
) {
    stationTrace("ui_current_state", station)
    val canSkip = onPrevious != null && onNext != null
    val sleepMinutes = rememberSleepTimerMinutes()
    val showTrack = trackTitle != null && isPlaying && playbackError == null
    // Small screens and large system fonts get a slightly smaller card, so
    // nothing is pushed off the edge or clipped.
    val configuration = LocalConfiguration.current
    val tight = configuration.screenWidthDp < 360 || configuration.fontScale > 1.3f
    val sidePadding = if (tight) AaltoSpaceM else AaltoSpaceL
    val logoSize = when {
        expanded && tight -> 88.dp
        expanded -> 112.dp
        tight -> 56.dp
        else -> 64.dp
    }
    val nameSize = when {
        expanded && tight -> 20.sp
        expanded -> 24.sp
        else -> 18.sp
    }
    val playSize = when {
        expanded && tight -> 64.dp
        expanded -> 72.dp
        else -> 56.dp
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(
                start = sidePadding,
                end = sidePadding,
                top = AaltoSpaceM,
                bottom = AaltoSpaceS
            )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val logo: @Composable () -> Unit = {
                    NowPlayingLogo(
                        station = station,
                        isPlaying = isPlaying,
                        logoSize = logoSize,
                        showHalo = expanded
                    )
                }
                if (canSkip) {
                    SwipeableStation(onPrevious = onPrevious!!, onNext = onNext!!, content = logo)
                } else {
                    logo()
                }

                Spacer(modifier = Modifier.width(if (tight) AaltoSpaceS else AaltoSpaceM))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = station.name,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        fontSize = nameSize,
                        maxLines = if (expanded) 2 else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    // Tapping what is playing opens the songs that have played:
                    // the question "what was that?" is asked right here.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = if (onOpenHistory == null) {
                            Modifier
                        } else {
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(
                                    onClickLabel = stringResource(R.string.history_open),
                                    role = Role.Button,
                                    onClick = onOpenHistory
                                )
                        }
                    ) {
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
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (onOpenHistory != null) {
                            Icon(
                                imageVector = Icons.Outlined.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(start = AaltoSpaceXs)
                                    .size(16.dp)
                            )
                        }
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
                    horizontalArrangement = Arrangement.spacedBy(if (tight) 0.dp else AaltoSpaceS)
                ) {
                    SkipButton(visible = canSkip, isNext = false, onClick = { onPrevious?.invoke() })
                    NowPlayingPlayPauseButton(
                        isPlaying = isPlaying,
                        isConnecting = isConnecting,
                        playSize = playSize,
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
    var pickerOpen by remember { mutableStateOf(false) }

    IconButton(
        onClick = { pickerOpen = true },
        modifier = Modifier.size(48.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.Timer,
            contentDescription = stringResource(R.string.sleep_timer),
            tint = if (active) AaltoBlue else tint,
            modifier = Modifier.size(24.dp)
        )
    }

    if (pickerOpen) {
        // The same dial as the alarm time, so every time choice feels alike.
        val options = listOf(stringResource(R.string.sleep_timer_off)) +
            SleepTimer.choicesMinutes.map { stringResource(R.string.sleep_timer_minutes, it) }
        val selectedIndex = SleepTimer.selectedMinutes
            ?.let { SleepTimer.choicesMinutes.indexOf(it) + 1 }
            ?.takeIf { it > 0 }
            ?: 0
        WheelChoiceDialog(
            title = stringResource(R.string.sleep_timer),
            options = options,
            selectedIndex = selectedIndex,
            onConfirm = { index ->
                pickerOpen = false
                if (index == 0) {
                    SleepTimer.cancel()
                } else {
                    SleepTimer.start(context, SleepTimer.choicesMinutes[index - 1])
                }
            },
            onDismiss = { pickerOpen = false }
        )
    }
}
