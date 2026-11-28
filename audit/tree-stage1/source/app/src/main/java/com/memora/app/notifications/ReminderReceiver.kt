package com.memora.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.memora.app.MemoraApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val id = intent.getLongExtra(AlarmScheduler.EXTRA_REMINDER_ID, -1L)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as MemoraApplication
                val reminder = app.repository.reminderById(id)
                val language = app.preferences.settings.first().language
                ReminderNotifier.deliver(context, language, id)
                if (reminder != null && reminder.enabled) {
                    AlarmScheduler(context).scheduleReminder(reminder)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
