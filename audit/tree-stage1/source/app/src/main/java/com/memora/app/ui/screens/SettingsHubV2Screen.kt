package com.memora.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.memora.app.BuildConfig
import com.memora.app.i18n.L
import com.memora.app.i18n.S
import com.memora.app.prefs.AppLanguage
import com.memora.app.prefs.UserSettings
import com.memora.app.ui.theme.MemoraDesign

private data class V2Setting(
    val icon: ImageVector,
    val title: String,
    val subtitle: String? = null,
    val trailingStatus: String? = null,
    val onClick: () -> Unit
)

@Composable
fun SettingsHubV2Screen(
    settings: UserSettings,
    onReminders: () -> Unit,
    onTitles: () -> Unit,
    onCalendar: () -> Unit,
    onTheme: () -> Unit,
    onLanguage: () -> Unit,
    onData: () -> Unit,
    onSecurity: () -> Unit,
    onObsidian: () -> Unit,
    onTopilmaIntegration: () -> Unit,
    onSubscription: () -> Unit,
    onAbout: () -> Unit
) {
    val l = settings.language
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = MemoraDesign.screenPadding,
            end = MemoraDesign.screenPadding,
            top = MemoraDesign.screenPadding,
            bottom = 110.dp
        )
    ) {
        item {
            Text(
                settingsTitle(l),
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 38.sp),
                fontWeight = FontWeight.SemiBold
            )
            Text(
                L.t(l, S.HOME_TAGLINE),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 20.dp)
            )
        }
        item {
            SettingsV2Section(
                title = writingSection(l),
                entries = listOf(
                    V2Setting(
                        Icons.Rounded.Notifications,
                        if (l == AppLanguage.UZ) "Eslatmalar" else "Reminders",
                        if (l == AppLanguage.UZ) "Eski yozuvlar va Kun izi" else "Memory recalls and Day Trace",
                        onClick = onReminders
                    ),
                    V2Setting(
                        Icons.Rounded.Title,
                        titlesLabel(l),
                        titlesSubtitle(l),
                        onClick = onTitles
                    ),
                )
            )
        }
        item {
            SettingsV2Section(
                title = appearanceSection(l),
                entries = listOf(
                    V2Setting(Icons.Rounded.Palette, themeLabel(l), themeStatus(settings), onClick = onTheme),
                    V2Setting(Icons.Rounded.Language, languageLabel(l), settings.language.displayName, onClick = onLanguage)
                )
            )
        }
        item {
            SettingsV2Section(
                title = dataSection(l),
                entries = listOf(
                    V2Setting(
                        Icons.Rounded.Storage,
                        dataBackupLabel(l),
                        dataBackupSubtitle(l),
                        onClick = onData
                    ),
                    V2Setting(
                        Icons.Rounded.Fingerprint,
                        securityLabel(l),
                        securitySubtitle(l),
                        trailingStatus = lockStatus(settings),
                        onClick = onSecurity
                    )
                )
            )
        }
        item {
            SettingsV2Section(
                title = if (l == AppLanguage.UZ) "Tashqi dasturlar" else "External apps",
                entries = listOf(
                    V2Setting(
                        Icons.Rounded.Hub,
                        "Obsidian",
                        if (settings.obsidianEnabled) "Memora → Obsidian ulangan" else if (settings.obsidianTreeUri != null) "O‘chirilgan" else "Ulanmagan",
                        trailingStatus = if (settings.obsidianEnabled) "ON" else null,
                        onClick = onObsidian
                    )
                )
            )
        }
        item {
            SettingsV2Section(
                title = memoraSection(l),
                entries = listOf(
                    V2Setting(
                        Icons.Rounded.WorkspacePremium,
                        subscriptionLabel(l),
                        subscriptionSubtitle(l),
                        onClick = onSubscription
                    ),
                    V2Setting(
                        Icons.Rounded.Policy,
                        privacyLabel(l),
                        privacySubtitle(l),
                        onClick = onAbout
                    ),
                    V2Setting(
                        Icons.Rounded.Info,
                        aboutLabel(l),
                        "v${BuildConfig.VERSION_NAME}",
                        onClick = onAbout
                    )
                ),
                bottom = 0.dp
            )
        }
    }
}

@Composable
private fun SettingsV2Section(title: String, entries: List<V2Setting>, bottom: Dp = 20.dp) {
    Column(Modifier.padding(bottom = bottom)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 5.dp, bottom = 8.dp)
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(MemoraDesign.radiusMedium),
            color = MaterialTheme.colorScheme.surface.copy(alpha = .985f),
            shadowElevation = MemoraDesign.shadowSubtle
        ) {
            Column {
                entries.forEachIndexed { index, entry ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(onClick = entry.onClick)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(MemoraDesign.radiusSmall),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = .085f),
                            modifier = Modifier.size(46.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    entry.icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(23.dp)
                                )
                            }
                        }
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text(
                                entry.title,
                                style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                                fontWeight = FontWeight.Medium
                            )
                            entry.subtitle?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.5.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 1.dp)
                                )
                            }
                        }
                        entry.trailingStatus?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                        Text(
                            "›",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (index != entries.lastIndex) {
                        HorizontalDivider(
                            Modifier.padding(start = 76.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .06f)
                        )
                    }
                }
            }
        }
    }
}

private fun settingsTitle(l: AppLanguage) = if (l == AppLanguage.UZ) "Sozlamalar" else if (l == AppLanguage.RU) "Настройки" else "Settings"
private fun writingSection(l: AppLanguage) = if (l == AppLanguage.UZ) "Yozish" else if (l == AppLanguage.RU) "Письмо" else "Writing"
private fun appearanceSection(l: AppLanguage) = if (l == AppLanguage.UZ) "Ko‘rinish" else if (l == AppLanguage.RU) "Оформление" else "Appearance"
private fun dataSection(l: AppLanguage) = if (l == AppLanguage.UZ) "Ma’lumotlar va maxfiylik" else if (l == AppLanguage.RU) "Данные и приватность" else "Data & privacy"
private fun memoraSection(l: AppLanguage) = "Memora"
private fun remindersLabel(l: AppLanguage) = if (l == AppLanguage.UZ) "Eslatmalar" else if (l == AppLanguage.RU) "Напоминания" else "Reminders"
private fun titlesLabel(l: AppLanguage) = if (l == AppLanguage.UZ) "Saqlangan sarlavhalar" else if (l == AppLanguage.RU) "Сохранённые заголовки" else "Saved titles"
private fun themeLabel(l: AppLanguage) = if (l == AppLanguage.UZ) "Mavzu" else if (l == AppLanguage.RU) "Тема" else "Theme"
private fun languageLabel(l: AppLanguage) = if (l == AppLanguage.UZ) "Til" else if (l == AppLanguage.RU) "Язык" else "Language"
private fun dataBackupLabel(l: AppLanguage) = if (l == AppLanguage.UZ) "Ma’lumotlar va backup" else if (l == AppLanguage.RU) "Данные и резервная копия" else "Data & backup"
private fun securityLabel(l: AppLanguage) = if (l == AppLanguage.UZ) "Xavfsizlik" else if (l == AppLanguage.RU) "Безопасность" else "Security"
private fun subscriptionLabel(l: AppLanguage) = if (l == AppLanguage.UZ) "Obuna" else if (l == AppLanguage.RU) "Подписка" else "Subscription"
private fun aboutLabel(l: AppLanguage) = if (l == AppLanguage.UZ) "Ilova haqida" else if (l == AppLanguage.RU) "О приложении" else "About"
private fun privacyLabel(l: AppLanguage) = if (l == AppLanguage.UZ) "Maxfiylik siyosati" else if (l == AppLanguage.RU) "Конфиденциальность" else "Privacy policy"
private fun remindersSubtitle(l: AppLanguage) = if (l == AppLanguage.UZ) "Yozishni eslatish va bildirishnomalar" else "Writing reminders and notifications"
private fun titlesSubtitle(l: AppLanguage) = if (l == AppLanguage.UZ) "Tezroq yozish uchun" else "For faster writing"
private fun dataBackupSubtitle(l: AppLanguage) = if (l == AppLanguage.UZ) "Export, import va tiklash" else "Export, import and restore"
private fun securitySubtitle(l: AppLanguage) = if (l == AppLanguage.UZ) "Ilovani himoyalash" else "Protect the app"
private fun subscriptionSubtitle(l: AppLanguage) = if (l == AppLanguage.UZ) "Memora’ning barcha imkoniyatlari" else "All Memora features"
private fun privacySubtitle(l: AppLanguage) = if (l == AppLanguage.UZ) "Ma’lumotlaringiz biz uchun muhim" else "Your privacy matters"
private fun themeStatus(s: UserSettings) = "${s.palette.name.lowercase().replaceFirstChar { it.uppercase() }} · ${s.themeMode.name.lowercase().replaceFirstChar { it.uppercase() }}"
private fun lockStatus(s: UserSettings) = if (s.lockEnabled) (if (s.language == AppLanguage.UZ) "Yoqilgan" else "On") else (if (s.language == AppLanguage.UZ) "O‘chiq" else "Off")
