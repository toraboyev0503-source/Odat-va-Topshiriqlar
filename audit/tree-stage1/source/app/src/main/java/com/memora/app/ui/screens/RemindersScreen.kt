package com.memora.app.ui.screens

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.memora.app.data.local.ReminderEntity
import com.memora.app.i18n.L
import com.memora.app.i18n.S
import com.memora.app.prefs.AppLanguage
import com.memora.app.ui.components.MemoraConfirmationDialog
import com.memora.app.ui.components.MemoraTopBar
import com.memora.app.ui.theme.MemoraDesign
import java.time.LocalTime
import java.util.Locale

@Composable
fun RemindersScreen(
    reminders: List<ReminderEntity>,
    language: AppLanguage,
    exactTimingAvailable: Boolean,
    onBack: () -> Unit,
    onRequestNotifications: () -> Unit,
    onRequestExactTiming: () -> Unit,
    onAdd: (Int, Int) -> Unit,
    onToggle: (ReminderEntity, Boolean) -> Unit,
    onDelete: (ReminderEntity) -> Unit,
    monthlySummaryEnabled: Boolean,
    yearlySummaryEnabled: Boolean,
    onMonthlySummary: (Boolean) -> Unit,
    onYearlySummary: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var pendingDelete by remember { mutableStateOf<ReminderEntity?>(null) }

    fun showPicker() {
        onRequestNotifications()
        val now = LocalTime.now()
        TimePickerDialog(
            context,
            { _, hour, minute -> onAdd(hour, minute) },
            now.hour,
            now.minute,
            true
        ).show()
    }

    Column(Modifier.fillMaxSize()) {
        MemoraTopBar(
            title = L.t(language, S.WRITING_TIME),
            navigation = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = null)
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp, end = 20.dp, top = 10.dp, bottom = 30.dp
            )
        ) {
            item("schedule-note") {
                Text(
                    L.t(language, S.SCHEDULE_NOTE),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            if (!exactTimingAvailable) {
                item("precise") {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        shape = RoundedCornerShape(MemoraDesign.radiusMedium),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Alarm, contentDescription = null)
                                Text(
                                    L.t(language, S.PRECISE_REMINDERS),
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(start = 10.dp)
                                )
                            }
                            Text(
                                L.t(language, S.REMINDER_FALLBACK_NOTE),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            TextButton(onClick = onRequestExactTiming) {
                                Text(L.t(language, S.ENABLE_PRECISE_REMINDERS))
                            }
                        }
                    }
                }
            }

            items(reminders, key = { it.id }) { reminder ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    shape = RoundedCornerShape(MemoraDesign.radiusMedium),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            String.format(Locale.US, "%02d:%02d", reminder.hour, reminder.minute),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = reminder.enabled,
                            onCheckedChange = { onToggle(reminder, it) }
                        )
                        IconButton(onClick = { pendingDelete = reminder }) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = L.t(language, S.DELETE))
                        }
                    }
                }
            }


            item("summary-title") {
                Text(
                    if (language == AppLanguage.UZ) "Xulosa bildirishnomalari" else "Summary notifications",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 22.dp, bottom = 8.dp)
                )
            }
            item("monthly-summary") {
                SummaryToggle(
                    label = if (language == AppLanguage.UZ) "Oylik xulosa" else "Monthly summary",
                    checked = monthlySummaryEnabled,
                    onChecked = onMonthlySummary
                )
            }
            item("yearly-summary") {
                SummaryToggle(
                    label = if (language == AppLanguage.UZ) "Yillik xulosa" else "Yearly summary",
                    checked = yearlySummaryEnabled,
                    onChecked = onYearlySummary
                )
            }

            item("add") {
                Surface(
                    onClick = ::showPicker,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    Row(
                        Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Text(
                            L.t(language, S.ADD_REMINDER),
                            modifier = Modifier.padding(start = 10.dp),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { reminder ->
        MemoraConfirmationDialog(
            language = language,
            title = if (language == AppLanguage.UZ) "Eslatmani o‘chirasizmi?" else "Delete reminder?",
            text = if (language == AppLanguage.UZ) "Bu yozish eslatmasi o‘chiriladi." else "This writing reminder will be deleted.",
            onConfirm = { onDelete(reminder); pendingDelete = null },
            onDismiss = { pendingDelete = null }
        )
    }
}


@Composable
private fun SummaryToggle(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Surface(
        shape = RoundedCornerShape(MemoraDesign.radiusMedium),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = onChecked)
        }
    }
}
