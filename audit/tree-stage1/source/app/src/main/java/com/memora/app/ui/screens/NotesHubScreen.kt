package com.memora.app.ui.screens

import com.memora.app.ui.components.MemoraSheet
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.memora.app.data.local.NoteSummary
import com.memora.app.data.repository.MemoraRepository
import com.memora.app.data.repository.NoteListFilter
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.paging.LoadState
import kotlinx.coroutines.delay
import com.memora.app.data.local.NoteType
import com.memora.app.data.model.NoteMood
import com.memora.app.i18n.L
import com.memora.app.i18n.S
import com.memora.app.prefs.AppLanguage
import com.memora.app.ui.components.MemoraConfirmationDialog
import com.memora.app.ui.components.MemoraEmptyState
import com.memora.app.ui.components.MoodSticker
import com.memora.app.ui.components.NoteCard
import com.memora.app.ui.theme.MemoraDesign
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private enum class NotesKind { ALL, SHORT, LONG }
private enum class MediaFilter { ANY, IMAGE, AUDIO, ANY_MEDIA, NO_MEDIA }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesHubScreen(
    repository: MemoraRepository,
    language: AppLanguage,
    initialType: NoteType? = null,
    canEdit: Boolean,
    onOpen: (String) -> Unit,
    onEdit: (NoteSummary) -> Unit,
    onDelete: (NoteSummary) -> Unit
) {
    var kind by remember(initialType) {
        mutableStateOf(
            when (initialType) {
                NoteType.SHORT -> NotesKind.SHORT
                NoteType.LONG -> NotesKind.LONG
                null -> NotesKind.ALL
            }
        )
    }
    var query by remember { mutableStateOf("") }
    var mood by remember { mutableStateOf<NoteMood?>(null) }
    var media by remember { mutableStateOf(MediaFilter.ANY) }
    var filterSheet by remember { mutableStateOf(false) }
    var dateFrom by remember { mutableStateOf<LocalDate?>(null) }
    var dateTo by remember { mutableStateOf<LocalDate?>(null) }
    var pendingDelete by remember { mutableStateOf<NoteSummary?>(null) }
    val listState = rememberLazyListState()
    val zone = remember { ZoneId.systemDefault() }
    var pickingFromDate by remember { mutableStateOf<Boolean?>(null) }

    // Debounce DB/FTS queries while preserving the visible search text on each keystroke.
    var settledQuery by remember { mutableStateOf("") }
    LaunchedEffect(query) {
        delay(220)
        settledQuery = query.trim()
    }
    val filter = remember(kind, settledQuery, mood, media, dateFrom, dateTo, zone) {
        NoteListFilter(
            type = when (kind) {
                NotesKind.ALL -> null
                NotesKind.SHORT -> NoteType.SHORT.name
                NotesKind.LONG -> NoteType.LONG.name
            },
            search = settledQuery,
            mood = mood?.name,
            media = media.name,
            from = dateFrom?.atStartOfDay(zone)?.toInstant()?.toEpochMilli(),
            untilExclusive = dateTo?.plusDays(1)?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()
        )
    }
    val pagingScope = rememberCoroutineScope()
    val pages = remember(repository, filter, pagingScope) {
        repository.pagedNotes(filter).cachedIn(pagingScope)
    }.collectAsLazyPagingItems()
    LaunchedEffect(filter) { listState.scrollToItem(0) }
    val matchingCount by remember(repository, filter) {
        repository.observeFilteredCount(filter)
    }.collectAsStateWithLifecycle(0)
    val archiveYears by remember(repository) {
        repository.observeArchiveYears()
    }.collectAsStateWithLifecycle(emptyList())
    val yearCounts = remember(archiveYears) { archiveYears.associate { it.year to it.count } }
    val currentYear = LocalDate.now().year
    var expandedYears by rememberSaveable { mutableStateOf(emptyList<Int>()) }
    val discoveryMode = query.isNotBlank() || mood != null || media != MediaFilter.ANY || dateFrom != null || dateTo != null

    Column(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
        Column(
            Modifier.padding(
                start = MemoraDesign.screenPadding,
                end = MemoraDesign.screenPadding,
                top = MemoraDesign.screenPadding,
                bottom = 8.dp
            )
        ) {
            Text(
                text = notesTitle(language),
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 38.sp),
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NotesKind.entries.forEach { option ->
                    FilterChip(
                        selected = kind == option,
                        onClick = { kind = option },
                        label = {
                            Text(
                                when (option) {
                                    NotesKind.ALL -> allLabel(language)
                                    NotesKind.SHORT -> L.t(language, S.SHORT)
                                    NotesKind.LONG -> L.t(language, S.LONG)
                                }
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color.Transparent,
                            selectedContainerColor = notesSelectedContainer(),
                            selectedLabelColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = .72f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = .58f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = .75f),
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .55f)
                ),
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                placeholder = { Text(L.t(language, S.SEARCH)) },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (query.isNotBlank()) {
                            IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, contentDescription = null) }
                        }
                        IconButton(onClick = { filterSheet = true }) {
                            Icon(
                                Icons.Rounded.FilterList,
                                contentDescription = filterLabel(language),
                                tint = if (mood != null || media != MediaFilter.ANY || dateFrom != null || dateTo != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (query.isBlank()) countLabel(language, matchingCount) else foundLabel(language, matchingCount),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                mood?.let {
                    MoodSticker(
                        mood = it,
                        description = moodName(language, it),
                        modifier = Modifier.padding(start = 8.dp).size(25.dp)
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(
                start = MemoraDesign.screenPadding,
                end = MemoraDesign.screenPadding,
                top = 4.dp,
                bottom = 28.dp
            )
        ) {
            if (pages.itemCount == 0 && pages.loadState.refresh is LoadState.NotLoading) {
                item("empty") {
                    Column(Modifier.fillMaxWidth().padding(top = 24.dp)) {
                        MemoraEmptyState(
                            title = if (query.isBlank()) emptyNotes(language) else noMatches(language),
                            body = if (query.isBlank()) {
                                if (language == AppLanguage.UZ) "Yozuvlaringiz shu yerda bir joyga jamlanadi." else "Your notes will gather here in one place."
                            } else {
                                if (language == AppLanguage.UZ) "Qidiruv yoki filtrlarni o‘zgartirib ko‘ring." else "Try changing your search or filters."
                            }
                        )
                        if (discoveryMode) TextButton(
                            onClick = { query = ""; mood = null; media = MediaFilter.ANY; dateFrom = null; dateTo = null },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) { Text(clearFilters(language)) }
                    }
                }
            }
            items(
                count = pages.itemCount,
                key = pages.itemKey { it.id }
            ) { index ->
                val note = pages[index]
                if (note != null) {
                    val previous = if (index > 0) pages.peek(index - 1) else null
                    val date = Instant.ofEpochMilli(note.createdAt).atZone(zone).toLocalDate()
                    val previousDate = previous?.let { Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate() }
                    val year = date.year
                    val archived = !discoveryMode && NotesArchivePolicy.archived(year, currentYear)
                    val expanded = !archived || year in expandedYears
                    Column {
                        if (archived && previousDate?.year != year) {
                            ArchiveGroupHeader(
                                title = year.toString(), count = yearCounts[year] ?: 0,
                                expanded = expanded, language = language,
                                onClick = {
                                    expandedYears = if (expanded) expandedYears - year else expandedYears + year
                                }
                            )
                        }
                        if (expanded) {
                            if (date != previousDate) DayArchiveHeader(date = date, language = language)
                            ArchiveNoteCard(
                                note = note, language = language, canEdit = canEdit,
                                onOpen = onOpen, onEdit = onEdit,
                                onDelete = { pendingDelete = it }
                            )
                        }
                    }
                }
            }
            if (pages.loadState.refresh is LoadState.Loading || pages.loadState.append is LoadState.Loading) {
                item("loading") {
                    Box(Modifier.fillMaxWidth().padding(22.dp), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.CircularProgressIndicator()
                    }
                }
            }
            val failure = (pages.loadState.refresh as? LoadState.Error)
                ?: (pages.loadState.append as? LoadState.Error)
            if (failure != null) {
                item("load-error") {
                    TextButton(onClick = { pages.retry() }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (language == AppLanguage.UZ) "Yuklashda xato. Qayta urinish" else "Retry loading notes")
                    }
                }
            }
        }
    }

    if (filterSheet) {
        MemoraSheet(onDismissRequest = { filterSheet = false }) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) {
                Text(filterLabel(language), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(moodFilterLabel(language), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    NoteMood.entries.forEach { option ->
                        val selected = mood == option
                        Surface(
                            onClick = { mood = if (selected) null else option },
                            shape = RoundedCornerShape(MemoraDesign.radiusSmall),
                            color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .12f) else MaterialTheme.colorScheme.surface
                        ) {
                            MoodSticker(option, moodName(language, option), Modifier.padding(5.dp).size(35.dp))
                        }
                    }
                }
                Text(dateFilterLabel(language), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp, bottom = 5.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { pickingFromDate = true },
                        modifier = Modifier.weight(1f)
                    ) { Text(dateFrom?.toString() ?: fromLabel(language)) }
                    OutlinedButton(
                        onClick = { pickingFromDate = false },
                        modifier = Modifier.weight(1f)
                    ) { Text(dateTo?.toString() ?: toLabel(language)) }
                }
                if (dateFrom != null || dateTo != null) {
                    TextButton(onClick = { dateFrom = null; dateTo = null }) { Text(clearDateLabel(language)) }
                }

                Text(mediaFilterLabel(language), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp, bottom = 5.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf(MediaFilter.ANY, MediaFilter.IMAGE, MediaFilter.AUDIO).forEach { option ->
                        FilterChip(
                            selected = media == option,
                            onClick = { media = option },
                            label = { Text(mediaName(language, option)) }
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf(MediaFilter.ANY_MEDIA, MediaFilter.NO_MEDIA).forEach { option ->
                        FilterChip(
                            selected = media == option,
                            onClick = { media = option },
                            label = { Text(mediaName(language, option)) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { mood = null; media = MediaFilter.ANY; dateFrom = null; dateTo = null }) {
                        Text(clearFilters(language))
                    }
                    androidx.compose.material3.Button(onClick = { filterSheet = false }) {
                        Text(filterResultsLabel(language))
                    }
                }
            }
        }
    }

    pickingFromDate?.let { from ->
        // The date picker uses the same Compose Material theme as every Memora overlay.
        val initial = if (from) dateFrom else dateTo
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = (initial ?: LocalDate.now())
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { pickingFromDate = null },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val selected = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        if (from) dateFrom = selected else dateTo = selected
                    }
                    pickingFromDate = null
                }) { Text(if (language == AppLanguage.UZ) "Tanlash" else if (language == AppLanguage.RU) "Выбрать" else "Select") }
            },
            dismissButton = {
                TextButton(onClick = { pickingFromDate = null }) { Text(L.t(language, S.CANCEL)) }
            }
        ) { DatePicker(state = datePickerState, showModeToggle = false) }
    }

    pendingDelete?.let { note ->
        MemoraConfirmationDialog(
            language = language,
            title = deleteNoteTitle(language),
            text = deleteNoteText(language),
            onConfirm = { onDelete(note); pendingDelete = null },
            onDismiss = { pendingDelete = null }
        )
    }
}

@Composable
private fun ArchiveGroupHeader(
    title: String,
    count: Int,
    expanded: Boolean,
    language: AppLanguage,
    onClick: () -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(160),
        label = "archive-chevron"
    )
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 0.dp,
                top = 12.dp,
                bottom = 4.dp
            ),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .42f)
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = 14.dp,
                vertical = 11.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp).rotate(rotation),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).padding(start = 7.dp)
            )
            Text(
                archiveCountLabel(count, language),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DayArchiveHeader(date: LocalDate, language: AppLanguage) {
    Text(
        dayLabel(date, language),
        style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 18.dp, top = 12.dp, bottom = 7.dp)
    )
}

@Composable
private fun ArchiveNoteCard(
    note: NoteSummary,
    language: AppLanguage,
    canEdit: Boolean,
    onOpen: (String) -> Unit,
    onEdit: (NoteSummary) -> Unit,
    onDelete: (NoteSummary) -> Unit
) {
    NoteCard(
        note = note.asCardEntity(),
        displayTitle = note.title.ifBlank { L.t(language, S.UNTITLED) },
        language = language,
        selected = false,
        previewLines = if (note.type == NoteType.SHORT.name) 2 else 3,
        onClick = { onOpen(note.id) },
        onLongClick = {},
        modifier = Modifier.testTag("memora_note_card"),
        onDeleteRequest = { onDelete(note) },
        onEditRequest = if (canEdit) ({ onEdit(note) }) else null,
        imageCount = note.imageCount,
        audioCount = note.audioCount
    )
    Spacer(Modifier.height(9.dp))
}

private fun archiveCountLabel(count: Int, language: AppLanguage): String = when (language) {
    AppLanguage.UZ -> "$count yozuv"
    AppLanguage.RU -> "$count записей"
    else -> "$count notes"
}

private fun notesTitle(l: AppLanguage) = when(l){ AppLanguage.UZ->"Yozuvlar"; AppLanguage.RU->"Записи"; AppLanguage.FR->"Écrits"; AppLanguage.DE->"Einträge"; AppLanguage.ES->"Notas"; AppLanguage.PT_BR->"Notas"; AppLanguage.TR->"Yazılar"; AppLanguage.AR->"الكتابات"; AppLanguage.HI->"लेखन"; AppLanguage.ID->"Catatan"; AppLanguage.JA->"記録"; AppLanguage.KO->"기록"; else->"Notes" }
private fun allLabel(l: AppLanguage)=when(l){AppLanguage.UZ->"Barchasi";AppLanguage.RU->"Все";AppLanguage.FR->"Tout";AppLanguage.DE->"Alle";AppLanguage.ES->"Todo";AppLanguage.PT_BR->"Tudo";AppLanguage.TR->"Tümü";AppLanguage.AR->"الكل";AppLanguage.HI->"सभी";AppLanguage.ID->"Semua";AppLanguage.JA->"すべて";AppLanguage.KO->"전체";else->"All"}
private fun filterLabel(l: AppLanguage)=when(l){AppLanguage.UZ->"Filtr";AppLanguage.RU->"Фильтр";AppLanguage.FR->"Filtres";AppLanguage.DE->"Filter";AppLanguage.ES->"Filtros";AppLanguage.PT_BR->"Filtros";AppLanguage.TR->"Filtre";AppLanguage.AR->"تصفية";AppLanguage.HI->"फ़िल्टर";AppLanguage.ID->"Filter";AppLanguage.JA->"フィルター";AppLanguage.KO->"필터";else->"Filters"}
private fun clearFilters(l:AppLanguage)=when(l){AppLanguage.UZ->"Filtrlarni tozalash";AppLanguage.RU->"Сбросить фильтры";else->"Clear filters"}
private fun moodFilterLabel(l:AppLanguage)=when(l){AppLanguage.UZ->"Kayfiyat";AppLanguage.RU->"Настроение";else->"Mood"}
private fun mediaFilterLabel(l:AppLanguage)=when(l){AppLanguage.UZ->"Media";AppLanguage.RU->"Медиа";else->"Media"}
private fun countLabel(l:AppLanguage,n:Int)=when(l){AppLanguage.UZ->"$n yozuv";AppLanguage.RU->"Записей: $n";else->"$n notes"}
private fun foundLabel(l:AppLanguage,n:Int)=when(l){AppLanguage.UZ->"$n ta topildi";AppLanguage.RU->"Найдено: $n";else->"$n found"}
private fun emptyNotes(l:AppLanguage)=when(l){AppLanguage.UZ->"Hali yozuv yo‘q. Markazdagi + orqali birinchi xotirangizni yozing.";AppLanguage.RU->"Пока нет записей. Создайте первую через +.";else->"No notes yet. Use + to capture your first memory."}
private fun noMatches(l:AppLanguage)=when(l){AppLanguage.UZ->"Mos yozuv topilmadi";AppLanguage.RU->"Подходящих записей нет";else->"No matching notes"}
private fun mediaName(l:AppLanguage,m:MediaFilter)=when(m){MediaFilter.ANY->if(l==AppLanguage.UZ)"Barchasi" else "Any";MediaFilter.IMAGE->if(l==AppLanguage.UZ)"Rasm" else "Image";MediaFilter.AUDIO->if(l==AppLanguage.UZ)"Audio" else "Audio";MediaFilter.ANY_MEDIA->if(l==AppLanguage.UZ)"Media bor" else "Has media";MediaFilter.NO_MEDIA->if(l==AppLanguage.UZ)"Mediasiz" else "No media"}
private fun moodName(l:AppLanguage,m:NoteMood)=when(m){NoteMood.GREAT->if(l==AppLanguage.UZ)"Zo‘r" else "Great";NoteMood.GOOD->if(l==AppLanguage.UZ)"Yaxshi" else "Good";NoteMood.NEUTRAL->if(l==AppLanguage.UZ)"O‘rtacha" else "Neutral";NoteMood.BAD->if(l==AppLanguage.UZ)"Yomon" else "Bad";NoteMood.VERY_BAD->if(l==AppLanguage.UZ)"Juda yomon" else "Very bad"}
private fun dateFilterLabel(l:AppLanguage)=if(l==AppLanguage.UZ) "Sana oralig‘i" else "Date range"
private fun fromLabel(l:AppLanguage)=if(l==AppLanguage.UZ) "Boshlanish" else "From"
private fun toLabel(l:AppLanguage)=if(l==AppLanguage.UZ) "Tugash" else "To"
private fun clearDateLabel(l:AppLanguage)=if(l==AppLanguage.UZ) "Sanani tozalash" else "Clear dates"
private fun deleteNoteTitle(l:AppLanguage)=if(l==AppLanguage.UZ) "Yozuvni o‘chirish" else "Delete note"
private fun deleteNoteText(l:AppLanguage)=if(l==AppLanguage.UZ) "Bu yozuv va unga tegishli media o‘chiriladi." else "This note and its attached media will be deleted."
private fun cancelNotesLabel(l:AppLanguage)=if(l==AppLanguage.UZ) "Bekor qilish" else "Cancel"

@Composable
private fun notesSelectedContainer(): Color =
    MaterialTheme.colorScheme.primary.copy(alpha = if (MaterialTheme.colorScheme.background.luminance() < .5f) .22f else .14f)

private fun dayLabel(date: LocalDate, language: AppLanguage): String {
    val now=LocalDate.now()
    if(date==now) return if(language==AppLanguage.UZ) "Bugun" else if(language==AppLanguage.RU) "Сегодня" else "Today"
    if(date==now.minusDays(1)) return if(language==AppLanguage.UZ) "Kecha" else if(language==AppLanguage.RU) "Вчера" else "Yesterday"
    return date.format(DateTimeFormatter.ofPattern(if(date.year==now.year) "d MMMM" else "d MMMM yyyy", java.util.Locale.forLanguageTag(language.tag)))
}

private fun filterResultsLabel(language: AppLanguage): String = when (language) {
    AppLanguage.UZ -> "Natijalarni ko‘rsatish"
    AppLanguage.RU -> "Показать результаты"
    AppLanguage.AR -> "عرض النتائج"
    AppLanguage.FR -> "Afficher les résultats"
    AppLanguage.DE -> "Ergebnisse anzeigen"
    AppLanguage.HI -> "परिणाम दिखाएँ"
    AppLanguage.ID -> "Tampilkan hasil"
    AppLanguage.JA -> "結果を表示"
    AppLanguage.KO -> "결과 보기"
    AppLanguage.PT_BR -> "Ver resultados"
    AppLanguage.ES -> "Mostrar resultados"
    AppLanguage.TR -> "Sonuçları göster"
    AppLanguage.EN -> "Show results"
}
