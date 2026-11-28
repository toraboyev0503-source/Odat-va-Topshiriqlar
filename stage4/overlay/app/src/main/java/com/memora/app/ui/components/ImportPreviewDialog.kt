package com.memora.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.memora.app.export.ImportPreview
import com.memora.app.prefs.AppLanguage

@Composable
fun ImportPreviewDialog(
    preview: ImportPreview,
    language: AppLanguage,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    MemoraDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (language == AppLanguage.UZ) "Backup’ni tiklash" else "Restore backup",
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (language == AppLanguage.UZ)
                        "Fayl tekshirildi. Quyidagi ma’lumotlar tiklanadi:"
                    else "The file was checked. These items will be restored:",
                    style = MaterialTheme.typography.bodyMedium
                )
                PreviewRow(if (language == AppLanguage.UZ) "Yozuvlar" else "Notes", "${preview.newNotes}")
                if (preview.duplicateNotes > 0) {
                    PreviewRow(
                        if (language == AppLanguage.UZ) "Takror yozuvlar (o‘tkazib yuboriladi)" else "Duplicate notes (skipped)",
                        preview.duplicateNotes.toString()
                    )
                }
                if (preview.images > 0) PreviewRow(if (language == AppLanguage.UZ) "Rasmlar" else "Images", preview.images.toString())
                if (preview.audio > 0) PreviewRow("Audio", preview.audio.toString())
                if (preview.savedTitles > 0) PreviewRow(if (language == AppLanguage.UZ) "Saqlangan sarlavhalar" else "Saved titles", preview.savedTitles.toString())
                if (preview.reminders > 0) PreviewRow(if (language == AppLanguage.UZ) "Eslatmalar" else "Reminders", preview.reminders.toString())
                if (preview.timeTracePhotos > 0) PreviewRow(if (language == AppLanguage.UZ) "Vaqt izi rasmlari" else "Time Trace photos", preview.timeTracePhotos.toString())
                if (preview.topilmaSources > 0) PreviewRow(if (language == AppLanguage.UZ) "Topilmalar manbalari" else "Topilmalar sources", preview.topilmaSources.toString())
                if (preview.topilmaCards > 0) PreviewRow(if (language == AppLanguage.UZ) "Topilma kartochkalari" else "Topilmalar cards", preview.topilmaCards.toString())
                if (preview.treeHistory > 0) PreviewRow(if(language==AppLanguage.UZ) "Xotira daraxti tarixi" else "Memory tree history",preview.treeHistory.toString())
                if (preview.includesSettings) PreviewRow(if (language == AppLanguage.UZ) "Sozlamalar" else "Settings", "✓")
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(if (language == AppLanguage.UZ) "Tiklash" else "Restore")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (language == AppLanguage.UZ) "Bekor qilish" else "Cancel")
            }
        }
    )
}

@Composable
private fun PreviewRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}
