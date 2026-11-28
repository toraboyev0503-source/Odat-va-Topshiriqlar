package com.memora.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.memora.app.data.local.NoteEntity
import com.memora.app.data.local.NoteType
import com.memora.app.i18n.L
import com.memora.app.i18n.S
import com.memora.app.prefs.AppLanguage
import com.memora.app.ui.components.MemoraTopBar
import com.memora.app.ui.components.MemoraEmptyState
import com.memora.app.ui.components.NoteCard
import com.memora.app.util.TimeUtils
import com.memora.app.ui.theme.MemoraDesign
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter


data class StatsSnapshot(
    val todayShort: Int = 0,
    val todayLong: Int = 0,
    val allShort: Int,
    val allLong: Int,
    val monthShort: Int,
    val monthLong: Int,
    val yearShort: Int,
    val yearLong: Int
)

private enum class CalendarStage { SUMMARY, YEAR, MONTH, DAY_TYPE, DAY_NOTES }

@Composable
fun StatisticsScreen(
    stats: StatsSnapshot,
    allNotes: List<NoteEntity>,
    language: AppLanguage,
    onBack: () -> Unit,
    onOpenNote: (String) -> Unit,
    startAtCalendar: Boolean = false
) {
    var stage by rememberSaveable {
        mutableStateOf(if (startAtCalendar) CalendarStage.YEAR else CalendarStage.SUMMARY)
    }
    var year by rememberSaveable { mutableIntStateOf(LocalDate.now().year) }
    var month by rememberSaveable { mutableIntStateOf(LocalDate.now().monthValue) }
    var day by rememberSaveable { mutableIntStateOf(LocalDate.now().dayOfMonth) }
    var selectedType by rememberSaveable { mutableStateOf(NoteType.SHORT) }

    fun back() {
        stage = when (stage) {
            CalendarStage.SUMMARY -> { onBack(); CalendarStage.SUMMARY }
            CalendarStage.YEAR -> {
                if (startAtCalendar) {
                    onBack()
                    CalendarStage.YEAR
                } else CalendarStage.SUMMARY
            }
            CalendarStage.MONTH -> CalendarStage.YEAR
            CalendarStage.DAY_TYPE -> CalendarStage.MONTH
            CalendarStage.DAY_NOTES -> CalendarStage.DAY_TYPE
        }
    }

    // Android's system Back must follow the same year → month → day-type → notes hierarchy
    // as the toolbar arrow instead of popping the whole Statistics route to Home.
    BackHandler { back() }

    Column(Modifier.fillMaxSize()) {
        MemoraTopBar(
            title = if (stage == CalendarStage.SUMMARY) L.t(language, S.STATISTICS) else L.t(language, S.WRITING_CALENDAR),
            navigation = { IconButton(onClick = ::back) { Icon(Icons.Rounded.ArrowBack, contentDescription = null) } }
        )
        when (stage) {
            CalendarStage.SUMMARY -> StatsSummary(stats, language) { stage = CalendarStage.YEAR }
            CalendarStage.YEAR -> YearCalendar(
                selectedYear = year,
                notes = allNotes,
                language = language,
                onYear = { year = it },
                onMonth = { month = it; stage = CalendarStage.MONTH }
            )
            CalendarStage.MONTH -> MonthCalendar(
                year = year,
                month = month,
                notes = allNotes,
                language = language,
                onDay = { day = it; stage = CalendarStage.DAY_TYPE }
            )
            CalendarStage.DAY_TYPE -> DayTypeChooser(
                year = year,
                month = month,
                day = day,
                notes = allNotes,
                language = language,
                onType = { selectedType = it; stage = CalendarStage.DAY_NOTES }
            )
            CalendarStage.DAY_NOTES -> DayNotes(
                year = year,
                month = month,
                day = day,
                type = selectedType,
                notes = allNotes,
                language = language,
                onOpen = onOpenNote
            )
        }
    }
}

@Composable
private fun StatsSummary(stats: StatsSnapshot, language: AppLanguage, onCalendar: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 4.dp)
    ) {
        item { StatsCard(L.t(language, S.TODAY), stats.todayShort, stats.todayLong, language) }
        item { StatsCard(L.t(language, S.THIS_MONTH), stats.monthShort, stats.monthLong, language) }
        item { StatsCard(L.t(language, S.THIS_YEAR), stats.yearShort, stats.yearLong, language) }
        item { StatsCard(L.t(language, S.ALL_TIME), stats.allShort, stats.allLong, language) }
        item {
            Surface(
                onClick = onCalendar,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 22.dp),
                shape = RoundedCornerShape(MemoraDesign.radiusMedium),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.09f)
            ) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(L.t(language, S.WRITING_CALENDAR), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Text("→", style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}

@Composable
private fun StatsCard(title: String, short: Int, long: Int, language: AppLanguage) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        shape = RoundedCornerShape(MemoraDesign.radiusMedium),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(short.toString(), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Medium)
                    Text(L.t(language, S.SHORT), style = MaterialTheme.typography.bodyMedium)
                }
                Column(Modifier.weight(1f)) {
                    Text(long.toString(), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Medium)
                    Text(L.t(language, S.LONG), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun YearCalendar(
    selectedYear: Int,
    notes: List<NoteEntity>,
    language: AppLanguage,
    onYear: (Int) -> Unit,
    onMonth: (Int) -> Unit
) {
    val years = remember(notes) {
        (notes.map { epochDate(it.createdAt).year } + LocalDate.now().year).distinct().sortedDescending()
    }
    var yearMenu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Box {
            Row(
                Modifier.clickable { yearMenu = true }.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(selectedYear.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null)
            }
            DropdownMenu(expanded = yearMenu, onDismissRequest = { yearMenu = false }) {
                years.forEach { y -> DropdownMenuItem(text = { Text(y.toString()) }, onClick = { onYear(y); yearMenu = false }) }
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 22.dp)
        ) {
            items((1..12).toList(), key = { it }) { m ->
                MonthListCard(
                    year = selectedYear,
                    month = m,
                    notes = notes,
                    language = language,
                    onClick = { onMonth(m) }
                )
            }
        }
    }
}


@Composable
private fun MonthListCard(
    year: Int,
    month: Int,
    notes: List<NoteEntity>,
    language: AppLanguage,
    onClick: () -> Unit
) {
    val monthNotes = notes.filter { val d = epochDate(it.createdAt); d.year == year && d.monthValue == month }
    val short = monthNotes.count { it.type == NoteType.SHORT.name }
    val long = monthNotes.count { it.type == NoteType.LONG.name }
    val now = LocalDate.now()
    val currentMonth = year == now.year && month == now.monthValue
    val locale = TimeUtils.locale(language)
    val shortLabel = L.t(language, S.SHORT).lowercase(locale)
    val longLabel = L.t(language, S.LONG).lowercase(locale)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(MemoraDesign.radiusMedium),
        color = if (currentMonth) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            0.6.dp,
            if (currentMonth) MaterialTheme.colorScheme.primary.copy(alpha = 0.40f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.50f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    monthLabel(year, month, language),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (currentMonth) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "$shortLabel: $short   $longLabel: $long",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Text(
                "→",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MonthCalendar(
    year: Int,
    month: Int,
    notes: List<NoteEntity>,
    language: AppLanguage,
    onDay: (Int) -> Unit
) {
    val ym = YearMonth.of(year, month)
    val monthNotes = notes.filter { val d = epochDate(it.createdAt); d.year == year && d.monthValue == month }
    val short = monthNotes.count { it.type == NoteType.SHORT.name }
    val long = monthNotes.count { it.type == NoteType.LONG.name }
    val firstOffset = ym.atDay(1).dayOfWeek.value - 1
    val cells = List(firstOffset) { 0 } + (1..ym.lengthOfMonth()).toList()
    val today = LocalDate.now()

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(monthLabel(year, month, language), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text("$short / $long", style = MaterialTheme.typography.titleMedium)
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            weekdayLabels(language).forEach { label -> Text(label, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        LazyVerticalGrid(columns = GridCells.Fixed(7), modifier = Modifier.fillMaxSize()) {
            items(cells) { d ->
                if (d == 0) { Spacer(Modifier.height(64.dp)) }
                else {
                    val dayNotes = monthNotes.filter { epochDate(it.createdAt).dayOfMonth == d }
                    val ds = dayNotes.count { it.type == NoteType.SHORT.name }
                    val dl = dayNotes.count { it.type == NoteType.LONG.name }
                    val current = today.year == year && today.monthValue == month && today.dayOfMonth == d
                    Surface(
                        onClick = { onDay(d) },
                        shape = RoundedCornerShape(MemoraDesign.radiusSmall),
                        color = if (current) MaterialTheme.colorScheme.primary.copy(alpha = 0.13f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                        modifier = Modifier.padding(2.dp)
                    ) {
                        Column(Modifier.padding(vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(shape = CircleShape, color = if (current) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent) {
                                Text(
                                    d.toString(),
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                    color = if (current) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text("$ds / $dl", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayTypeChooser(
    year: Int,
    month: Int,
    day: Int,
    notes: List<NoteEntity>,
    language: AppLanguage,
    onType: (NoteType) -> Unit
) {
    val date = LocalDate.of(year, month, day)
    val dayNotes = notes.filter { epochDate(it.createdAt) == date }
    val short = dayNotes.count { it.type == NoteType.SHORT.name }
    val long = dayNotes.count { it.type == NoteType.LONG.name }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text(date.format(DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.LONG).withLocale(TimeUtils.locale(language))), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 18.dp))
        TypeCard(L.t(language, S.SHORT_NOTES), short) { onType(NoteType.SHORT) }
        Spacer(Modifier.height(12.dp))
        TypeCard(L.t(language, S.LONG_NOTES), long) { onType(NoteType.LONG) }
    }
}

@Composable
private fun TypeCard(title: String, count: Int, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(MemoraDesign.radiusMedium), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(count.toString(), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DayNotes(
    year: Int,
    month: Int,
    day: Int,
    type: NoteType,
    notes: List<NoteEntity>,
    language: AppLanguage,
    onOpen: (String) -> Unit
) {
    val date = LocalDate.of(year, month, day)
    val filtered = notes.filter { epochDate(it.createdAt) == date && it.type == type.name }.sortedByDescending { it.createdAt }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Text(L.t(language, if (type == NoteType.SHORT) S.SHORT_NOTES else S.LONG_NOTES), style = MaterialTheme.typography.titleLarge) }
        if (filtered.isEmpty()) item {
            MemoraEmptyState(
                title = L.t(language, S.NO_NOTES),
                body = if (language == AppLanguage.UZ) "Tanlangan sana va tur bo‘yicha yozuv topilmadi." else "No notes were found for the selected date and type."
            )
        }
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
    }
}

private fun epochDate(epoch: Long): LocalDate = Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).toLocalDate()
private fun monthLabel(year: Int, month: Int, language: AppLanguage): String =
    YearMonth.of(year, month).format(DateTimeFormatter.ofPattern("LLLL", TimeUtils.locale(language))).uppercase(TimeUtils.locale(language))
private fun weekdayLabels(language: AppLanguage): List<String> {
    val locale = TimeUtils.locale(language)
    return (1..7).map { java.time.DayOfWeek.of(it).getDisplayName(java.time.format.TextStyle.NARROW, locale) }
}
