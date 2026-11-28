package com.memora.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.memora.app.export.BackupScope
import com.memora.app.export.ExportFormat
import com.memora.app.export.ExportPeriod
import com.memora.app.export.ExportRequest
import com.memora.app.export.WordLayout
import com.memora.app.i18n.L
import com.memora.app.i18n.S
import com.memora.app.prefs.AppLanguage
import java.time.LocalDate

@Composable
fun ExportDialog(
    language: AppLanguage,
    onDismiss: () -> Unit,
    onExport: (ExportRequest) -> Unit
) {
    val now = LocalDate.now()
    var format by remember { mutableStateOf(ExportFormat.MULTIMEDIA) }
    var period by remember { mutableStateOf(ExportPeriod.ALL) }
    var year by remember { mutableIntStateOf(now.year) }
    var month by remember { mutableIntStateOf(now.monthValue) }
    var scope by remember { mutableStateOf(BackupScope.BOTH) }
    var wordLayout by remember { mutableStateOf(WordLayout.CHRONOLOGICAL) }
    var yearMenu by remember { mutableStateOf(false) }
    var monthMenu by remember { mutableStateOf(false) }

    MemoraDialog(
        onDismissRequest = onDismiss,
        title = { Text(L.t(language, S.SAVE_NOTES)) },
        text = {
            Column {
                Text(L.t(language, S.SAVE_FILE), style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    FilterChip(
                        selected = format == ExportFormat.MULTIMEDIA,
                        onClick = { format = ExportFormat.MULTIMEDIA },
                        label = { Text("HTML") },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    FilterChip(
                        selected = format == ExportFormat.WORD,
                        onClick = { format = ExportFormat.WORD },
                        label = { Text(L.t(language, S.WORD)) }
                    )
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    FilterChip(
                        selected = format == ExportFormat.BACKUP,
                        onClick = { format = ExportFormat.BACKUP },
                        label = { Text(L.t(language, S.APP_BACKUP)) }
                    )
                }

                if (format != ExportFormat.BACKUP) {
                    Text(
                        L.t(language, S.EXPORT_LAYOUT),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 18.dp)
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        WordLayout.entries.forEach { item ->
                            FilterChip(
                                selected = wordLayout == item,
                                onClick = { wordLayout = item },
                                label = {
                                    Text(
                                        L.t(language, if (item == WordLayout.CHRONOLOGICAL) {
                                            S.WORD_LAYOUT_CHRONOLOGICAL
                                        } else {
                                            S.WORD_LAYOUT_BY_TITLE
                                        })
                                    )
                                },
                                modifier = Modifier.padding(end = 6.dp)
                            )
                        }
                    }
                    Text(
                        L.t(
                            language,
                            if (format == ExportFormat.MULTIMEDIA) S.MULTIMEDIA_NOTE else S.WORD_PACKAGE_NOTE
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Text(
                    L.t(language, S.EXPORT_PERIOD),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 18.dp)
                )
                Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    ExportPeriod.entries.forEach { item ->
                        FilterChip(
                            selected = period == item,
                            onClick = { period = item },
                            label = {
                                Text(
                                    L.t(language, when (item) {
                                        ExportPeriod.MONTH -> S.MONTH
                                        ExportPeriod.YEAR -> S.YEAR
                                        ExportPeriod.ALL -> S.ALL
                                    })
                                )
                            },
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                }

                if (period != ExportPeriod.ALL) {
                    TextButton(onClick = { yearMenu = true }) {
                        Text("${L.t(language, S.YEAR)}: $year")
                    }
                    DropdownMenu(expanded = yearMenu, onDismissRequest = { yearMenu = false }) {
                        for (value in now.year downTo (now.year - 50)) {
                            DropdownMenuItem(
                                text = { Text(value.toString()) },
                                onClick = { year = value; yearMenu = false }
                            )
                        }
                    }
                }
                if (period == ExportPeriod.MONTH) {
                    TextButton(onClick = { monthMenu = true }) {
                        Text("${L.t(language, S.MONTH)}: $month")
                    }
                    DropdownMenu(expanded = monthMenu, onDismissRequest = { monthMenu = false }) {
                        (1..12).forEach { value ->
                            DropdownMenuItem(
                                text = { Text(value.toString()) },
                                onClick = { month = value; monthMenu = false }
                            )
                        }
                    }
                }

                Text(
                    L.t(language, S.NOTE_SCOPE),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 12.dp)
                )
                Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    BackupScope.entries.forEach { item ->
                        FilterChip(
                            selected = scope == item,
                            onClick = { scope = item },
                            label = {
                                Text(
                                    L.t(language, when (item) {
                                        BackupScope.SHORT -> S.SHORT
                                        BackupScope.LONG -> S.LONG
                                        BackupScope.BOTH -> S.BOTH
                                    })
                                )
                            },
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onExport(
                        ExportRequest(
                            format = format,
                            period = period,
                            year = if (period == ExportPeriod.ALL) null else year,
                            month = if (period == ExportPeriod.MONTH) month else null,
                            backupScope = scope,
                            wordLayout = wordLayout
                        )
                    )
                }
            ) { Text(L.t(language, S.EXPORT)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(L.t(language, S.CANCEL)) }
        }
    )
}
