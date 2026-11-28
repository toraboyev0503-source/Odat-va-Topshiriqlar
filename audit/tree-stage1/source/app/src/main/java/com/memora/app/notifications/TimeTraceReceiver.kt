package com.memora.app.notifications

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.memora.app.MainActivity
import com.memora.app.MemoraApplication
import com.memora.app.R
import com.memora.app.prefs.AppLanguage
import com.memora.app.timetrace.TimeTraceStorage
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TimeTraceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as MemoraApplication
                val settings = app.preferences.settings.first()
                when (intent.getStringExtra(TimeTraceScheduler.EXTRA_MODE)) {
                    TimeTraceScheduler.MODE_DAILY -> {
                        if (settings.timeTraceReminderEnabled) {
                            val today = TimeTraceStorage.todayKey()
                            if (app.repository.timeTracePhoto(today) == null) {
                                notify(
                                    context,
                                    210_001,
                                    title = if (settings.language == AppLanguage.UZ) "Vaqt izi" else "Time Trace",
                                    text = if (settings.language == AppLanguage.UZ) "Bugungi suratni xotiraga qo‘shishni xohlaysizmi?" else "Would you like to add today’s photo?"
                                )
                            }
                            TimeTraceScheduler(context).scheduleDaily(settings.timeTraceReminderHour, settings.timeTraceReminderMinute)
                        }
                    }
                    TimeTraceScheduler.MODE_MILESTONE -> {
                        if (settings.timeTraceMilestonesEnabled) {
                            val photos = app.repository.allTimeTracePhotos().sortedBy { it.dateKey }
                            val first = photos.firstOrNull()?.let { runCatching { TimeTraceStorage.parseDate(it.dateKey) }.getOrNull() }
                            if (first != null) milestoneIfDue(context, settings.language, first)
                            TimeTraceScheduler(context).scheduleMilestoneCheck()
                        }
                    }
                }
            } finally { pending.finish() }
        }
    }

    private fun milestoneIfDue(context: Context, language: AppLanguage, first: LocalDate) {
        val today = LocalDate.now()
        val months = ChronoUnit.MONTHS.between(YearMonth.from(first), YearMonth.from(today)).toInt()
        if (months < 1) return
        val anniversaryDay = minOf(first.dayOfMonth, YearMonth.from(today).lengthOfMonth())
        if (today.dayOfMonth != anniversaryDay) return

        val period = when {
            months % 12 == 0 -> 12
            months % 6 == 0 -> 6
            months % 3 == 0 -> 3
            else -> 1
        }
        val key = "${today.year}-${today.monthValue}-$period"
        val prefs = context.getSharedPreferences("time_trace_notifications", Context.MODE_PRIVATE)
        if (prefs.getString("last_milestone", null) == key) return
        prefs.edit().putString("last_milestone", key).apply()

        val text = if (language == AppLanguage.UZ) {
            if (period == 1) "Oxirgi oy davomida saqlangan suratlaringizni ko‘rasizmi?"
            else "$period oy davomida saqlangan suratlaringizni ko‘rasizmi?"
        } else {
            if (period == 1) "Would you like to view the photos you saved over the last month?"
            else "Would you like to view the photos you saved over the last $period months?"
        }
        notify(context, 210_010 + period, if (language == AppLanguage.UZ) "Vaqt izi" else "Time Trace", text)
    }

    @SuppressLint("MissingPermission")
    private fun notify(context: Context, id: Int, title: String, text: String) {
        val open = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(MainActivity.EXTRA_OPEN_TIME_TRACE, true)
                putExtra(MainActivity.EXTRA_NOTIFICATION_ID, id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, NotificationChannels.STATS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try { NotificationManagerCompat.from(context).notify(id, n) } catch (_: SecurityException) { }
    }
}
