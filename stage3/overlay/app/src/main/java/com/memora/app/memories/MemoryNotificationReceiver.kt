package com.memora.app.memories

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.memora.app.MainActivity
import com.memora.app.MemoraApplication
import com.memora.app.R
import com.memora.app.notifications.NotificationChannels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MemoryNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { process(context.applicationContext, intent) } finally { pending.finish() }
        }
    }

    private suspend fun process(context: Context, intent: Intent) {
        val app = context as MemoraApplication
        val settings = app.preferences.settings.first()
        val item = intent.getStringExtra("item") ?: ""
        val scheduler = MemoryScheduler(context)
        val channel = when (intent.action?.substringAfterLast('.')) {
            "NEXT_DAY" -> { scheduler.refresh(); return }
            "MEMORY" -> "MEMORY"
            "ALBUM" -> "ALBUM"
            "SNOOZE_MEMORY" -> {
                // Dismiss the notification only. A pending leaf must not disappear or be re-planned.
                NotificationManagerCompat.from(context).cancel(item.hashCode())
                return
            }
            "DISMISS_MEMORY" -> {
                // Closing a notification is not reading its memory.
                NotificationManagerCompat.from(context).cancel(item.hashCode())
                return
            }
            "SNOOZE_ALBUM" -> {
                val at = System.currentTimeMillis() + 3_600_000
                scheduler.snoozeAlbum(item, at)
                NotificationManagerCompat.from(context).cancel(item.hashCode())
                return
            }
            "DISMISS_ALBUM" -> {
                NotificationManagerCompat.from(context).cancel(item.hashCode())
                return
            }
            else -> return
        }
        if (channel == "MEMORY" && !settings.memoryEnabled) return
        if (channel == "ALBUM" && !settings.albumReminderEnabled) return
        if (channel == "ALBUM") {
            // Once the user has recorded a moment today, further prompts are unnecessary.
            val today = com.memora.app.timetrace.TimeTraceStorage.todayKey()
            if (app.repository.allMemoryAlbum().any {
                com.memora.app.timetrace.TimeTraceStorage.dateKey(it.capturedAt) == today
            }) return
        }
        if (channel == "MEMORY") {
            val event=app.memoryTreeRepository.deliverPlanned(item,System.currentTimeMillis()) ?: return
            // Avoid leaking original diary content in a notification; opening uses the exact occurrence.
            val uz=settings.language==com.memora.app.prefs.AppLanguage.UZ
            post(context,item.hashCode(),NotificationChannels.MEMORIES,
                if(uz) "Xotira daraxti" else "Memory tree",
                if(uz) "Bir xotira sizni kutmoqda." else "A memory is waiting.", "",
                activity(context,item,MainActivity.EXTRA_OPEN_NOTE,"tree-event:"+event.id),
                action(context,"DISMISS_MEMORY",item,10),
                action(context,"SNOOZE_MEMORY",item,11),settings.language,true)
        } else {
            post(context, item.hashCode(), NotificationChannels.ALBUM,
                MemoryText.get(settings.language, MemoryText.Key.PHOTO_REMINDER),
                MemoryText.albumPrompt(settings.language), "",
                activity(context, item, MainActivity.EXTRA_OPEN_ALBUM_CAMERA, "true"),
                action(context, "DISMISS_ALBUM", item, 20),
                action(context, "SNOOZE_ALBUM", item, 21),
                settings.language, false)
        }
    }

    private fun activity(context: Context, item: String, extra: String, value: String): PendingIntent =
        PendingIntent.getActivity(context, item.hashCode() + 1000,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                data = android.net.Uri.parse("memora://notification/"+android.net.Uri.encode(item))
                putExtra(extra, value)
                putExtra(MainActivity.EXTRA_NOTIFICATION_ID, item.hashCode())
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun action(context: Context, verb: String, item: String, salt: Int): PendingIntent =
        PendingIntent.getBroadcast(context, item.hashCode() + salt,
            Intent(context, MemoryNotificationReceiver::class.java).apply {
                action = "com.memora.app.memories.$verb"
                data = android.net.Uri.parse("memora://notification-action/$verb/"+android.net.Uri.encode(item))
                putExtra("item", item)
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    @Suppress("MissingPermission")
    private fun post(context: Context, id: Int, channel: String, title: String, body: String,
        metadata: String, open: PendingIntent, dismiss: PendingIntent, later: PendingIntent,
        language: com.memora.app.prefs.AppLanguage, isMemory: Boolean) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setSubText(metadata)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body + if (metadata.isBlank()) "" else "\n$metadata"))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(open)
            .apply {
                if(!isMemory) {
                    addAction(0, MemoryText.get(language, MemoryText.Key.SKIP), dismiss)
                    addAction(0, MemoryText.get(language, MemoryText.Key.LATER), later)
                }
            }
            .addAction(0, MemoryText.get(language,
                if (isMemory) MemoryText.Key.READ else MemoryText.Key.START), open)
        manager.notify(id, builder.build())
    }
}
