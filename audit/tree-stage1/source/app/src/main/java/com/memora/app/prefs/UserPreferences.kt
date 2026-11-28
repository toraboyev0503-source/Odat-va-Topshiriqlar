package com.memora.app.prefs

import android.content.Context
import android.os.LocaleList
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.util.Locale
import com.memora.app.notifications.TimeTraceScheduler
import com.memora.app.notifications.TopilmalarReviewScheduler

private val Context.memoraDataStore by preferencesDataStore("memora_preferences")

enum class AppLanguage(val tag: String, val displayName: String, val rtl: Boolean = false) {
    AR("ar", "العربية", true),
    EN("en", "English"),
    FR("fr", "Français"),
    DE("de", "Deutsch"),
    HI("hi", "हिन्दी"),
    ID("id", "Bahasa Indonesia"),
    JA("ja", "日本語"),
    KO("ko", "한국어"),
    PT_BR("pt-BR", "Português (Brasil)"),
    RU("ru", "Русский"),
    ES("es", "Español"),
    TR("tr", "Türkçe"),
    UZ("uz", "O‘zbekcha");

    companion object {
        fun deviceDefault(): AppLanguage {
            val locales = LocaleList.getDefault()
            return fromLanguageTags((0 until locales.size()).map { locales[it].toLanguageTag() })
        }

        fun fromLanguageTags(tags: List<String>): AppLanguage {
            tags.forEach { tag ->
                val locale = Locale.forLanguageTag(tag)
                if (locale.language.equals("pt", ignoreCase = true) &&
                    locale.country.equals("BR", ignoreCase = true)
                ) return PT_BR
                entries.firstOrNull {
                    Locale.forLanguageTag(it.tag).language.equals(locale.language, ignoreCase = true)
                }?.let { return it }
            }
            return EN
        }
    }
}

enum class ThemeMode { LIGHT, DARK, SYSTEM }
enum class PaletteChoice { PAPER, IVORY, MIST, SAGE, SLATE }
enum class FontChoice { SANS, SERIF, MONO, CURSIVE }
enum class LockInterval { IMMEDIATELY, ONE_MINUTE, FIVE_MINUTES, FIFTEEN_MINUTES, THIRTY_MINUTES }
enum class TimeTraceSpeed { SLOW, MEDIUM, FAST }

data class UserSettings(
    val language: AppLanguage = AppLanguage.EN,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val palette: PaletteChoice = PaletteChoice.PAPER,
    val uiFont: FontChoice = FontChoice.SANS,
    val writingFont: FontChoice = FontChoice.SERIF,
    val uiScale: Float = 1.0f,
    val lockEnabled: Boolean = true,
    val lockInterval: LockInterval = LockInterval.IMMEDIATELY,
    val monthlySummaryEnabled: Boolean = true,
    val yearlySummaryEnabled: Boolean = true,
    val timeTraceReminderEnabled: Boolean = false,
    val timeTraceReminderHour: Int = 20,
    val timeTraceReminderMinute: Int = 0,
    val timeTraceMilestonesEnabled: Boolean = true,
    val timeTraceSpeed: TimeTraceSpeed = TimeTraceSpeed.MEDIUM,
    val timeTraceDateOverlay: Boolean = true,
    val timeTraceIncludeInBackup: Boolean = true,
    val topilmalarDailyCount: Int = 5,
    val topilmalarReminderEnabled: Boolean = false,
    val topilmalarReminderHour: Int = 20,
    val topilmalarReminderMinute: Int = 0,
    val memoryEnabled: Boolean = false,
    val memoryDailyCount: Int = 3,
    val albumReminderEnabled: Boolean = false,
    val albumReminderDailyCount: Int = 3,
    val albumReminderRandom: Boolean = true,
    val albumReminderHour: Int = 9,
    val albumReminderMinute: Int = 0,
    val obsidianEnabled: Boolean = false,
    val obsidianTreeUri: String? = null,
    val obsidianExportExisting: Boolean = true
)

class UserPreferences(private val context: Context) {
    private object Keys {
        val language = stringPreferencesKey("language")
        val themeMode = stringPreferencesKey("theme_mode")
        val palette = stringPreferencesKey("palette")
        val uiFont = stringPreferencesKey("ui_font")
        val writingFont = stringPreferencesKey("writing_font")
        val uiScale = floatPreferencesKey("ui_scale")
        val lockEnabled = booleanPreferencesKey("lock_enabled")
        val lockInterval = stringPreferencesKey("lock_interval")
        val monthlySummaryEnabled = booleanPreferencesKey("monthly_summary_enabled")
        val yearlySummaryEnabled = booleanPreferencesKey("yearly_summary_enabled")
        val timeTraceReminderEnabled = booleanPreferencesKey("time_trace_reminder_enabled")
        val timeTraceReminderHour = intPreferencesKey("time_trace_reminder_hour")
        val timeTraceReminderMinute = intPreferencesKey("time_trace_reminder_minute")
        val timeTraceMilestonesEnabled = booleanPreferencesKey("time_trace_milestones_enabled")
        val timeTraceSpeed = stringPreferencesKey("time_trace_speed")
        val timeTraceDateOverlay = booleanPreferencesKey("time_trace_date_overlay")
        val timeTraceIncludeInBackup = booleanPreferencesKey("time_trace_include_in_backup")
        val topilmalarDailyCount = intPreferencesKey("topilmalar_daily_count")
        val topilmalarReminderEnabled = booleanPreferencesKey("topilmalar_reminder_enabled")
        val topilmalarReminderHour = intPreferencesKey("topilmalar_reminder_hour")
        val topilmalarReminderMinute = intPreferencesKey("topilmalar_reminder_minute")
        val memoryEnabled = booleanPreferencesKey("memory_enabled")
        val memoryDailyCount = intPreferencesKey("memory_daily_count")
        val albumReminderEnabled = booleanPreferencesKey("album_reminder_enabled")
        val albumReminderDailyCount = intPreferencesKey("album_reminder_daily_count")
        val albumReminderRandom = booleanPreferencesKey("album_reminder_random")
        val albumReminderHour = intPreferencesKey("album_reminder_hour")
        val albumReminderMinute = intPreferencesKey("album_reminder_minute")
        val obsidianEnabled = booleanPreferencesKey("obsidian_enabled")
        val obsidianTreeUri = stringPreferencesKey("obsidian_tree_uri")
        val obsidianExportExisting = booleanPreferencesKey("obsidian_export_existing")
    }

    val settings: Flow<UserSettings> = context.memoraDataStore.data
        .catch { throwable ->
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map { p ->
        UserSettings(
            language = p.enumOr(Keys.language, AppLanguage.deviceDefault()),
            themeMode = p.enumOr(Keys.themeMode, ThemeMode.SYSTEM),
            palette = p.enumOr(Keys.palette, PaletteChoice.PAPER),
            uiFont = p.enumOr(Keys.uiFont, FontChoice.SANS),
            writingFont = p.enumOr(Keys.writingFont, FontChoice.SERIF),
            uiScale = (p[Keys.uiScale] ?: 1.0f).coerceIn(0.8f, 1.5f),
            lockEnabled = p[Keys.lockEnabled] ?: true,
            lockInterval = p.enumOr(Keys.lockInterval, LockInterval.IMMEDIATELY),
            monthlySummaryEnabled = p[Keys.monthlySummaryEnabled] ?: true,
            yearlySummaryEnabled = p[Keys.yearlySummaryEnabled] ?: true,
            timeTraceReminderEnabled = p[Keys.timeTraceReminderEnabled] ?: false,
            timeTraceReminderHour = (p[Keys.timeTraceReminderHour] ?: 20).coerceIn(0, 23),
            timeTraceReminderMinute = (p[Keys.timeTraceReminderMinute] ?: 0).coerceIn(0, 59),
            timeTraceMilestonesEnabled = p[Keys.timeTraceMilestonesEnabled] ?: true,
            timeTraceSpeed = p.enumOr(Keys.timeTraceSpeed, TimeTraceSpeed.MEDIUM),
            timeTraceDateOverlay = p[Keys.timeTraceDateOverlay] ?: true,
            timeTraceIncludeInBackup = p[Keys.timeTraceIncludeInBackup] ?: true,
            topilmalarDailyCount = (p[Keys.topilmalarDailyCount] ?: 5).takeIf { it in setOf(3, 5, 10, 15, 20) } ?: 5,
            topilmalarReminderEnabled = p[Keys.topilmalarReminderEnabled] ?: false,
            topilmalarReminderHour = (p[Keys.topilmalarReminderHour] ?: 20).coerceIn(0, 23),
            topilmalarReminderMinute = (p[Keys.topilmalarReminderMinute] ?: 0).coerceIn(0, 59),
            memoryEnabled = p[Keys.memoryEnabled] ?: false,
            memoryDailyCount = (p[Keys.memoryDailyCount] ?: 3).takeIf { it in setOf(1, 3, 5) } ?: 3,
            albumReminderEnabled = p[Keys.albumReminderEnabled] ?: false,
            albumReminderDailyCount = (p[Keys.albumReminderDailyCount] ?: 3).takeIf { it in setOf(1, 3, 5) } ?: 3,
            albumReminderRandom = p[Keys.albumReminderRandom] ?: true,
            albumReminderHour = (p[Keys.albumReminderHour] ?: 9).coerceIn(0, 23),
            albumReminderMinute = (p[Keys.albumReminderMinute] ?: 0).coerceIn(0, 59),
            obsidianEnabled = p[Keys.obsidianEnabled] ?: false,
            obsidianTreeUri = p[Keys.obsidianTreeUri],
            obsidianExportExisting = p[Keys.obsidianExportExisting] ?: true
        )
        }

    suspend fun setLanguage(value: AppLanguage) = edit(Keys.language, value.name)
    suspend fun setThemeMode(value: ThemeMode) = edit(Keys.themeMode, value.name)
    suspend fun setPalette(value: PaletteChoice) = edit(Keys.palette, value.name)
    suspend fun setUiFont(value: FontChoice) = edit(Keys.uiFont, value.name)
    suspend fun setWritingFont(value: FontChoice) = edit(Keys.writingFont, value.name)
    suspend fun setUiScale(value: Float) {
        context.memoraDataStore.edit { it[Keys.uiScale] = value.coerceIn(0.8f, 1.5f) }
    }
    suspend fun setLockEnabled(value: Boolean) {
        context.memoraDataStore.edit { it[Keys.lockEnabled] = value }
    }
    suspend fun setLockInterval(value: LockInterval) = edit(Keys.lockInterval, value.name)
    suspend fun setMonthlySummaryEnabled(value: Boolean) { context.memoraDataStore.edit { it[Keys.monthlySummaryEnabled] = value } }
    suspend fun setYearlySummaryEnabled(value: Boolean) { context.memoraDataStore.edit { it[Keys.yearlySummaryEnabled] = value } }
    suspend fun setTimeTraceReminderEnabled(value: Boolean) {
        context.memoraDataStore.edit { it[Keys.timeTraceReminderEnabled] = value }
        val current = settings.first()
        val scheduler = TimeTraceScheduler(context)
        if (value) scheduler.scheduleDaily(current.timeTraceReminderHour, current.timeTraceReminderMinute) else scheduler.cancelDaily()
    }
    suspend fun setTimeTraceReminderTime(hour: Int, minute: Int) {
        val safeHour = hour.coerceIn(0, 23)
        val safeMinute = minute.coerceIn(0, 59)
        context.memoraDataStore.edit {
            it[Keys.timeTraceReminderHour] = safeHour
            it[Keys.timeTraceReminderMinute] = safeMinute
        }
        if (settings.first().timeTraceReminderEnabled) TimeTraceScheduler(context).scheduleDaily(safeHour, safeMinute)
    }
    suspend fun setTimeTraceMilestonesEnabled(value: Boolean) {
        context.memoraDataStore.edit { it[Keys.timeTraceMilestonesEnabled] = value }
        val scheduler = TimeTraceScheduler(context)
        if (value) scheduler.scheduleMilestoneCheck() else scheduler.cancelMilestoneCheck()
    }
    suspend fun setTimeTraceSpeed(value: TimeTraceSpeed) = edit(Keys.timeTraceSpeed, value.name)
    suspend fun setTimeTraceDateOverlay(value: Boolean) { context.memoraDataStore.edit { it[Keys.timeTraceDateOverlay] = value } }
    suspend fun setTimeTraceIncludeInBackup(value: Boolean) { context.memoraDataStore.edit { it[Keys.timeTraceIncludeInBackup] = value } }
    suspend fun setTopilmalarDailyCount(value: Int) {
        require(value in setOf(3, 5, 10, 15, 20))
        context.memoraDataStore.edit { it[Keys.topilmalarDailyCount] = value }
    }
    suspend fun setTopilmalarReminderEnabled(value: Boolean) {
        context.memoraDataStore.edit { it[Keys.topilmalarReminderEnabled] = value }
        val current = settings.first()
        val scheduler = TopilmalarReviewScheduler(context)
        if (value) scheduler.scheduleDaily(current.topilmalarReminderHour, current.topilmalarReminderMinute)
        else scheduler.cancelDaily()
    }
    suspend fun setTopilmalarReminderTime(hour: Int, minute: Int) {
        val safeHour = hour.coerceIn(0, 23)
        val safeMinute = minute.coerceIn(0, 59)
        context.memoraDataStore.edit {
            it[Keys.topilmalarReminderHour] = safeHour
            it[Keys.topilmalarReminderMinute] = safeMinute
        }
        if (settings.first().topilmalarReminderEnabled) {
            TopilmalarReviewScheduler(context).scheduleDaily(safeHour, safeMinute)
        }
    }

    private fun refreshMemoryReminders() = com.memora.app.memories.MemoryScheduler(context).refreshAsync()

    suspend fun setMemoryEnabled(value: Boolean) {
        context.memoraDataStore.edit { it[Keys.memoryEnabled] = value }
        refreshMemoryReminders()
    }
    suspend fun setMemoryDailyCount(value: Int) {
        require(value in setOf(1, 3, 5))
        context.memoraDataStore.edit { it[Keys.memoryDailyCount] = value }
        refreshMemoryReminders()
    }
    suspend fun setAlbumReminderEnabled(value: Boolean) {
        context.memoraDataStore.edit { it[Keys.albumReminderEnabled] = value }
        refreshMemoryReminders()
    }
    suspend fun setAlbumReminderDailyCount(value: Int) {
        require(value in setOf(1, 3, 5))
        context.memoraDataStore.edit { it[Keys.albumReminderDailyCount] = value }
        refreshMemoryReminders()
    }
    suspend fun setAlbumReminderRandom(value: Boolean) {
        context.memoraDataStore.edit { it[Keys.albumReminderRandom] = value }
        refreshMemoryReminders()
    }
    suspend fun setAlbumReminderFirstTime(hour: Int, minute: Int) {
        context.memoraDataStore.edit {
            it[Keys.albumReminderHour] = hour.coerceIn(0, 23)
            it[Keys.albumReminderMinute] = minute.coerceIn(0, 59)
        }
        refreshMemoryReminders()
    }

    suspend fun setObsidianEnabled(value: Boolean) {
        context.memoraDataStore.edit { it[Keys.obsidianEnabled] = value }
    }

    suspend fun setObsidianTreeUri(value: String?) {
        context.memoraDataStore.edit { prefs ->
            if (value.isNullOrBlank()) prefs.remove(Keys.obsidianTreeUri) else prefs[Keys.obsidianTreeUri] = value
        }
    }

    suspend fun setObsidianExportExisting(value: Boolean) {
        context.memoraDataStore.edit { it[Keys.obsidianExportExisting] = value }
    }

    private suspend fun edit(key: Preferences.Key<String>, value: String) {
        context.memoraDataStore.edit { it[key] = value }
    }

    private inline fun <reified E : Enum<E>> Preferences.enumOr(
        key: Preferences.Key<String>,
        default: E
    ): E {
        val raw = this[key] ?: return default
        return enumValues<E>().firstOrNull { it.name == raw } ?: default
    }
}
