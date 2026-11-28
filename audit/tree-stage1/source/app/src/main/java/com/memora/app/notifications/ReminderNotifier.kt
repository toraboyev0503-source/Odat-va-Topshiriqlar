package com.memora.app.notifications

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.memora.app.MainActivity
import com.memora.app.R
import com.memora.app.i18n.L
import com.memora.app.i18n.S
import com.memora.app.prefs.AppLanguage
import java.time.LocalDate

object ReminderNotifier {
    private const val PREFS = "memora_reminder_delivery"

    @SuppressLint("MissingPermission")
    fun deliver(context: Context, language: AppLanguage, reminderId: Long): Boolean {
        val today = LocalDate.now().toString()
        val key = "${reminderId}_$today"
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(key, false)) return false

        val notificationId = (100_000 + (reminderId % 50_000)).toInt()
        val shortIntent = openEditorIntent(context, "SHORT", 30_000 + reminderId.toInt(), notificationId)
        val longIntent = openEditorIntent(context, "LONG", 40_000 + reminderId.toInt(), notificationId)

        val notification = NotificationCompat.Builder(context, NotificationChannels.WRITING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(L.t(language, S.WRITING_TIME_NOTIFICATION))
            .setContentText("Memora")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(0, L.t(language, S.SHORT_NOTES), shortIntent)
            .addAction(0, L.t(language, S.LONG_NOTES), longIntent)
            .setContentIntent(shortIntent)
            .build()

        return try {
            val manager = NotificationManagerCompat.from(context)
            if (!manager.areNotificationsEnabled()) return false
            manager.notify(notificationId, notification)
            prefs.edit().putBoolean(key, true).apply()
            cleanupOldKeys(prefs, today)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private fun openEditorIntent(
        context: Context,
        type: String,
        requestCode: Int,
        notificationId: Int
    ): PendingIntent = PendingIntent.getActivity(
        context,
        requestCode,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_OPEN_EDITOR, type)
            putExtra(MainActivity.EXTRA_NOTIFICATION_ID, notificationId)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun cleanupOldKeys(
        prefs: android.content.SharedPreferences,
        today: String
    ) {
        val editor = prefs.edit()
        prefs.all.keys.filter { !it.endsWith(today) }.take(100).forEach(editor::remove)
        editor.apply()
    }
}
