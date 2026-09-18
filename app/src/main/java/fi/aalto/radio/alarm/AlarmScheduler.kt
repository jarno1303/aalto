package fi.aalto.radio.alarm

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import fi.aalto.radio.MainActivity
import fi.aalto.radio.R
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Schedules the wake-up radio with AlarmManager.setAlarmClock: exact, allowed
 * in Doze, and shown as the next alarm in the system status bar.
 */
internal object AlarmScheduler {

    private const val REQUEST_ALARM = 4201
    private const val REQUEST_SNOOZE = 4202
    private const val REQUEST_SHOW = 4203
    private const val REQUEST_UPCOMING = 4204
    private const val REQUEST_SKIP = 4205
    private const val REQUEST_DISABLE = 4206
    private const val UPCOMING_CHANNEL_ID = "aalto_alarm_upcoming"
    private const val UPCOMING_NOTIFICATION_ID = 4102
    private const val UPCOMING_LEAD_MS = 60L * 60_000L

    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val manager = context.getSystemService(AlarmManager::class.java) ?: return false
        return manager.canScheduleExactAlarms()
    }

    /**
     * Next regular ring time (epoch ms), honouring a skipped occurrence,
     * or null when the alarm is off.
     */
    fun nextRegularMillis(context: Context): Long? {
        val settings = AlarmStore.load(context)
        if (!settings.enabled || !settings.hasStation) return null
        val now = ZonedDateTime.now()
        var next = nextAlarmTime(now, settings.hour, settings.minute, settings.days)
        val skip = AlarmStore.skipAt(context)
        if (skip != 0L && next.toInstant().toEpochMilli() == skip) {
            next = nextAlarmTime(next, settings.hour, settings.minute, settings.days)
        }
        return next.toInstant().toEpochMilli()
    }

    /** Re-arms the regular alarm from the stored settings (or cancels it). */
    fun reschedule(context: Context) {
        val settings = AlarmStore.load(context)
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val operation = firePendingIntent(context, AlarmReceiver.ACTION_FIRE, REQUEST_ALARM)
        manager.cancel(operation)
        val upcoming = firePendingIntent(context, AlarmReceiver.ACTION_UPCOMING, REQUEST_UPCOMING)
        manager.cancel(upcoming)

        // A skipped occurrence that has passed is no longer needed.
        val skip = AlarmStore.skipAt(context)
        if (skip != 0L && skip < System.currentTimeMillis()) AlarmStore.setSkipAt(context, 0L)

        val next = nextRegularMillis(context)
        if (next != null && canScheduleExact(context)) {
            setAlarmClock(context, manager, next, operation)
            val showAt = next - UPCOMING_LEAD_MS
            if (showAt <= System.currentTimeMillis()) {
                showUpcoming(context)
            } else {
                cancelUpcomingNotification(context)
                runCatching {
                    manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, showAt, upcoming)
                }
            }
        } else {
            cancelUpcomingNotification(context)
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
        val regular = nextRegularMillis(context)
        val snooze = AlarmStore.snoozeAt(context).takeIf { it > System.currentTimeMillis() }
        return listOfNotNull(regular, snooze).minOrNull()
    }

    /** "Ohita tämä kerta": skips the next occurrence (a one-time alarm is switched off). */
    fun skipNext(context: Context) {
        val settings = AlarmStore.load(context)
        val next = nextRegularMillis(context) ?: return
        if (settings.days.isEmpty()) {
            AlarmStore.save(context, settings.copy(enabled = false))
        } else {
            AlarmStore.setSkipAt(context, next)
        }
        reschedule(context)
        cancelUpcomingNotification(context)
    }

    /** Quiet "Tuleva herätys" notification with a skip action. */
    @android.annotation.SuppressLint("MissingPermission")
    fun showUpcoming(context: Context) {
        val settings = AlarmStore.load(context)
        val next = nextRegularMillis(context) ?: return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    UPCOMING_CHANNEL_ID,
                    context.getString(R.string.alarm_upcoming_channel),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        val time = Instant.ofEpochMilli(next).atZone(ZoneId.systemDefault())
        val text = listOfNotNull(
            "${dayShort(time.dayOfWeek)} ${formatClock(time.hour, time.minute)}",
            settings.stationName
        ).joinToString(" · ")
        val open = PendingIntent.getActivity(
            context,
            REQUEST_SHOW,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, UPCOMING_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(context.getString(R.string.alarm_upcoming_title))
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .addAction(
                0,
                context.getString(R.string.alarm_skip_once),
                firePendingIntent(context, AlarmReceiver.ACTION_SKIP, REQUEST_SKIP)
            )
            .addAction(
                0,
                context.getString(R.string.alarm_switch_off),
                firePendingIntent(context, AlarmReceiver.ACTION_DISABLE, REQUEST_DISABLE)
            )
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(UPCOMING_NOTIFICATION_ID, notification) }
    }

    fun cancelUpcomingNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(UPCOMING_NOTIFICATION_ID)
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
