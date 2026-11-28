package com.memora.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.memora.app.data.local.NoteEntity
import com.memora.app.data.local.NoteType
import com.memora.app.i18n.L
import com.memora.app.i18n.S
import com.memora.app.prefs.AppLanguage
import com.memora.app.ui.components.MemoraConfirmationDialog
import com.memora.app.ui.components.MemoraEmptyState
import com.memora.app.ui.components.MemoraTopBar
import com.memora.app.ui.components.NoteCard
import com.memora.app.ui.theme.MemoraDesign
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun NotesScreen(
    type: NoteType,
    notes: List<NoteEntity>,
    language: AppLanguage,
    canWrite: Boolean,
    onBack: () -> Unit,
    onNew: () -> Unit,
    onWriteRequired: () -> Unit,
    onOpen: (String) -> Unit,
    onSearch: () -> Unit,
    onDelete: (List<String>) -> Unit
) {
    var selected by remember { mutableStateOf(setOf<String>()) }
    var confirmDelete by remember { mutableStateOf(false) }

    val zone = remember { ZoneId.systemDefault() }
    val groupedNotes = remember(notes, zone) {
        notes.groupBy { note ->
            Instant.ofEpochMilli(note.createdAt).atZone(zone).toLocalDate()
        }
    }

    BackHandler {
        when {
            confirmDelete -> confirmDelete = false
            selected.isNotEmpty() -> selected = emptySet()
            else -> onBack()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(Modifier.fillMaxSize()) {
            if (selected.isEmpty()) {
                MemoraTopBar(
                    title = L.t(language, if (type == NoteType.SHORT) S.SHORT_NOTES else S.LONG_NOTES),
                    navigation = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Rounded.ArrowBack, contentDescription = null)
                        }
                    },
                    actions = {
                        IconButton(onClick = onSearch) {
                            Icon(Icons.Rounded.Search, contentDescription = L.t(language, S.SEARCH))
                        }
                    }
                )
            } else {
                MemoraTopBar(
                    title = selected.size.toString(),
                    navigation = {
                        IconButton(onClick = { selected = emptySet() }) {
                            Icon(Icons.Rounded.Close, contentDescription = null)
                        }
                    },
                    actions = {
                        IconButton(onClick = { if (canWrite) confirmDelete = true else onWriteRequired() }) {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = L.t(language, S.DELETE),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = MemoraDesign.screenPadding,
                    end = MemoraDesign.screenPadding,
                    top = MemoraDesign.spaceSm,
                    bottom = 34.dp
                )
            ) {
                item("new") {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = { if (canWrite) onNew() else onWriteRequired() }),
                        shape = RoundedCornerShape(MemoraDesign.radiusMedium),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null)
                            Text(
                                L.t(language, S.NEW_NOTE),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(MemoraDesign.spaceLg))
                }

                if (notes.isEmpty()) {
                    item("empty") {
                        MemoraEmptyState(
                            title = L.t(language, S.NO_NOTES),
                            body = if (language == AppLanguage.UZ)
                                "Birinchi xotirangizni yozganingizda u shu yerda paydo bo‘ladi."
                            else "Your first memory will appear here after you write it.",
                            modifier = Modifier.padding(vertical = 18.dp)
                        )
                    }
                }

                groupedNotes.entries.forEachIndexed { groupIndex, (date, dayNotes) ->
                    item("day-$date") {
                        if (groupIndex > 0) Spacer(Modifier.height(MemoraDesign.spaceXl))
                        DayGroupHeader(
                            date = date,
                            count = dayNotes.size,
                            language = language
                        )
                        Spacer(Modifier.height(MemoraDesign.spaceSm))
                    }

                    items(dayNotes, key = { it.id }) { note ->
                        val isSelected = note.id in selected
                        NoteCard(
                            note = note,
                            displayTitle = note.title.ifBlank { L.t(language, S.UNTITLED) },
                            language = language,
                            selected = isSelected,
                            previewLines = if (type == NoteType.SHORT) 2 else 3,
                            onClick = {
                                if (selected.isNotEmpty()) {
                                    selected = if (isSelected) selected - note.id else selected + note.id
                                } else {
                                    onOpen(note.id)
                                }
                            },
                            onLongClick = if (canWrite) ({
                                selected = if (isSelected) selected - note.id else selected + note.id
                            }) else ({ onWriteRequired() }),
                            onDeleteRequest = {
                                if (canWrite) {
                                    selected = setOf(note.id)
                                    confirmDelete = true
                                } else {
                                    onWriteRequired()
                                }
                            }
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        MemoraConfirmationDialog(
            language = language,
            title = L.t(language, S.DELETE_CONFIRM_TITLE),
            text = L.t(language, S.DELETE_CONFIRM_TEXT),
            confirmLabel = L.t(language, S.CONFIRM),
            onConfirm = {
                onDelete(selected.toList())
                selected = emptySet()
                confirmDelete = false
            },
            onDismiss = { confirmDelete = false }
        )
    }
}

@Composable
private fun DayGroupHeader(
    date: LocalDate,
    count: Int,
    language: AppLanguage
) {
    val formatter = remember(language) {
        DateTimeFormatter.ofPattern("d-MMM, yyyy", java.util.Locale.forLanguageTag(language.tag))
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = date.format(formatter),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = noteCountText(count, language),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun noteCountText(count: Int, language: AppLanguage): String = when (language) {
    AppLanguage.UZ -> "$count ta ${L.t(language, S.NOTES)}"
    else -> "$count ${L.t(language, S.NOTES)}"
}
