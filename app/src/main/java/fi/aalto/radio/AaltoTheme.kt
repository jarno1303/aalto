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

internal val AaltoBlue = Color(0xFF1769FF)

internal val AaltoLightBackground = Color(0xFFF4F6F8)

internal val AaltoLightSurface = Color(0xFFFBFCFD)

internal val AaltoLightLine = Color(0xFFE1E7ED)

internal val AaltoLightSelectedSurface = Color(0xFFEAF2FF)

internal val AaltoLightText = Color(0xFF101317)

internal val AaltoLightMuted = Color(0xFF68727D)

internal val AaltoLightLogoSurface = Color(0xFFF0F3F6)

internal val AaltoDarkBackground = Color(0xFF0E1114)

internal val AaltoDarkSurface = Color(0xFF171B20)

internal val AaltoDarkLine = Color(0xFF2B333B)

internal val AaltoDarkSelectedSurface = Color(0xFF1A2A42)

internal val AaltoDarkText = Color(0xFFF3F6F8)

internal val AaltoDarkMuted = Color(0xFF9BA6B2)

internal val AaltoDarkLogoSurface = Color(0xFF232A31)

internal val AaltoNightBlack = Color(0xFF000000)

internal val AaltoLightError = Color(0xFFC62828)

internal val AaltoDarkError = Color(0xFFFF8A80)

internal val AaltoNightError = Color(0xFFFFB8B8)

internal val AaltoSpaceXs = 4.dp

internal val AaltoSpaceS = 8.dp

internal val AaltoSpaceM = 12.dp

internal val AaltoSpaceL = 16.dp

internal val AaltoSpaceXl = 24.dp

internal val AaltoSpaceXxl = 32.dp

internal val AaltoScreenHorizontalPadding = AaltoSpaceL

internal val AaltoScreenTopPadding = AaltoSpaceL

internal val AaltoScreenBottomPadding = AaltoSpaceXl

internal val AaltoRowSpacing = AaltoSpaceS

internal val AaltoSurfaceRadius = 14.dp

internal val AaltoLogoRadius = 12.dp

@Composable
internal fun AaltoTheme(
    content: @Composable () -> Unit
) {
    val darkTheme = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (darkTheme) {
            androidx.compose.material3.darkColorScheme(
                primary = AaltoBlue,
                onPrimary = Color.White,
                background = AaltoDarkBackground,
                onBackground = AaltoDarkText,
                surface = AaltoDarkSurface,
                onSurface = AaltoDarkText,
                surfaceVariant = AaltoDarkLogoSurface,
                onSurfaceVariant = AaltoDarkMuted,
                outline = AaltoDarkLine,
                primaryContainer = AaltoDarkSelectedSurface,
                onPrimaryContainer = AaltoDarkText,
                error = AaltoDarkError,
                onError = AaltoDarkBackground
            )
        } else {
            androidx.compose.material3.lightColorScheme(
                primary = AaltoBlue,
                onPrimary = Color.White,
                background = AaltoLightBackground,
                onBackground = AaltoLightText,
                surface = AaltoLightSurface,
                onSurface = AaltoLightText,
                surfaceVariant = AaltoLightLogoSurface,
                onSurfaceVariant = AaltoLightMuted,
                outline = AaltoLightLine,
                primaryContainer = AaltoLightSelectedSurface,
                onPrimaryContainer = AaltoLightText,
                error = AaltoLightError,
                onError = Color.White
            )
        },
        content = content
    )
}
