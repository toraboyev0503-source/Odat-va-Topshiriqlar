package com.memora.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.memora.app.MemoraApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in SUPPORTED_ACTIONS) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as MemoraApplication
                val scheduler = AlarmScheduler(context)
                app.repository.enabledReminders().forEach(scheduler::scheduleReminder)
                StatsScheduler(context).scheduleAll()
                val settings = app.preferences.settings.first()
                // The two legacy reminder mechanisms are retired; retain their data for backup.
                TimeTraceScheduler(context).cancelDaily()
                TimeTraceScheduler(context).cancelMilestoneCheck()
                TopilmalarReviewScheduler(context).cancelDaily()
                com.memora.app.memories.MemoryScheduler(context).refresh()
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val SUPPORTED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED
        )
    }
}
