package com.memora.app.notifications

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.memora.app.MainActivity
import com.memora.app.MemoraApplication
import com.memora.app.R
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TopilmalarReviewScheduler(private val context: Context) {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    fun scheduleDaily(hour: Int, minute: Int) {
        val now = LocalDateTime.now()
        var next = now.withHour(hour.coerceIn(0, 23)).withMinute(minute.coerceIn(0, 59)).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, TopilmalarReviewReceiver::class.java).setAction(ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val at = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarms.canScheduleExactAlarms()) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        } else {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }

    fun cancelDaily() {
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, TopilmalarReviewReceiver::class.java).setAction(ACTION),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pending != null) alarms.cancel(pending)
    }

    companion object {
        const val ACTION = "com.memora.app.ACTION_TOPILMALAR_REVIEW"
        private const val REQUEST_CODE = 73_510
    }
}

class TopilmalarReviewReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TopilmalarReviewScheduler.ACTION) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as MemoraApplication
                val settings = app.preferences.settings.first()
                if (settings.topilmalarReminderEnabled) {
                    val available = runCatching {
                        app.topilmalarRepository
                            .getOrCreateDailySession(settings.topilmalarDailyCount)
                            .items
                            .size
                    }.getOrDefault(0)
                    if (available > 0) notify(context, available)
                    TopilmalarReviewScheduler(context).scheduleDaily(
                        settings.topilmalarReminderHour,
                        settings.topilmalarReminderMinute
                    )
                }
            } finally {
                pending.finish()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun notify(context: Context, count: Int) {
        val notificationId = 73_511
        val open = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_OPEN_TOPILMALAR_REVIEW, true)
                putExtra(MainActivity.EXTRA_NOTIFICATION_ID, notificationId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, NotificationChannels.TOPILMALAR)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Bugungi $count ta topilmangiz tayyor.")
            .setContentText("Memora · Qayta ko‘rish")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(open)
            .build()
        try {
            val manager = NotificationManagerCompat.from(context)
            if (manager.areNotificationsEnabled()) manager.notify(notificationId, notification)
        } catch (_: SecurityException) {
            // Permission may be denied; the next scheduled day remains intact.
        }
    }
}
