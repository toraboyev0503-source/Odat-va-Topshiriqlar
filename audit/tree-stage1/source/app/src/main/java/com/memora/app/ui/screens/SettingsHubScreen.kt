package com.memora.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material.icons.rounded.Upload
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
import androidx.compose.ui.unit.dp
import com.memora.app.i18n.L
import com.memora.app.i18n.S
import com.memora.app.prefs.AppLanguage
import com.memora.app.ui.theme.MemoraDesign

private data class SettingsEntry(
    val icon: ImageVector,
    val text: String,
    val onClick: () -> Unit
)

private data class SettingsSectionLabels(
    val writingAndData: String,
    val experience: String,
    val accountAndSecurity: String
)

@Composable
fun SettingsHubScreen(
    language: AppLanguage,
    isDark: Boolean,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onSubscription: () -> Unit,
    onReminders: () -> Unit,
    onTitles: () -> Unit,
    onLanguage: () -> Unit,
    onStatistics: () -> Unit,
    onTheme: () -> Unit,
    onSecurity: () -> Unit,
    onToggleDark: () -> Unit
) {
    val labels = settingsSectionLabels(language)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = MemoraDesign.screenPadding,
            end = MemoraDesign.screenPadding,
            top = MemoraDesign.screenPadding,
            bottom = 116.dp
        )
    ) {
        item {
            Column(Modifier.padding(bottom = MemoraDesign.spaceXxl)) {
                Text(
                    "Memora",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    L.t(language, S.HOME_TAGLINE),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = MemoraDesign.spaceXs)
                )
            }
        }

        item {
            SettingsSection(
                title = labels.writingAndData,
                entries = listOf(
                    SettingsEntry(Icons.Rounded.Download, L.t(language, S.SAVE_NOTES), onExport),
                    SettingsEntry(Icons.Rounded.Upload, L.t(language, S.IMPORT_FILE), onImport),
                    SettingsEntry(Icons.Rounded.BarChart, L.t(language, S.STATISTICS), onStatistics)
                )
            )
        }

        item {
            SettingsSection(
                title = labels.experience,
                entries = listOf(
                    SettingsEntry(Icons.Rounded.Notifications, L.t(language, S.WRITING_TIME), onReminders),
                    SettingsEntry(Icons.Rounded.Title, L.t(language, S.TITLES), onTitles),
                    SettingsEntry(Icons.Rounded.Language, L.t(language, S.LANGUAGE), onLanguage),
                    SettingsEntry(Icons.Rounded.Palette, L.t(language, S.THEME), onTheme),
                    SettingsEntry(
                        if (isDark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                        if (isDark) L.t(language, S.LIGHT) else L.t(language, S.DARK),
                        onToggleDark
                    )
                )
            )
        }

        item {
            SettingsSection(
                title = labels.accountAndSecurity,
                entries = listOf(
                    SettingsEntry(Icons.Rounded.WorkspacePremium, L.t(language, S.SUBSCRIPTION), onSubscription),
                    SettingsEntry(Icons.Rounded.Lock, L.t(language, S.SECURITY), onSecurity)
                ),
                bottomPadding = 0.dp
            )
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    entries: List<SettingsEntry>,
    bottomPadding: androidx.compose.ui.unit.Dp = MemoraDesign.spaceXxl
) {
    Column(Modifier.padding(bottom = bottomPadding)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = MemoraDesign.spaceXs, bottom = MemoraDesign.spaceSm)
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(MemoraDesign.radiusMedium),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = MemoraDesign.shadowSubtle,
            tonalElevation = 0.dp
        ) {
            Column {
                entries.forEachIndexed { index, entry ->
                    SettingsRow(entry)
                    if (index != entries.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 58.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(entry: SettingsEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = entry.onClick)
            .padding(horizontal = MemoraDesign.spaceLg, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            entry.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            entry.text,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .weight(1f)
                .padding(start = MemoraDesign.spaceLg)
        )
        Text(
            "›",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun settingsSectionLabels(language: AppLanguage): SettingsSectionLabels = when (language) {
    AppLanguage.UZ -> SettingsSectionLabels("Yozuvlar va ma’lumotlar", "Shaxsiylashtirish", "Obuna va xavfsizlik")
    AppLanguage.RU -> SettingsSectionLabels("Записи и данные", "Персонализация", "Подписка и безопасность")
    AppLanguage.AR -> SettingsSectionLabels("الكتابة والبيانات", "التخصيص", "الاشتراك والأمان")
    AppLanguage.FR -> SettingsSectionLabels("Écrits et données", "Personnalisation", "Abonnement et sécurité")
    AppLanguage.DE -> SettingsSectionLabels("Notizen und Daten", "Personalisierung", "Abo und Sicherheit")
    AppLanguage.HI -> SettingsSectionLabels("लेखन और डेटा", "वैयक्तिकरण", "सदस्यता और सुरक्षा")
    AppLanguage.ID -> SettingsSectionLabels("Tulisan dan data", "Personalisasi", "Langganan dan keamanan")
    AppLanguage.JA -> SettingsSectionLabels("記録とデータ", "カスタマイズ", "購読とセキュリティ")
    AppLanguage.KO -> SettingsSectionLabels("기록 및 데이터", "개인 설정", "구독 및 보안")
    AppLanguage.PT_BR -> SettingsSectionLabels("Textos e dados", "Personalização", "Assinatura e segurança")
    AppLanguage.ES -> SettingsSectionLabels("Notas y datos", "Personalización", "Suscripción y seguridad")
    AppLanguage.TR -> SettingsSectionLabels("Yazılar ve veriler", "Kişiselleştirme", "Abonelik ve güvenlik")
    AppLanguage.EN -> SettingsSectionLabels("Writing and data", "Personalization", "Subscription and security")
}
