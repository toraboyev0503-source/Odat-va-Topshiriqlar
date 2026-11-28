package com.memora.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDateTime
import java.time.ZoneId

class TimeTraceScheduler(private val context: Context) {
    private val manager = context.getSystemService(AlarmManager::class.java)

    fun scheduleDaily(hour: Int, minute: Int) {
        schedule(nextAt(hour, minute), MODE_DAILY, RC_DAILY)
    }

    fun cancelDaily() = manager.cancel(pending(MODE_DAILY, RC_DAILY))

    fun scheduleMilestoneCheck() {
        // One quiet daily check is cheap and lets the receiver decide whether today is a
        // 1/3/6/12-month anniversary. It never notifies when there is no milestone.
        schedule(nextAt(20, 15), MODE_MILESTONE, RC_MILESTONE)
    }

    fun cancelMilestoneCheck() = manager.cancel(pending(MODE_MILESTONE, RC_MILESTONE))

    private fun nextAt(hour: Int, minute: Int): LocalDateTime {
        val now = LocalDateTime.now()
        var next = now.withHour(hour.coerceIn(0, 23)).withMinute(minute.coerceIn(0, 59)).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return next
    }

    private fun pending(mode: String, requestCode: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        requestCode,
        Intent(context, TimeTraceReceiver::class.java).apply {
            action = ACTION_TIME_TRACE
            putExtra(EXTRA_MODE, mode)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun schedule(next: LocalDateTime, mode: String, requestCode: Int) {
        val p = pending(mode, requestCode)
        val at = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !manager.canScheduleExactAlarms()) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
        } else {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
        }
    }

    companion object {
        const val ACTION_TIME_TRACE = "com.memora.app.ACTION_TIME_TRACE"
        const val EXTRA_MODE = "time_trace_mode"
        const val MODE_DAILY = "daily"
        const val MODE_MILESTONE = "milestone"
        private const val RC_DAILY = 80_001
        private const val RC_MILESTONE = 80_002
    }
}
