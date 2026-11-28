package com.memora.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationChannels {
    const val WRITING = "memora_writing"
    const val STATS = "memora_stats"
    const val TOPILMALAR = "memora_topilmalar_review"
    const val MEMORIES = "memora_memory_recall"
    const val ALBUM = "memora_album_prompt"

    fun create(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val writing = NotificationChannel(
            WRITING,
            "Writing reminders",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Daily writing reminders from Memora"
        }
        val stats = NotificationChannel(
            STATS,
            "Writing statistics",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Monthly and yearly Memora summaries"
        }
        val topilmalar = NotificationChannel(
            TOPILMALAR,
            "Topilmalarni qayta ko‘rish",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Daily Topilmalar review reminder"
        }
        manager.createNotificationChannels(listOf(writing, stats, topilmalar,
            NotificationChannel(MEMORIES, "Memora memories", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(ALBUM, "Memora day trace", NotificationManager.IMPORTANCE_DEFAULT)))
    }
}
