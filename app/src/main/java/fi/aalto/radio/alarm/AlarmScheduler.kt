package fi.aalto.radio.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import fi.aalto.radio.MainActivity
import java.time.ZonedDateTime

/**
 * Schedules the wake-up radio with AlarmManager.setAlarmClock: exact, allowed
 * in Doze, and shown as the next alarm in the system status bar.
 */
internal object AlarmScheduler {

    private const val REQUEST_ALARM = 4201
    private const val REQUEST_SNOOZE = 4202
    private const val REQUEST_SHOW = 4203

    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val manager = context.getSystemService(AlarmManager::class.java) ?: return false
        return manager.canScheduleExactAlarms()
    }

    /** Re-arms the regular alarm from the stored settings (or cancels it). */
    fun reschedule(context: Context) {
        val settings = AlarmStore.load(context)
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val operation = firePendingIntent(context, AlarmReceiver.ACTION_FIRE, REQUEST_ALARM)
        manager.cancel(operation)

        if (settings.enabled && settings.hasStation && canScheduleExact(context)) {
            val next = nextAlarmTime(ZonedDateTime.now(), settings.hour, settings.minute, settings.days)
            setAlarmClock(context, manager, next.toInstant().toEpochMilli(), operation)
        }

        // A snooze that is still ahead survives reboots and time changes.
        val snoozeAt = AlarmStore.snoozeAt(context)
        if (snoozeAt > System.currentTimeMillis() && canScheduleExact(context)) {
            setAlarmClock(
                context,
                manager,
                snoozeAt,
                firePendingIntent(context, AlarmReceiver.ACTION_SNOOZE_FIRE, REQUEST_SNOOZE)
            )
        }
    }

    fun scheduleSnooze(context: Context, minutes: Int) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        if (!canScheduleExact(context)) return
        val at = System.currentTimeMillis() + minutes * 60_000L
        AlarmStore.setSnoozeAt(context, at)
        setAlarmClock(
            context,
            manager,
            at,
            firePendingIntent(context, AlarmReceiver.ACTION_SNOOZE_FIRE, REQUEST_SNOOZE)
        )
    }

    fun cancelSnooze(context: Context) {
        AlarmStore.setSnoozeAt(context, 0L)
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        manager.cancel(firePendingIntent(context, AlarmReceiver.ACTION_SNOOZE_FIRE, REQUEST_SNOOZE))
    }

    /** Next ring time in epoch millis, or null when nothing is scheduled. */
    fun nextRingMillis(context: Context): Long? {
        val settings = AlarmStore.load(context)
        val regular = if (settings.enabled && settings.hasStation) {
            nextAlarmTime(ZonedDateTime.now(), settings.hour, settings.minute, settings.days)
                .toInstant().toEpochMilli()
        } else {
            null
        }
        val snooze = AlarmStore.snoozeAt(context).takeIf { it > System.currentTimeMillis() }
        return listOfNotNull(regular, snooze).minOrNull()
    }

    private fun setAlarmClock(
        context: Context,
        manager: AlarmManager,
        triggerAt: Long,
        operation: PendingIntent
    ) {
        val show = PendingIntent.getActivity(
            context,
            REQUEST_SHOW,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        runCatching {
            manager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, show), operation)
        }
    }

    private fun firePendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
