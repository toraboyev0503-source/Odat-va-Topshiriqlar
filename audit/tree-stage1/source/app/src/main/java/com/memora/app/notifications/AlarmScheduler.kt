package com.memora.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.memora.app.data.local.ReminderEntity
import java.time.LocalDateTime
import java.time.ZoneId

class AlarmScheduler(private val context: Context) {
    private val manager = context.getSystemService(AlarmManager::class.java)

    fun scheduleReminder(reminder: ReminderEntity) {
        if (!reminder.enabled) {
            cancelReminder(reminder.id)
            return
        }
        val now = LocalDateTime.now()
        var next = now.withHour(reminder.hour).withMinute(reminder.minute).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val intent = PendingIntent.getBroadcast(
            context,
            reminderRequestCode(reminder.id),
            Intent(context, ReminderReceiver::class.java).apply {
                action = ACTION_REMINDER
                putExtra(EXTRA_REMINDER_ID, reminder.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        schedule(next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), intent)
        // A WorkManager fallback survives aggressive battery policies and covers devices that do
        // not grant exact-alarm access. ReminderNotifier de-duplicates the two delivery paths.
        ReminderFallbackScheduler.schedule(context, reminder)
    }

    fun cancelReminder(id: Long) {
        val intent = PendingIntent.getBroadcast(
            context,
            reminderRequestCode(id),
            Intent(context, ReminderReceiver::class.java).apply { action = ACTION_REMINDER },
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (intent != null) manager.cancel(intent)
        ReminderFallbackScheduler.cancel(context, id)
    }

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()

    private fun schedule(epochMillis: Long, intent: PendingIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !manager.canScheduleExactAlarms()) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMillis, intent)
        } else {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMillis, intent)
        }
    }

    private fun reminderRequestCode(id: Long): Int = 10_000 + (id % 1_000_000).toInt()

    companion object {
        const val ACTION_REMINDER = "com.memora.app.ACTION_REMINDER"
        const val EXTRA_REMINDER_ID = "reminder_id"
    }
}
