package fi.aalto.radio.alarm

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * The two system settings a reliable wake-up radio depends on, followed as
 * state (refreshed on resume) so the warnings in the alarm sheet go away the
 * moment the user allows them:
 *  - exact alarms: without them the alarm may be minutes late,
 *  - full-screen alarms (Android 14+): without them the alarm still rings,
 *    but its screen does not open over the lock screen.
 */
@Stable
internal class AlarmPermissionsState(private val context: Context) {
    var exactAllowed by mutableStateOf(AlarmScheduler.canScheduleExact(context))
        private set
    var fullScreenAllowed by mutableStateOf(readFullScreen())
        private set

    fun refresh() {
        val wasExact = exactAllowed
        exactAllowed = AlarmScheduler.canScheduleExact(context)
        fullScreenAllowed = readFullScreen()
        // Allowed just now: move the alarm from inexact to exact at once.
        if (exactAllowed && !wasExact) AlarmScheduler.reschedule(context)
    }

    private fun readFullScreen(): Boolean {
        if (Build.VERSION.SDK_INT < 34) return true
        val manager = context.getSystemService(NotificationManager::class.java) ?: return true
        return runCatching { manager.canUseFullScreenIntent() }.getOrDefault(true)
    }

    fun openExactSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    .setData(Uri.parse("package:" + context.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun openFullScreenSettings() {
        if (Build.VERSION.SDK_INT < 34) return
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                    .setData(Uri.parse("package:" + context.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

@Composable
internal fun rememberAlarmPermissions(): AlarmPermissionsState {
    val context = LocalContext.current
    val state = remember(context) { AlarmPermissionsState(context.applicationContext) }
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
