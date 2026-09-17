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
@Suppress("DEPRECATION")
internal fun NightScreenSystemBars(active: Boolean) {
    val activity = LocalActivity.current ?: return

    DisposableEffect(activity, active) {
        val window = activity.window
        val previousStatusBarColor = window.statusBarColor
        val previousNavigationBarColor = window.navigationBarColor
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        val previousLightStatusBars = controller.isAppearanceLightStatusBars
        val previousLightNavigationBars = controller.isAppearanceLightNavigationBars

        if (active) {
            window.statusBarColor = AaltoNightBlack.toArgb()
            window.navigationBarColor = AaltoNightBlack.toArgb()
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        }

        onDispose {
            window.statusBarColor = previousStatusBarColor
            window.navigationBarColor = previousNavigationBarColor
            controller.isAppearanceLightStatusBars = previousLightStatusBars
            controller.isAppearanceLightNavigationBars = previousLightNavigationBars
        }
    }
}

@Composable
internal fun NightScreen(
    station: RadioStation,
    isPlaying: Boolean,
    playbackError: String?,
    onExit: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val playbackText = stringResource(
        when {
            playbackError != null -> R.string.night_state_error
            isPlaying -> R.string.night_state_playing
            else -> R.string.state_paused
        }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AaltoNightBlack)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClickLabel = stringResource(R.string.night_screen_exit),
                onClick = onExit
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
                fontWeight = FontWeight.SemiBold
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
        }
    }
}
