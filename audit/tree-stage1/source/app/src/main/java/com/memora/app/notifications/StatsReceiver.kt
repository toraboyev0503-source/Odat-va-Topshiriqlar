package com.memora.app.notifications

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.memora.app.MemoraApplication
import com.memora.app.R
import com.memora.app.data.local.NoteType
import com.memora.app.i18n.L
import com.memora.app.i18n.S
import com.memora.app.util.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

class StatsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val mode = intent.getStringExtra(StatsScheduler.EXTRA_MODE) ?: return pending.finish()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as MemoraApplication
                val userSettings = app.preferences.settings.first()
                val language = userSettings.language
                when (mode) {
                    StatsScheduler.MODE_MONTHLY -> {
                        if (!userSettings.monthlySummaryEnabled) {
                            StatsScheduler(context).scheduleMonthly()
                            return@launch
                        }
                        val (year, month, range) = TimeUtils.previousMonthRange()
                        val short = app.repository.countBetween(NoteType.SHORT, range.start!!, range.end!!)
                        val long = app.repository.countBetween(NoteType.LONG, range.start!!, range.end!!)
                        val period = TimeUtils.monthName(year, month, language)
                        val statsText = L.format(language, S.MONTHLY_STATS_BODY, period, short, long)
                        notify(
                            context,
                            200_001,
                            L.t(language, S.MONTHLY_STATS_TITLE),
                            statsText
                        )
                        StatsScheduler(context).scheduleMonthly()
                    }
                    StatsScheduler.MODE_YEARLY -> {
                        if (!userSettings.yearlySummaryEnabled) {
                            StatsScheduler(context).scheduleYearly()
                            return@launch
                        }
                        val year = LocalDate.now().year - 1
                        val range = TimeUtils.yearRange(year)
                        val short = app.repository.countBetween(NoteType.SHORT, range.start!!, range.end!!)
                        val long = app.repository.countBetween(NoteType.LONG, range.start!!, range.end!!)
                        notify(
                            context,
                            200_002,
                            L.t(language, S.YEARLY_STATS_TITLE),
                            L.format(language, S.YEARLY_STATS_BODY, year, short, long)
                        )
                        StatsScheduler(context).scheduleYearly()
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun notify(
        context: Context,
        id: Int,
        title: String,
        text: String
    ) {
        val builder = NotificationCompat.Builder(context, NotificationChannels.STATS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text.lineSequence().firstOrNull().orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)

        val notification = builder.build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Notification permission not granted.
        }
    }
}
