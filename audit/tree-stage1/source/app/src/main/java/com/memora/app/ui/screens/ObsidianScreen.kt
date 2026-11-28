package com.memora.app.ui.screens

import com.memora.app.ui.components.MemoraDialog as AlertDialog
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memora.app.obsidian.ObsidianSyncManager
import com.memora.app.prefs.AppLanguage
import com.memora.app.prefs.UserSettings
import com.memora.app.ui.theme.MemoraDesign
import com.memora.app.util.TimeUtils
import kotlinx.coroutines.launch

@Composable
fun ObsidianScreen(
    settings: UserSettings,
    manager: ObsidianSyncManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pending by manager.pendingCount.collectAsStateWithLifecycle(initialValue = 0)
    val lastSync by manager.lastSyncedAt.collectAsStateWithLifecycle(initialValue = null)
    val lastError by manager.lastError.collectAsStateWithLifecycle(initialValue = null)
    var exportExisting by remember { mutableStateOf(settings.obsidianExportExisting) }
    var showConnectChoice by remember { mutableStateOf(false) }
    var connectionError by remember { mutableStateOf<String?>(null) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        scope.launch {
            runCatching { manager.connect(uri, exportExisting) }
                .onFailure { connectionError = it.message ?: "Obsidian papkasiga ulanib bo‘lmadi" }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Orqaga") }
            Text(
                if (settings.language == AppLanguage.UZ) "Obsidian" else "Obsidian",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        }

        Column(
            Modifier.fillMaxSize().padding(horizontal = MemoraDesign.screenPadding, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(
                shape = RoundedCornerShape(MemoraDesign.radiusLarge),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = MemoraDesign.shadowSubtle)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ObsidianMark(Modifier.size(52.dp))
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text("Obsidian", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (settings.obsidianEnabled) "Ulangan — Memora → Obsidian" else "Ulanmagan",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.obsidianEnabled,
                        onCheckedChange = { enabled ->
                            when {
                                enabled && settings.obsidianTreeUri.isNullOrBlank() -> showConnectChoice = true
                                else -> scope.launch { manager.setEnabled(enabled) }
                            }
                        }
                    )
                }
            }

            if (settings.obsidianTreeUri != null) {
                ObsidianStatusRow("Vault", Uri.parse(settings.obsidianTreeUri).lastPathSegment ?: settings.obsidianTreeUri.orEmpty())
                ObsidianStatusRow(
                    "Oxirgi sinxronlash",
                    lastSync?.let { TimeUtils.dateTime(it, settings.language) } ?: "Hali sinxronlanmagan"
                )
                ObsidianStatusRow("Kutilayotganlar", pending.toString())
                lastError?.takeIf { it.isNotBlank() }?.let { error ->
                    Text(
                        text = "Sinxronlash: $error",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { scope.launch { manager.syncPending() } },
                        modifier = Modifier.weight(1f),
                        enabled = settings.obsidianEnabled
                    ) {
                        Icon(Icons.Rounded.Sync, null, modifier = Modifier.size(18.dp))
                        Text("Hozir sinxronlash", modifier = Modifier.padding(start = 7.dp))
                    }
                    OutlinedButton(
                        onClick = { scope.launch { manager.resyncAll() } },
                        modifier = Modifier.weight(1f),
                        enabled = settings.obsidianEnabled
                    ) {
                        Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(18.dp))
                        Text("Qayta", modifier = Modifier.padding(start = 7.dp))
                    }
                }
                OutlinedButton(
                    onClick = { showConnectChoice = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.FolderOpen, null)
                    Text("Papkani almashtirish", modifier = Modifier.padding(start = 8.dp))
                }
            } else {
                Text(
                    "Obsidian vault asosiy papkasini bir marta tanlaysiz. Memora ichida Qisqa matn, Topilmalar va Uzun matn papkalari avtomatik yaratiladi.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(Modifier.weight(1f))
            Text(
                "Sinxronlash bir tomonlama: Memora → Obsidian. Obsidian ichida Memora blokidan tashqariga yozgan qo‘shimchalaringiz keyingi sinxronlashda saqlanib qoladi.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showConnectChoice) {
        AlertDialog(
            onDismissRequest = { showConnectChoice = false },
            title = { Text("Obsidian ulash") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Birinchi ulashda qaysi yozuvlar o‘tsin?")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = exportExisting, onClick = { exportExisting = true })
                        Text("Barcha mavjud yozuvlarni ham ko‘chirish")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = !exportExisting, onClick = { exportExisting = false })
                        Text("Faqat bundan keyingilar")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showConnectChoice = false
                    folderPicker.launch(settings.obsidianTreeUri?.let { Uri.parse(it) })
                }) { Text("Papka tanlash") }
            },
            dismissButton = { TextButton(onClick = { showConnectChoice = false }) { Text("Bekor qilish") } }
        )
    }

    connectionError?.let { error ->
        AlertDialog(
            onDismissRequest = { connectionError = null },
            title = { Text("Ulanish amalga oshmadi") },
            text = { Text(error) },
            confirmButton = { TextButton(onClick = { connectionError = null }) { Text("Yopish") } }
        )
    }
}

@Composable
private fun ObsidianStatusRow(label: String, value: String) {
    Card(shape = RoundedCornerShape(MemoraDesign.radiusMedium)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1.4f)
            )
        }
    }
}

@Composable
private fun ObsidianMark(modifier: Modifier = Modifier) {
    val primary = Color(0xFF7C5CFC)
    val secondary = Color(0xFF9A7DFF)
    val dark = Color(0xFF4C35A8)
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val outline = Path().apply {
            moveTo(w * .48f, h * .04f)
            lineTo(w * .84f, h * .28f)
            lineTo(w * .74f, h * .78f)
            lineTo(w * .42f, h * .96f)
            lineTo(w * .13f, h * .66f)
            lineTo(w * .20f, h * .22f)
            close()
        }
        drawPath(outline, primary)
        val facetA = Path().apply {
            moveTo(w * .48f, h * .04f); lineTo(w * .55f, h * .55f); lineTo(w * .20f, h * .22f); close()
        }
        drawPath(facetA, secondary)
        val facetB = Path().apply {
            moveTo(w * .55f, h * .55f); lineTo(w * .84f, h * .28f); lineTo(w * .74f, h * .78f); close()
        }
        drawPath(facetB, dark)
        drawLine(color = Color.White.copy(alpha = .25f), start = Offset(w * .20f, h * .22f), end = Offset(w * .74f, h * .78f), strokeWidth = w * .035f)
    }
}
