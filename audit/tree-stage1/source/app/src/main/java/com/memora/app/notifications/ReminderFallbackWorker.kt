package com.memora.app.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.memora.app.MemoraApplication
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class ReminderFallbackWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_REMINDER_ID, -1L)
        if (id < 0L) return Result.success()
        val app = applicationContext as MemoraApplication
        val reminder = app.repository.reminderById(id) ?: return Result.success()
        if (!reminder.enabled) return Result.success()

        val language = app.preferences.settings.first().language
        ReminderNotifier.deliver(applicationContext, language, id)
        ReminderFallbackScheduler.scheduleNext(applicationContext, reminder, cancelExisting = false)
        return Result.success()
    }

    companion object {
        const val KEY_REMINDER_ID = "reminder_id"
    }
}

object ReminderFallbackScheduler {
    fun schedule(context: Context, reminder: com.memora.app.data.local.ReminderEntity) {
        val tag = tag(reminder.id)
        WorkManager.getInstance(context).cancelAllWorkByTag(tag)
        if (reminder.enabled) scheduleNext(context, reminder, cancelExisting = false)
    }

    fun cancel(context: Context, id: Long) {
        WorkManager.getInstance(context).cancelAllWorkByTag(tag(id))
    }

    fun scheduleNext(
        context: Context,
        reminder: com.memora.app.data.local.ReminderEntity,
        cancelExisting: Boolean
    ) {
        if (!reminder.enabled) return
        val now = LocalDateTime.now()
        var next = now.withHour(reminder.hour).withMinute(reminder.minute).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delayMillis = Duration.between(now, next).toMillis().coerceAtLeast(1_000L)
        val workName = "memora_reminder_${reminder.id}_${next.toLocalDate()}"
        val request = OneTimeWorkRequestBuilder<ReminderFallbackWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(Data.Builder().putLong(ReminderFallbackWorker.KEY_REMINDER_ID, reminder.id).build())
            .addTag(tag(reminder.id))
            .build()
        val manager = WorkManager.getInstance(context)
        if (cancelExisting) manager.cancelAllWorkByTag(tag(reminder.id))
        manager.enqueueUniqueWork(workName, ExistingWorkPolicy.REPLACE, request)
    }

    private fun tag(id: Long): String = "memora_reminder_$id"
}
