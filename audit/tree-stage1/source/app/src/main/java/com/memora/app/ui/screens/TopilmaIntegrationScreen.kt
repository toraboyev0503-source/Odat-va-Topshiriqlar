package com.memora.app.ui.screens

import com.memora.app.ui.components.MemoraDialog as AlertDialog
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.memora.app.prefs.TopilmaIntegrationPreferences
import com.memora.app.prefs.AppLanguage
import com.memora.app.prefs.UserSettings
import com.memora.app.MemoraApplication
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memora.app.ui.theme.MemoraDesign

data class LaunchableApp(val packageName: String, val label: String, val icon: ImageBitmap)

@Composable
fun TopilmaIntegrationScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { TopilmaIntegrationPreferences(context) }
    val settings by remember(context) { (context.applicationContext as MemoraApplication).preferences.settings }
        .collectAsStateWithLifecycle(initialValue = UserSettings())
    val l = settings.language
    fun msg(uz: String, ru: String, en: String) = when (l) {
        AppLanguage.UZ -> uz
        AppLanguage.RU -> ru
        else -> en
    }
    var revision by remember { mutableStateOf(0) }
    var pickerOpen by remember { mutableStateOf(false) }
    val enabled = remember(revision) { prefs.enabledPackages() }
    val apps = remember(revision) { launchableApps(context) }
    val enabledApps = remember(apps, enabled) { apps.filter { it.packageName in enabled } }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = msg("Orqaga", "Назад", "Back")) }
            Column(Modifier.weight(1f)) {
                Text(msg("Topilmalar integratsiyasi", "Интеграции находок", "Topilmalar integrations"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(msg("Boshqa dasturlardan Topilmaga tez saqlash", "Быстрое сохранение из других приложений", "Quick save from other apps"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(onClick = { pickerOpen = true }, shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = .11f), modifier = Modifier.size(42.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Add, contentDescription = msg("Dastur qo‘shish", "Добавить приложение", "Add app")) }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            shape = RoundedCornerShape(MemoraDesign.radiusMedium),
            color = MaterialTheme.colorScheme.surface
        ) {
            Text(
                msg(
                    "Tanlangan dasturlardan matnni belgilaganda Memora manbani tezroq taklif qiladi. Boshqa dasturlardan ham Android menyusi orqali qo‘lda saqlash mumkin. Avtomatik kuzatuv o‘chiq.",
                    "Для выбранных приложений Memora быстрее предлагает источник. Из других приложений также можно сохранить вручную через меню Android. Автоматическое отслеживание выключено.",
                    "Selected apps get quick source suggestions. You can still save manually through Android from other apps. Automatic monitoring is off."
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        }

        Text(
            msg("Tezkor manba tanlash uchun dasturlar", "Приложения для быстрого выбора источника", "Apps for quick source selection"),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 22.dp, top = 14.dp, bottom = 8.dp)
        )
        if (enabledApps.isEmpty()) {
            Text(
                msg("Hozircha dastur tanlanmagan. + orqali dastur qo‘shing.", "Приложений пока нет. Нажмите +, чтобы добавить.", "No apps selected. Tap + to add one."),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp)
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 30.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(enabledApps, key = { it.packageName }) { app ->
                    AppPermissionRow(app = app, enabled = true) {
                        prefs.setEnabled(app.packageName, false)
                        revision++
                    }
                }
            }
        }
    }

    if (pickerOpen) {
        AlertDialog(
            onDismissRequest = { pickerOpen = false },
            title = { Text(msg("Dastur qo‘shish", "Добавить приложение", "Add app")) },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(apps, key = { it.packageName }) { app ->
                        val selected = app.packageName in enabled
                        AppPermissionRow(app = app, enabled = selected) {
                            prefs.setEnabled(app.packageName, !selected)
                            revision++
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { pickerOpen = false }) { Text(msg("Tayyor", "Готово", "Done")) } }
        )
    }
}

@Composable
private fun AppPermissionRow(app: LaunchableApp, enabled: Boolean, onToggle: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
        shape = RoundedCornerShape(MemoraDesign.radiusSmall),
        color = if (enabled) MaterialTheme.colorScheme.primary.copy(alpha = .08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .35f)
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(app.icon, contentDescription = null, modifier = Modifier.size(36.dp))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(app.label, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Switch(checked = enabled, onCheckedChange = { onToggle() })
        }
    }
}

private fun launchableApps(context: Context): List<LaunchableApp> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .asSequence()
        .mapNotNull { info ->
            val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
            if (pkg == context.packageName) return@mapNotNull null
            val label = runCatching { info.loadLabel(pm).toString() }.getOrDefault(pkg)
            val icon = runCatching { info.loadIcon(pm).toBitmap(96, 96).asImageBitmap() }.getOrNull() ?: return@mapNotNull null
            LaunchableApp(pkg, label, icon)
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
        .toList()
}

