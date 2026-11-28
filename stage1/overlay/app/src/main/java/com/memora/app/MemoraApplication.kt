package com.memora.app

import android.app.Application
import com.memora.app.billing.SubscriptionManager
import com.memora.app.data.local.AppDatabase
import com.memora.app.data.repository.MemoraRepository
import com.memora.app.data.repository.TopilmalarRepository
import com.memora.app.diagnostics.LocalCrashDiagnostics
import com.memora.app.notifications.AlarmScheduler
import com.memora.app.notifications.NotificationChannels
import com.memora.app.notifications.StatsScheduler
import com.memora.app.notifications.TimeTraceScheduler
import com.memora.app.notifications.TopilmalarReviewScheduler
import com.memora.app.obsidian.ObsidianSyncManager
import com.memora.app.prefs.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

class MemoraApplication : Application() {
    lateinit var memoryTreeRepository: com.memora.app.data.repository.MemoryTreeRepository
        private set
    lateinit var repository: MemoraRepository
        private set
    lateinit var preferences: UserPreferences
        private set
    lateinit var topilmalarRepository: TopilmalarRepository
        private set
    lateinit var subscriptions: SubscriptionManager
        private set
    lateinit var obsidianSync: ObsidianSyncManager
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        LocalCrashDiagnostics.install(this)
        subscriptions = SubscriptionManager(this, appScope)
        val database = AppDatabase.get(this)
        preferences = UserPreferences(this)
        obsidianSync = ObsidianSyncManager(this, database, preferences, appScope)
        repository = MemoraRepository(database, subscriptions::canWriteNow, obsidianSync)
        memoryTreeRepository = com.memora.app.data.repository.MemoryTreeRepository(database)
        topilmalarRepository = TopilmalarRepository(database, subscriptions::canWriteNow, obsidianSync)
        obsidianSync.kickPendingOnLaunch()
        // These services are not required for the first frame. Keeping them off the main thread
        // shortens the Android system splash so the Memora artwork appears immediately.
        appScope.launch {
            NotificationChannels.create(this@MemoraApplication)
            StatsScheduler(this@MemoraApplication).scheduleAll()

            // Self-heal reminder schedules on every real app start. This covers app updates,
            // vendor battery management and cases where a previous alarm registration was lost.
            val scheduler = AlarmScheduler(this@MemoraApplication)
            repository.enabledReminders().forEach(scheduler::scheduleReminder)

            val traceSettings = preferences.settings.first()
            com.memora.app.memories.MemoryScheduler(this@MemoraApplication).refresh()
            val traceScheduler = TimeTraceScheduler(this@MemoraApplication)
            traceScheduler.cancelDaily()
            traceScheduler.cancelMilestoneCheck()

            TopilmalarReviewScheduler(this@MemoraApplication).cancelDaily()
        }
    }
}
