package com.memora.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.aspectRatio
import com.memora.app.data.local.TreeVisualCounts
import com.memora.app.data.local.TreeVisibleLeaf
import com.memora.app.ui.components.MemoryTreeScene
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.memora.app.i18n.L
import com.memora.app.i18n.S
import com.memora.app.prefs.AppLanguage
import com.memora.app.ui.components.MemoraBrandBackdrop
import com.memora.app.ui.theme.MemoraDesign
import com.memora.app.data.local.NoteEntity
import com.memora.app.memories.MemoryText
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.rounded.History
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(
    language: AppLanguage,
    shortCount: Int,
    longCount: Int,
    isDark: Boolean,
    uiScale: Float,
    onToggleDark: () -> Unit,
    onShort: () -> Unit,
    onLong: () -> Unit,
    timeTraceTodaySaved: Boolean,
    timeTraceStreak: Int,
    onTimeTrace: () -> Unit,
    treeCounts: TreeVisualCounts? = null,
    treeLeaves: List<TreeVisibleLeaf>? = null,
    onTreeLeaf: ((TreeVisibleLeaf) -> Unit)? = null,
    onTreeTreasure: (() -> Unit)? = null
) {
    val inheritedDensity = LocalDensity.current
    val safeScale = uiScale.coerceIn(0.8f, 1.5f)
    val homeScale = safeScale.coerceAtMost(1.2f)
    val homeDensity = Density(inheritedDensity.density * (homeScale / safeScale), inheritedDensity.fontScale)

    CompositionLocalProvider(LocalDensity provides homeDensity) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            MemoraBrandBackdrop(
                modifier = Modifier.fillMaxWidth().height(500.dp).align(Alignment.TopCenter),
                primaryColor = MaterialTheme.colorScheme.primary,
                secondaryColor = MaterialTheme.colorScheme.surfaceVariant,
                strength = if (isDark) 0.26f else 0.38f
            )
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                HomeHeader(language, isDark, onToggleDark)
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        HomeNoteCompactCard(L.t(language, S.SHORT), shortCount, Icons.Rounded.Description, onShort, Modifier.weight(1f))
                        HomeNoteCompactCard(L.t(language, S.LONG), longCount, Icons.Rounded.MenuBook, onLong, Modifier.weight(1f))
                    }
                    TimeTraceHomeCard(language, timeTraceTodaySaved, timeTraceStreak, onTimeTrace)

                }
                Spacer(Modifier.height(12.dp))
                // Keep the first real counts stable; never flash a fictitious zero tree.
                treeCounts?.let { counts ->
                    if (treeLeaves != null) MemoryTreeScene(
                        counts = counts, visibleLeaves = treeLeaves, dark = isDark,
                        uzbek = language == AppLanguage.UZ,
                        onLeaf = onTreeLeaf, onTreasure = onTreeTreasure,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp).aspectRatio(0.88f)
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun HomeHeader(language: AppLanguage, isDark: Boolean, onToggleDark: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            L.t(language, S.HOME_TAGLINE),
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = .88f), shadowElevation = MemoraDesign.shadowSubtle) {
            IconButton(onClick = onToggleDark, modifier = Modifier.size(46.dp)) {
                Icon(if (isDark) Icons.Rounded.DarkMode else Icons.Rounded.LightMode, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun HomeNoteCompactCard(
    title: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier.clickable(onClick = onClick), shape = RoundedCornerShape(MemoraDesign.radiusMedium), color = MaterialTheme.colorScheme.surface.copy(alpha = .985f), shadowElevation = MemoraDesign.shadowSubtle) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 17.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Text(title, style = MaterialTheme.typography.titleLarge.copy(fontSize = 21.sp), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
            Text(count.toString(), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun TimeTraceHomeCard(language: AppLanguage, todaySaved: Boolean, streak: Int, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(MemoraDesign.radiusMedium), color = MaterialTheme.colorScheme.surface.copy(alpha = .97f), shadowElevation = MemoraDesign.shadowSubtle) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = .10f), modifier = Modifier.size(46.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(if (todaySaved) Icons.Rounded.CheckCircle else Icons.Rounded.PhotoCamera, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp)) }
            }
            Column(Modifier.weight(1f).padding(start = 13.dp)) {
                Text(if (language == AppLanguage.UZ) "Kun izi" else "Day Trace", style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp), fontWeight = FontWeight.SemiBold)
                Text(
                    if (todaySaved) if (language == AppLanguage.UZ) "✓ Bugungi lahza saqlandi" else "✓ Today’s photo is saved"
                    else if (language == AppLanguage.UZ) "Bugundan lahza saqlash" else "Capture today’s moment",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (streak > 0) Text(if (language == AppLanguage.UZ) "$streak surat" else "$streak photos", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun DailyReviewHomeCard(language: AppLanguage, count: Int, completed: Boolean, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(MemoraDesign.radiusMedium), color = MaterialTheme.colorScheme.surface.copy(alpha = .985f), shadowElevation = MemoraDesign.shadowSubtle) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = .11f), modifier = Modifier.size(46.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(if (completed) Icons.Rounded.CheckCircle else Icons.Rounded.AutoStories, null, tint = MaterialTheme.colorScheme.primary) }
            }
            Column(Modifier.weight(1f).padding(start = 13.dp)) {
                Text(if (language == AppLanguage.UZ) "Bugungi qayta ko‘rish" else "Today’s review", style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp), fontWeight = FontWeight.SemiBold)
                Text(
                    when {
                        completed -> if (language == AppLanguage.UZ) "Tugallandi ✓" else "Completed ✓"
                        count <= 0 -> if (language == AppLanguage.UZ) "Hozircha topilma yo‘q" else "Nothing ready yet"
                        else -> if (language == AppLanguage.UZ) "$count ta topilma tayyor" else "$count finds are ready"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Rounded.ArrowForward, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}


@Composable
private fun NostalgiaMemoryCard(
    note: NoteEntity,
    language: AppLanguage,
    onRead: () -> Unit,
    onDismiss: () -> Unit,
    onLater: () -> Unit
) {
    val original = Instant.ofEpochMilli(note.createdAt).atZone(ZoneId.systemDefault())
    val time = original.format(DateTimeFormatter.ofPattern("dd.MM.yyyy · HH:mm"))
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MemoraDesign.radiusLarge),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = MemoraDesign.shadowSubtle
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.History, null, tint = MaterialTheme.colorScheme.primary)
                Text(MemoryText.get(language, MemoryText.Key.MEMORY),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 9.dp))
            }
            Text(note.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
            Text(note.plainText, maxLines = 2, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (note.hasAudio || note.hasImages) Text((if (note.hasImages) "▧ " else "") + (if (note.hasAudio) "♪" else ""),
                color = MaterialTheme.colorScheme.primary)
            Text(if (language == AppLanguage.UZ) "Bu yozuvni $time da saqlagansiz" else "Written on $time",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onDismiss) { Text(MemoryText.get(language, MemoryText.Key.SKIP), maxLines = 1) }
                TextButton(onClick = onLater) { Text(MemoryText.get(language, MemoryText.Key.LATER), maxLines = 1) }
                TextButton(onClick = onRead) { Text(MemoryText.get(language, MemoryText.Key.READ), maxLines = 1) }
            }
        }
    }
}
