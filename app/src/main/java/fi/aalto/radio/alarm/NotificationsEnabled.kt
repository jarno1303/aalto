package fi.aalto.radio.alarm

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Whether the app may post notifications, as state rather than as a one-time
 * reading. The warning about missing notifications has to disappear the moment
 * permission is given, not the next time the screen happens to be rebuilt.
 */
@Stable
internal class NotificationsEnabledState(private val context: Context) {

    var enabled by mutableStateOf(read())
        private set

    fun refresh() {
        enabled = read()
    }

    private fun read(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()
}

/**
 * Follows the permission for as long as it is on screen: refreshed when the
 * app is resumed, which covers granting the permission in the system dialog
 * and in the system settings alike. [NotificationsEnabledState.refresh] covers
 * the third case, an answer from a permission launcher.
 */
@Composable
internal fun rememberNotificationsEnabled(): NotificationsEnabledState {
    val context = LocalContext.current
    val state = remember(context) { NotificationsEnabledState(context.applicationContext) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, state) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) state.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return state
}
