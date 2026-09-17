package fi.aalto.radio

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay

private const val NIGHT_SCREEN_BRIGHTNESS = 0.02f
private const val NIGHT_CONTROLS_VISIBLE_MS = 4_000L

/**
 * Night Screen window setup: black system bars, very low screen brightness
 * and keep-screen-on while active (useful on a car mount). Everything is
 * restored when the Night Screen closes.
 */
@Composable
@Suppress("DEPRECATION")
internal fun NightScreenSystemBars(active: Boolean) {
    val activity = LocalActivity.current ?: return

    DisposableEffect(activity, active) {
        val window = activity.window
        val previousStatusBarColor = window.statusBarColor
        val previousNavigationBarColor = window.navigationBarColor
        val previousBrightness = window.attributes.screenBrightness
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        val previousLightStatusBars = controller.isAppearanceLightStatusBars
        val previousLightNavigationBars = controller.isAppearanceLightNavigationBars

        if (active) {
            window.statusBarColor = AaltoNightBlack.toArgb()
            window.navigationBarColor = AaltoNightBlack.toArgb()
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
            window.attributes = window.attributes.apply {
                screenBrightness = NIGHT_SCREEN_BRIGHTNESS
            }
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        onDispose {
            window.statusBarColor = previousStatusBarColor
            window.navigationBarColor = previousNavigationBarColor
            controller.isAppearanceLightStatusBars = previousLightStatusBars
            controller.isAppearanceLightNavigationBars = previousLightNavigationBars
            if (active) {
                window.attributes = window.attributes.apply {
                    screenBrightness = previousBrightness
                }
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
}

/**
 * Near-black listening surface for dark car cabins.
 *
 * First tap reveals a few large, dim controls for a moment; they fade out by
 * themselves. The close button (or Back) returns to the normal view.
 */
@Composable
internal fun NightScreen(
    station: RadioStation,
    isPlaying: Boolean,
    isConnecting: Boolean,
    playbackError: String?,
    onPlayPause: () -> Unit,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
    onExit: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    var controlsVisible by remember { mutableStateOf(false) }
    // Any interaction restarts the auto-hide timer.
    var interactionCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(controlsVisible, interactionCount) {
        if (controlsVisible) {
            delay(NIGHT_CONTROLS_VISIBLE_MS)
            controlsVisible = false
        }
    }

    fun touched() {
        interactionCount++
    }

    val playbackText = stringResource(
        when {
            playbackError != null -> R.string.night_state_error
            isConnecting -> R.string.state_connecting
            isPlaying -> R.string.night_state_playing
            else -> R.string.state_paused
        }
    )
    val dim = Color.White.copy(alpha = 0.55f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AaltoNightBlack)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClickLabel = stringResource(R.string.night_screen_show_controls),
                onClick = {
                    controlsVisible = !controlsVisible
                    touched()
                }
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = AaltoSpaceXxl, vertical = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(station.logoColorArgb).copy(alpha = 0.28f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = station.initials,
                    color = Color.White.copy(alpha = 0.70f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            Text(
                text = station.name,
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = playbackText,
                color = if (playbackError == null) {
                    Color.White.copy(alpha = 0.36f)
                } else {
                    AaltoNightError.copy(alpha = 0.58f)
                },
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(AaltoSpaceXxl))

            // Reserve the controls' space so the text does not jump when they appear.
            Box(
                modifier = Modifier.height(72.dp),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(animationSpec = tween(durationMillis = 160)),
                    exit = fadeOut(animationSpec = tween(durationMillis = 300))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AaltoSpaceXl)
                    ) {
                        if (onPrevious != null) {
                            NightControl(
                                icon = { Icon(Icons.Filled.SkipPrevious, stringResource(R.string.action_previous_station), tint = dim) },
                                onClick = {
                                    touched()
                                    onPrevious()
                                }
                            )
                        }
                        NightControl(
                            large = true,
                            icon = {
                                val showPause = isPlaying || isConnecting
                                Icon(
                                    imageVector = if (showPause) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = stringResource(
                                        if (showPause) R.string.action_pause else R.string.action_play
                                    ),
                                    tint = dim,
                                    modifier = Modifier.size(36.dp)
                                )
                            },
                            onClick = {
                                touched()
                                onPlayPause()
                            }
                        )
                        if (onNext != null) {
                            NightControl(
                                icon = { Icon(Icons.Filled.SkipNext, stringResource(R.string.action_next_station), tint = dim) },
                                onClick = {
                                    touched()
                                    onNext()
                                }
                            )
                        }
                    }
                }
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(animationSpec = tween(durationMillis = 160)),
            exit = fadeOut(animationSpec = tween(durationMillis = 300)),
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            IconButton(onClick = onExit) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.night_screen_exit),
                    tint = dim
                )
            }
        }
    }
}

@Composable
private fun NightControl(
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    large: Boolean = false
) {
    Box(
        modifier = Modifier
            .size(if (large) 72.dp else 56.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (large) 0.08f else 0.04f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        icon()
    }
}
