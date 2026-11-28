package com.memora.app.ui.screens

import com.memora.app.ui.components.MemoraSheet
import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.memora.app.data.local.NoteSummary
import com.memora.app.data.local.NoteType
import com.memora.app.data.model.NoteMood
import com.memora.app.prefs.AppLanguage
import com.memora.app.ui.components.MoodSticker
import com.memora.app.ui.components.MemoraEmptyState
import com.memora.app.ui.components.NoteCard
import com.memora.app.ui.theme.MemoraDesign
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private enum class CalendarMode { CALENDAR, STATS }
private enum class DayKind { ALL, SHORT, LONG }
private enum class StatsPeriod { MONTH, THREE, SIX, YEAR, ALL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarHubScreen(
    notes: List<NoteSummary>,
    language: AppLanguage,
    onOpenNote: (String) -> Unit
) {
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    var mode by remember { mutableStateOf(CalendarMode.CALENDAR) }
    var selectedDay by remember { mutableStateOf<LocalDate?>(null) }
    var drag by remember { mutableFloatStateOf(0f) }
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = MemoraDesign.screenPadding, vertical = MemoraDesign.screenPadding)
    ) {
        Text(calendarTitle(language), style = MaterialTheme.typography.headlineLarge.copy(fontSize = 38.sp), fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = mode == CalendarMode.CALENDAR,
                onClick = { mode = CalendarMode.CALENDAR },
                label = { Text(calendarLabel(language)) },
                leadingIcon = { Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp)) },
                colors = calendarChipColors()
            )
            FilterChip(
                selected = mode == CalendarMode.STATS,
                onClick = { mode = CalendarMode.STATS },
                label = { Text(statsLabel(language)) },
                leadingIcon = { Icon(Icons.Rounded.BarChart, contentDescription = null, modifier = Modifier.size(18.dp)) },
                colors = calendarChipColors()
            )
        }
        Spacer(Modifier.height(12.dp))

        if (mode == CalendarMode.CALENDAR) {
            MonthHeader(month, language,
                onPrevious = { month = month.minusMonths(1) },
                onNext = { month = month.plusMonths(1) },
                onToday = { month = YearMonth.from(today); selectedDay = today },
                onPick = {
                    DatePickerDialog(
                        context,
                        { _, year, monthIndex, _ -> month = YearMonth.of(year, monthIndex + 1) },
                        month.year, month.monthValue - 1, 1
                    ).show()
                }
            )
            MonthGrid(
                month = month,
                notes = notes,
                language = language,
                today = today,
                selectedDay = selectedDay,
                onDay = { selectedDay = it },
                modifier = Modifier.pointerInput(month) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (drag > 70f) month = month.minusMonths(1)
                            if (drag < -70f) month = month.plusMonths(1)
                            drag = 0f
                        },
                        onHorizontalDrag = { _, amount -> drag += amount }
                    )
                }
            )
            val hasMonthNotes = remember(notes, month, zone) {
                notes.any { YearMonth.from(Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate()) == month }
            }
            if (!hasMonthNotes) {
                Text(
                    if (language == AppLanguage.UZ) "Bu oyda hali yozuv yo‘q" else "No notes in this month yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 14.dp)
                )
            }
        } else {
            StatisticsPanel(notes = notes, language = language, zone = zone)
        }
    }

    selectedDay?.let { day ->
        MemoraSheet(onDismissRequest = { selectedDay = null }) {
            DayNotesSheet(day, notes, language, onOpenNote)
        }
    }
}

@Composable
private fun MonthHeader(month: YearMonth, language: AppLanguage, onPrevious:()->Unit, onNext:()->Unit, onToday:()->Unit, onPick:()->Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) { Icon(Icons.Rounded.ArrowBackIosNew, contentDescription = null) }
        Text(
            month.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.forLanguageTag(language.tag))).replaceFirstChar { if(it.isLowerCase()) it.titlecase() else it.toString() },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).clickable(onClick = onPick)
        )
        IconButton(onClick = onNext) { Icon(Icons.Rounded.ArrowForwardIos, contentDescription = null) }
        IconButton(onClick = onToday) { Icon(Icons.Rounded.CalendarToday, contentDescription = todayLabel(language)) }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    notes: List<NoteSummary>,
    language: AppLanguage,
    today: LocalDate,
    selectedDay: LocalDate?,
    onDay: (LocalDate)->Unit,
    modifier: Modifier = Modifier
) {
    val zone = remember { ZoneId.systemDefault() }
    val dayCounts = remember(notes, month, zone) {
        notes.asSequence().map { Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate() }
            .filter { YearMonth.from(it) == month }
            .groupingBy { it }.eachCount()
    }
    val offset = month.atDay(1).dayOfWeek.value - 1
    val days = month.lengthOfMonth()
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            (1..7).forEach { d ->
                Text(
                    java.time.DayOfWeek.of(d).getDisplayName(TextStyle.NARROW, Locale.forLanguageTag(language.tag)),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        var cell = 0
        repeat(6) {
            Row(Modifier.fillMaxWidth()) {
                repeat(7) {
                    val dayNumber = cell - offset + 1
                    if (dayNumber !in 1..days) {
                        Spacer(Modifier.weight(1f).height(58.dp))
                    } else {
                        val date = month.atDay(dayNumber)
                        val count = dayCounts[date] ?: 0
                        DayCell(date, count, today, selectedDay, onDay, Modifier.weight(1f))
                    }
                    cell++
                }
            }
            if (cell-offset > days) return@repeat
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, count: Int, today: LocalDate, selectedDay: LocalDate?, onDay:(LocalDate)->Unit, modifier:Modifier=Modifier) {
    val isToday = date == today
    val isSelected = date == selectedDay
    Surface(
        onClick = { onDay(date) },
        modifier = modifier.padding(2.dp).height(58.dp),
        shape = RoundedCornerShape(MemoraDesign.radiusSmall),
        color = when {
            isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = .18f)
            isToday -> MaterialTheme.colorScheme.primary.copy(alpha=.08f)
            else -> MaterialTheme.colorScheme.surface.copy(alpha=.78f)
        }
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(
                Modifier.size(28.dp).background(if(isSelected || (isToday && selectedDay == null)) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(date.dayOfMonth.toString(), color = if(isSelected || (isToday && selectedDay == null)) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
            }
            if (count > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top=4.dp)) {
                    repeat(minOf(count, 3)) { Box(Modifier.size(4.dp).background(MaterialTheme.colorScheme.primary, CircleShape)) }
                }
            }
        }
    }
}

@Composable
private fun DayNotesSheet(day: LocalDate, notes: List<NoteSummary>, language: AppLanguage, onOpen:(String)->Unit) {
    val zone = remember { ZoneId.systemDefault() }
    var kind by remember(day) { mutableStateOf(DayKind.ALL) }
    val dayNotes = remember(notes, day, kind, zone) {
        notes.filter { Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate() == day }
            .filter { kind==DayKind.ALL || (kind==DayKind.SHORT && it.type==NoteType.SHORT.name) || (kind==DayKind.LONG && it.type==NoteType.LONG.name) }
            .sortedByDescending { it.createdAt }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal=22.dp)) {
        Text(day.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag(language.tag))), style=MaterialTheme.typography.headlineSmall, fontWeight=FontWeight.SemiBold)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp), modifier=Modifier.padding(vertical=10.dp)) {
            FilterChip(kind==DayKind.ALL,{kind=DayKind.ALL},{Text(allLabelCal(language))})
            FilterChip(kind==DayKind.SHORT,{kind=DayKind.SHORT},{Text(shortLabelCal(language))})
            FilterChip(kind==DayKind.LONG,{kind=DayKind.LONG},{Text(longLabelCal(language))})
        }
        LazyColumn(contentPadding=androidx.compose.foundation.layout.PaddingValues(bottom=28.dp)) {
            if(dayNotes.isEmpty()) item {
                MemoraEmptyState(
                    title = noNotesDay(language),
                    body = if (language == AppLanguage.UZ) "Bu sana uchun saqlangan xotira yo‘q." else "There are no saved memories for this date.",
                    modifier = Modifier.padding(vertical = 20.dp)
                )
            }
            items(dayNotes,key={it.id}) { note ->
                NoteCard(note.asCardEntity(),note.title,language,false,onClick={onOpen(note.id)},onLongClick={},imageCount=note.imageCount,audioCount=note.audioCount)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun StatisticsPanel(notes: List<NoteSummary>, language: AppLanguage, zone: ZoneId) {
    if (notes.isEmpty()) {
        MemoraEmptyState(
            title = if (language == AppLanguage.UZ) "Statistika hali yo‘q" else "No statistics yet",
            body = if (language == AppLanguage.UZ) "Yozishni boshlaganingizdan keyin rivojlanish ko‘rsatkichlari shu yerda paydo bo‘ladi." else "Your progress statistics will appear here after you start writing.",
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 24.dp)
        )
        return
    }
    var period by remember { mutableStateOf(StatsPeriod.MONTH) }
    val today = LocalDate.now(zone)
    val filtered = remember(notes, period, today) {
        notes.filter { note ->
            val d = Instant.ofEpochMilli(note.createdAt).atZone(zone).toLocalDate()
            when(period) {
                StatsPeriod.MONTH -> !d.isBefore(today.minusMonths(1))
                StatsPeriod.THREE -> !d.isBefore(today.minusMonths(3))
                StatsPeriod.SIX -> !d.isBefore(today.minusMonths(6))
                StatsPeriod.YEAR -> !d.isBefore(today.minusYears(1))
                StatsPeriod.ALL -> true
            }
        }
    }
    val writingDays = filtered.map { Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate() }.distinct().sorted()
    val streak = longestStreak(writingDays)
    val short = filtered.count { it.type == NoteType.SHORT.name }
    val long = filtered.size-short
    val media = filtered.count { it.hasImages || it.hasAudio }
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatsPeriod.entries.forEach { p ->
                FilterChip(
                    selected = period == p,
                    onClick = { period = p },
                    label = { Text(statsPeriodName(language, p)) },
                    colors = calendarChipColors()
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(totalLabel(language), filtered.size.toString(), Icons.Rounded.ReceiptLong, Color(0xFFDDEAF6), Modifier.weight(1f))
            StatCard(daysLabel(language), writingDays.size.toString(), Icons.Rounded.CalendarMonth, Color(0xFFE4EFE8), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(streakLabel(language), streak.toString(), Icons.Rounded.LocalFireDepartment, Color(0xFFF3EBDD), Modifier.weight(1f))
            StatCard(shortLabelCal(language), short.toString(), Icons.Rounded.Description, Color(0xFFDCE8E5), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(longLabelCal(language), long.toString(), Icons.Rounded.MenuBook, Color(0xFFDDEBF0), Modifier.weight(1f))
            StatCard(mediaLabel(language), media.toString(), Icons.Rounded.Image, Color(0xFFE2EEE7), Modifier.weight(1f))
        }
        Text(
            moodStatsLabel(language),
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 22.dp, bottom = 10.dp)
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            NoteMood.entries.forEach { m ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    MoodSticker(m, moodNameCal(language, m), Modifier.size(44.dp))
                    Text(
                        moodNameCal(language, m),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Text(
                        filtered.count { it.mood == m.name }.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBackground: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier,
        shape = RoundedCornerShape(MemoraDesign.radiusMedium),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = MemoraDesign.shadowSubtle
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = if (MaterialTheme.colorScheme.background.luminance() < .5f) MaterialTheme.colorScheme.surfaceVariant else iconBackground,
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(23.dp))
                }
            }
            Column(Modifier.padding(start = 12.dp)) {
                Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun calendarChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = Color.Transparent,
    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = if (MaterialTheme.colorScheme.background.luminance() < .5f) .22f else .14f),
    selectedLabelColor = MaterialTheme.colorScheme.onSurface,
    selectedLeadingIconColor = MaterialTheme.colorScheme.primary
)
private fun longestStreak(days:List<LocalDate>):Int{if(days.isEmpty())return 0;var best=1;var cur=1;for(i in 1 until days.size){if(days[i-1].plusDays(1)==days[i]){cur++;best=maxOf(best,cur)}else cur=1};return best}
private fun calendarTitle(l:AppLanguage)=if(l==AppLanguage.UZ)"Xotiralar" else if(l==AppLanguage.RU)"История" else "Memories"
private fun calendarLabel(l:AppLanguage)=if(l==AppLanguage.UZ)"Taqvim" else if(l==AppLanguage.RU)"Календарь" else "Calendar"
private fun statsLabel(l:AppLanguage)=if(l==AppLanguage.UZ)"Statistika" else if(l==AppLanguage.RU)"Статистика" else "Statistics"
private fun todayLabel(l:AppLanguage)=if(l==AppLanguage.UZ)"Bugun" else if(l==AppLanguage.RU)"Сегодня" else "Today"
private fun allLabelCal(l:AppLanguage)=if(l==AppLanguage.UZ)"Barchasi" else if(l==AppLanguage.RU)"Все" else "All"
private fun shortLabelCal(l:AppLanguage)=if(l==AppLanguage.UZ)"Qisqa" else if(l==AppLanguage.RU)"Короткие" else "Short"
private fun longLabelCal(l:AppLanguage)=if(l==AppLanguage.UZ)"Uzun" else if(l==AppLanguage.RU)"Длинные" else "Long"
private fun noNotesDay(l:AppLanguage)=if(l==AppLanguage.UZ)"Bu kunda yozuv yo‘q" else if(l==AppLanguage.RU)"В этот день записей нет" else "No notes on this day"
private fun totalLabel(l:AppLanguage)=if(l==AppLanguage.UZ)"Jami" else "Total"
private fun daysLabel(l:AppLanguage)=if(l==AppLanguage.UZ)"Yozilgan kun" else "Writing days"
private fun streakLabel(l:AppLanguage)=if(l==AppLanguage.UZ)"Ketma-ket" else "Streak"
private fun mediaLabel(l:AppLanguage)=if(l==AppLanguage.UZ)"Media" else "Media"
private fun moodStatsLabel(l:AppLanguage)=if(l==AppLanguage.UZ)"Kayfiyat taqsimoti" else "Mood distribution"
private fun moodNameCal(l:AppLanguage,m:NoteMood)=when(m){NoteMood.GREAT->if(l==AppLanguage.UZ)"Zo‘r" else "Great";NoteMood.GOOD->if(l==AppLanguage.UZ)"Yaxshi" else "Good";NoteMood.NEUTRAL->if(l==AppLanguage.UZ)"O‘rtacha" else "Neutral";NoteMood.BAD->if(l==AppLanguage.UZ)"Yomon" else "Bad";NoteMood.VERY_BAD->if(l==AppLanguage.UZ)"Juda yomon" else "Very bad"}
private fun statsPeriodName(l:AppLanguage,p:StatsPeriod)=when(p){StatsPeriod.MONTH->if(l==AppLanguage.UZ)"Oy" else "Month";StatsPeriod.THREE->"3";StatsPeriod.SIX->"6";StatsPeriod.YEAR->if(l==AppLanguage.UZ)"1 yil" else "1y";StatsPeriod.ALL->if(l==AppLanguage.UZ)"Hammasi" else "All"}
