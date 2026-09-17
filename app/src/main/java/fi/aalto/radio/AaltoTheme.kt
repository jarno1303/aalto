package fi.aalto.radio

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// =============================================================
// COLOURS
// =============================================================

internal val AaltoBlue = Color(0xFF1769FF)

// Background is a clear step darker than cards, so screen areas separate.
internal val AaltoLightBackground = Color(0xFFE8EDF3)
internal val AaltoLightSurface = Color(0xFFFFFFFF)
internal val AaltoLightLine = Color(0xFFD5DDE6)
internal val AaltoLightSelectedSurface = Color(0xFFDDE9FF)
internal val AaltoLightText = Color(0xFF101317)
internal val AaltoLightMuted = Color(0xFF68727D)
internal val AaltoLightLogoSurface = Color(0xFFEFF3F7)
internal val AaltoLightError = Color(0xFFC62828)

internal val AaltoDarkBackground = Color(0xFF0B0D10)
internal val AaltoDarkSurface = Color(0xFF1B2027)
internal val AaltoDarkLine = Color(0xFF323B45)
internal val AaltoDarkSelectedSurface = Color(0xFF1C3050)
internal val AaltoDarkText = Color(0xFFF3F6F8)
internal val AaltoDarkMuted = Color(0xFF9BA6B2)
internal val AaltoDarkLogoSurface = Color(0xFF232A31)
internal val AaltoDarkError = Color(0xFFFF8A80)

internal val AaltoNightBlack = Color(0xFF000000)
internal val AaltoNightError = Color(0xFFFFB8B8)

// =============================================================
// SPACING AND SHAPES
// =============================================================

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

// =============================================================
// TYPOGRAPHY
// =============================================================
//
// One scale for the whole app. Screens use MaterialTheme.typography
// roles instead of ad-hoc font sizes:
//
// headlineSmall  station name on Now Playing
// titleLarge     screen titles (Hae, Suosikit)
// titleMedium    section headers, mini player title
// bodyLarge      list row title
// bodyMedium     secondary text, state lines
// bodySmall      metadata lines
// labelLarge     text buttons
// labelMedium    card titles
// labelSmall     bottom navigation
// =============================================================

internal val AaltoTypography = Typography(
    headlineSmall = TextStyle(fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium)
)

// =============================================================
// THEME MODE
// =============================================================

internal enum class AaltoThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

/**
 * Remembers the user's appearance choice. Small local preference, so plain
 * SharedPreferences are enough; the value is exposed as Compose state so the
 * whole app recomposes immediately when it changes.
 */
internal object AaltoThemePreferences {

    private const val PREFS = "aalto_appearance"
    private const val KEY_MODE = "theme_mode"

    var mode by mutableStateOf(AaltoThemeMode.SYSTEM)
        private set

    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val stored = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MODE, null)
        mode = AaltoThemeMode.entries.firstOrNull { it.name == stored } ?: AaltoThemeMode.SYSTEM
    }

    fun update(context: Context, newMode: AaltoThemeMode) {
        mode = newMode
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, newMode.name)
            .apply()
    }
}

@Composable
internal fun AaltoThemeMode.isDark(): Boolean = when (this) {
    AaltoThemeMode.SYSTEM -> isSystemInDarkTheme()
    AaltoThemeMode.LIGHT -> false
    AaltoThemeMode.DARK -> true
}

// =============================================================
// THEME
// =============================================================

@Composable
internal fun AaltoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) {
            darkColorScheme(
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
            lightColorScheme(
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
        typography = AaltoTypography,
        content = content
    )
}
