package com.memora.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.memora.app.data.local.NoteEntity
import com.memora.app.data.local.NoteType
import com.memora.app.data.local.SavedTitleEntity
import com.memora.app.data.model.NoteMood
import com.memora.app.i18n.L
import com.memora.app.i18n.S
import com.memora.app.prefs.AppLanguage
import com.memora.app.ui.components.MemoraEmptyState
import com.memora.app.ui.components.NoteCard
import com.memora.app.ui.theme.MemoraDesign

@Composable
fun SearchScreen(
    type: NoteType,
    notes: List<NoteEntity>,
    titles: List<SavedTitleEntity>,
    language: AppLanguage,
    onBack: () -> Unit,
    onOpen: (String) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedTitle by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedMood by rememberSaveable { mutableStateOf<String?>(null) }

    val allTitleFilters = remember(titles) {
        titles.map { it.value }.distinct()
    }

    val filtered = remember(notes, query, selectedTitle, selectedMood) {
        val q = query.trim()
        notes.filter { note ->
            val titleOk = selectedTitle == null || note.title == selectedTitle
            val moodOk = selectedMood == null || note.mood == selectedMood
            val queryOk = q.isBlank() ||
                note.title.contains(q, ignoreCase = true) ||
                note.plainText.contains(q, ignoreCase = true)
            titleOk && moodOk && queryOk
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = null)
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
                placeholder = { Text(L.t(language, S.SEARCH)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = null)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(MemoraDesign.radiusMedium)
            )
        }

        if (allTitleFilters.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                allTitleFilters.forEach { title ->
                    FilterChip(
                        selected = selectedTitle == title,
                        onClick = {
                            selectedTitle = if (selectedTitle == title) null else title
                        },
                        label = { Text(title) }
                    )
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NoteMood.entries.forEach { mood ->
                FilterChip(
                    selected = selectedMood == mood.name,
                    onClick = {
                        selectedMood = if (selectedMood == mood.name) null else mood.name
                    },
                    label = { Text("${mood.emoji} ${L.t(language, searchMoodLabelKey(mood))}") }
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp, end = 20.dp, top = 10.dp, bottom = 30.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(filtered, key = { it.id }) { note ->
                NoteCard(
                    note = note,
                    displayTitle = note.title.ifBlank { L.t(language, S.UNTITLED) },
                    language = language,
                    selected = false,
                    onClick = { onOpen(note.id) },
                    onLongClick = {}
                )
            }
            if (filtered.isEmpty()) {
                item {
                    MemoraEmptyState(
                        title = if (query.isBlank()) L.t(language, S.NO_NOTES) else if (language == AppLanguage.UZ) "Natija topilmadi" else "No results found",
                        body = if (query.isBlank()) {
                            if (language == AppLanguage.UZ) "Qidirish uchun avval biror xotira yozing." else "Write a memory first, then search it here."
                        } else {
                            if (language == AppLanguage.UZ) "Boshqa so‘z yoki ibora bilan qidirib ko‘ring." else "Try another word or phrase."
                        },
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            }
        }
    }
}

private fun searchMoodLabelKey(mood: NoteMood): S = when (mood) {
    NoteMood.GREAT -> S.MOOD_GREAT
    NoteMood.GOOD -> S.MOOD_GOOD
    NoteMood.NEUTRAL -> S.MOOD_NEUTRAL
    NoteMood.BAD -> S.MOOD_BAD
    NoteMood.VERY_BAD -> S.MOOD_VERY_BAD
}
