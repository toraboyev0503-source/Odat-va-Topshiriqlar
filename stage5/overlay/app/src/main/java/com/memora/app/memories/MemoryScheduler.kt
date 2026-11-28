package com.memora.app.memories

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.memora.app.MemoraApplication
import com.memora.app.data.local.MemoryRecallEventEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/** Schedules only local notifications; exact delivery is best-effort without exact-alarm access. */
class MemoryScheduler(private val context: Context) {
    private val manager = context.getSystemService(AlarmManager::class.java)

    fun refreshAsync() {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { refresh() }
    }

    suspend fun refresh() {
        val app = context.applicationContext as MemoraApplication
        val settings = app.preferences.settings.first()
        val repository = app.repository
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val futureTime = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() + 60_000
        alarm("NEXT_DAY", futureTime, "", 9000)
        val tree=app.memoryTreeRepository
        tree.pruneOperational(today.minusDays(120).toString())

        if (settings.memoryEnabled) {
            tree.prepareDay(today,zone,settings.memoryDailyCount).forEach { event ->
                if(event.scheduledAt>now) alarm("MEMORY",event.scheduledAt,event.id,event.id.hashCode())
                else tree.deliverPlanned(event.id,now,settings.memoryDailyCount,zone)
            }
        }

        if (settings.albumReminderEnabled) {
            MemoryRecallPlanner.albumTimes(today, zone, settings.albumReminderDailyCount,
                settings.albumReminderRandom, settings.albumReminderHour, settings.albumReminderMinute)
                .forEachIndexed { index, time ->
                    if (time > now) alarm("ALBUM", time, "${today}_$index", 400_000 + index)
                }
        }
    }

    fun snoozeRecall(id: String, time: Long) { alarm("MEMORY", time, id, id.hashCode()) }
    fun snoozeAlbum(id: String, time: Long) { alarm("ALBUM", time, id, id.hashCode()) }

    private fun alarm(kind: String, at: Long, item: String, code: Int) {
        val pending = PendingIntent.getBroadcast(
            context, code,
            Intent(context, MemoryNotificationReceiver::class.java).apply {
                action = "com.memora.app.memories.$kind"
                data = android.net.Uri.parse("memora://alarm/$kind/"+android.net.Uri.encode(item))
                putExtra("item", item)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !manager.canScheduleExactAlarms()) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        } else manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
    }
}
