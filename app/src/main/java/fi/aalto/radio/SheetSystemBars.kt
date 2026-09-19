package fi.aalto.radio

import android.os.Build
import android.view.View
import android.view.Window
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

/**
 * A bottom sheet lives in its own window, and that window's navigation bar
 * kept the system's light scrim: a pale strip under a dark sheet. Called first
 * inside a sheet, this paints the bar with the sheet's own colour and picks
 * matching button icons.
 */
@Composable
internal fun MatchSheetNavigationBar() {
    val view = LocalView.current
    val color = MaterialTheme.colorScheme.surface
    DisposableEffect(view, color) {
        sheetWindow(view)?.let { window ->
            @Suppress("DEPRECATION")
            window.navigationBarColor = color.toArgb()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
            WindowCompat.getInsetsController(window, window.decorView)
                .isAppearanceLightNavigationBars = color.luminance() > 0.5f
        }
        onDispose { }
    }
}

private fun sheetWindow(view: View): Window? {
    var current: Any? = view
    while (current != null) {
        if (current is DialogWindowProvider) return current.window
        current = (current as? View)?.parent
    }
    return null
}
