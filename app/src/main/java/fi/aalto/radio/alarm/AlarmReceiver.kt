package fi.aalto.radio.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** Fires the wake-up radio (regular alarm or snooze). Not exported. */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_FIRE -> {
                val settings = AlarmStore.load(context)
                if (settings.days.isEmpty()) {
                    // One-time alarm: switch off after it rings.
                    AlarmStore.save(context, settings.copy(enabled = false))
                }
                startRinging(context)
                AlarmScheduler.reschedule(context)
            }
            ACTION_SNOOZE_FIRE -> {
                AlarmStore.setSnoozeAt(context, 0L)
                startRinging(context)
            }
        }
    }

    private fun startRinging(context: Context) {
        val serviceIntent = Intent(context, AlarmService::class.java)
            .setAction(AlarmService.ACTION_START)
        // Allowed from the background: alarm-clock alarms are exempt.
        ContextCompat.startForegroundService(context, serviceIntent)
    }

    companion object {
        const val ACTION_FIRE = "fi.aalto.radio.alarm.FIRE"
        const val ACTION_SNOOZE_FIRE = "fi.aalto.radio.alarm.SNOOZE_FIRE"
    }
}

/**
 * Re-arms alarms after reboot, app update and clock or time zone changes.
 * Exported only for these system broadcasts; it never starts playback.
 */
class AlarmBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED" ->
                AlarmScheduler.reschedule(context)
        }
    }
}
