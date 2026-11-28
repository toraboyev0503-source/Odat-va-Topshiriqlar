package com.memora.app.ui.screens

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.memora.app.memories.MemoryText
import com.memora.app.prefs.AppLanguage
import com.memora.app.prefs.UserPreferences
import com.memora.app.prefs.UserSettings
import kotlinx.coroutines.launch

@Composable
fun MemorySettingsScreen(
    settings: UserSettings,
    preferences: UserPreferences,
    onBack: () -> Unit,
    onNotificationPermission: () -> Unit,
    onExactPermission: () -> Unit
) {
    val language = settings.language
    val uz = language == AppLanguage.UZ
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = null) }
            Text(MemoryText.get(language, MemoryText.Key.NOTIFY), style = MaterialTheme.typography.headlineSmall)
        }
        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.History, contentDescription = null)
                    Text(MemoryText.get(language, MemoryText.Key.MEMORY), style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f).padding(start = 12.dp))
                    Switch(checked = settings.memoryEnabled, onCheckedChange = {
                        scope.launch { preferences.setMemoryEnabled(it) }
                        if (it) onNotificationPermission()
                    })
                }
                Text(if (uz) "Kamida 7 kun oldin yozilgan qaydlar, imkon qadar o‘sha yozilgan soatda eslatiladi. Bir yozuv 30 kun ichida takrorlanmaydi."
                    else "Notes older than seven days can return at their original writing time. Recent repeats are avoided.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
                Text(if (uz) "Kuniga" else "Per day", style = MaterialTheme.typography.labelLarge)
                ReminderFrequency(selected = settings.memoryDailyCount,
                    onSelect = { scope.launch { preferences.setMemoryDailyCount(it) } })
            }
        }
        Spacer(Modifier.height(18.dp))
        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.PhotoCamera, null)
                    Text(MemoryText.get(language, MemoryText.Key.ALBUM), style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f).padding(start = 12.dp))
                    Switch(checked = settings.albumReminderEnabled, onCheckedChange = {
                        scope.launch { preferences.setAlbumReminderEnabled(it) }
                        if (it) onNotificationPermission()
                    })
                }
                Text(if (uz) "Suratga olib, bugundan xotira qoldirish uchun eslatmalar."
                    else "Gentle prompts to photograph and caption your day.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Text(if (uz) "Kuniga" else "Per day", style = MaterialTheme.typography.labelLarge)
                ReminderFrequency(selected = settings.albumReminderDailyCount,
                    onSelect = { scope.launch { preferences.setAlbumReminderDailyCount(it) } })
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (uz) "Tasodifiy vaqtlarda" else "Random times", Modifier.weight(1f))
                    Switch(checked = settings.albumReminderRandom,
                        onCheckedChange = { scope.launch { preferences.setAlbumReminderRandom(it) } })
                }
                Text(if (uz) "Tasodifiy vaqtlar: 08:00–21:00; 3 ta eslatmada kamida 3 soat, 5 ta eslatmada kamida 2 soat 15 daqiqa."
                    else "Random reminders are spread apart by 2¼–3 hours.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                if (!settings.albumReminderRandom) {
                    OutlinedButton(onClick = {
                        TimePickerDialog(context, { _, h, m ->
                            scope.launch { preferences.setAlbumReminderFirstTime(h, m) }
                        }, settings.albumReminderHour, settings.albumReminderMinute, true).show()
                    }) {
                        Text("${settings.albumReminderHour.toString().padStart(2, '0')}:${settings.albumReminderMinute.toString().padStart(2, '0')}")
                    }
                }
            }
        }
        Text(if (uz) "Bildirishnomalar Android ruxsatlari va batareya rejimi sabab biroz kechikishi mumkin."
            else "Android battery settings can delay notification delivery.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 16.dp))
        OutlinedButton(onClick = onExactPermission) {
            Text(if (uz) "Aniq vaqt ruxsatini tekshirish" else "Check exact reminder permission")
        }
        Spacer(Modifier.height(70.dp))
    }
}

@Composable
private fun ReminderFrequency(selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(1, 3, 5).forEach { count ->
            FilterChip(selected = selected == count,
                onClick = { onSelect(count) },
                label = { Text("$count") },
                modifier = Modifier.weight(1f))
        }
    }
}
