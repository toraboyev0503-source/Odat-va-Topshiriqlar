package com.memora.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDateTime
import java.time.ZoneId

class StatsScheduler(private val context: Context) {
    private val manager = context.getSystemService(AlarmManager::class.java)

    fun scheduleAll() {
        scheduleMonthly()
        scheduleYearly()
    }

    fun scheduleMonthly() {
        val now = LocalDateTime.now()
        var next = now.withDayOfMonth(1).withHour(8).withMinute(0).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusMonths(1).withDayOfMonth(1)
        schedule(next, MODE_MONTHLY, 70_001)
    }

    fun scheduleYearly() {
        val now = LocalDateTime.now()
        var next = now.withMonth(1).withDayOfMonth(1).withHour(8).withMinute(0).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusYears(1).withMonth(1).withDayOfMonth(1)
        schedule(next, MODE_YEARLY, 70_002)
    }

    private fun schedule(next: LocalDateTime, mode: String, requestCode: Int) {
        val intent = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, StatsReceiver::class.java).apply {
                action = ACTION_STATS
                putExtra(EXTRA_MODE, mode)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val time = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !manager.canScheduleExactAlarms()) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, intent)
        } else {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, intent)
        }
    }

    companion object {
        const val ACTION_STATS = "com.memora.app.ACTION_STATS"
        const val EXTRA_MODE = "stats_mode"
        const val MODE_MONTHLY = "monthly"
        const val MODE_YEARLY = "yearly"
    }
}
