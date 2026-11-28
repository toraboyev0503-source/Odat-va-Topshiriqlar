package com.memora.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.memora.app.prefs.AppLanguage
import com.memora.app.ui.theme.MemoraDesign
import com.memora.app.ui.theme.MemoraMotion

enum class RootTab { HOME, NOTES, CALENDAR, SETTINGS }

@Composable
fun MemoraBottomBar(
    language: AppLanguage,
    selected: RootTab,
    onHome: () -> Unit,
    onNotes: () -> Unit,
    onAdd: () -> Unit,
    onCalendar: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MemoraDesign.bottomBarHorizontal, vertical = 10.dp)
            .semantics { testTagsAsResourceId = true },
        shape = RoundedCornerShape(MemoraDesign.radiusLarge),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.975f),
        shadowElevation = MemoraDesign.shadowRaised,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            RootItem(
                icon = Icons.Rounded.Home,
                label = bottomLabel(language, RootTab.HOME),
                selected = selected == RootTab.HOME,
                tag = "memora_tab_home",
                onClick = onHome
            )
            RootItem(
                icon = Icons.Rounded.MenuBook,
                label = bottomLabel(language, RootTab.NOTES),
                selected = selected == RootTab.NOTES,
                tag = "memora_tab_notes",
                onClick = onNotes
            )
            AddItem(onClick = onAdd)
            RootItem(
                icon = Icons.Rounded.CalendarMonth,
                label = bottomLabel(language, RootTab.CALENDAR),
                selected = selected == RootTab.CALENDAR,
                tag = "memora_tab_calendar",
                onClick = onCalendar
            )
            RootItem(
                icon = Icons.Rounded.Settings,
                label = bottomLabel(language, RootTab.SETTINGS),
                selected = selected == RootTab.SETTINGS,
                tag = "memora_tab_settings",
                onClick = onSettings
            )
        }
    }
}

@Composable
private fun RootItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = tween(MemoraMotion.Press, easing = MemoraMotion.EmphasizedDecelerate),
        label = "root-item-press"
    )
    val foreground by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(MemoraMotion.Fast, easing = MemoraMotion.EmphasizedDecelerate),
        label = "root-item-foreground"
    )
    val background by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.075f) else Color.Transparent,
        animationSpec = tween(MemoraMotion.Fast, easing = MemoraMotion.EmphasizedDecelerate),
        label = "root-item-background"
    )

    Surface(
        modifier = Modifier
            .testTag(tag)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            ),
        shape = RoundedCornerShape(MemoraDesign.radiusSmall),
        color = background,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = foreground,
                modifier = Modifier.size(23.dp)
            )
            Text(
                label,
                color = foreground,
                fontSize = 10.25.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun AddItem(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(MemoraMotion.Press, easing = MemoraMotion.EmphasizedDecelerate),
        label = "add-button-press"
    )

    Surface(
        modifier = Modifier
            .size(54.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            ),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = MemoraDesign.shadowRaised
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Rounded.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(30.dp)
            )
        }
    }
}

private fun bottomLabel(language: AppLanguage, tab: RootTab): String = when (language) {
    AppLanguage.UZ -> when (tab) {
        RootTab.HOME -> "Bosh sahifa"
        RootTab.NOTES -> "Yozuvlar"
        RootTab.CALENDAR -> "Taqvim"
        RootTab.SETTINGS -> "Sozlamalar"
    }
    AppLanguage.RU -> when (tab) {
        RootTab.HOME -> "Главная"
        RootTab.NOTES -> "Записи"
        RootTab.CALENDAR -> "Календарь"
        RootTab.SETTINGS -> "Настройки"
    }
    AppLanguage.AR -> when (tab) {
        RootTab.HOME -> "الرئيسية"
        RootTab.NOTES -> "الكتابات"
        RootTab.CALENDAR -> "التقويم"
        RootTab.SETTINGS -> "الإعدادات"
    }
    AppLanguage.FR -> when (tab) {
        RootTab.HOME -> "Accueil"
        RootTab.NOTES -> "Écrits"
        RootTab.CALENDAR -> "Calendrier"
        RootTab.SETTINGS -> "Réglages"
    }
    AppLanguage.DE -> when (tab) {
        RootTab.HOME -> "Start"
        RootTab.NOTES -> "Einträge"
        RootTab.CALENDAR -> "Kalender"
        RootTab.SETTINGS -> "Einstellungen"
    }
    AppLanguage.HI -> when (tab) {
        RootTab.HOME -> "होम"
        RootTab.NOTES -> "लेखन"
        RootTab.CALENDAR -> "कैलेंडर"
        RootTab.SETTINGS -> "सेटिंग्स"
    }
    AppLanguage.ID -> when (tab) {
        RootTab.HOME -> "Beranda"
        RootTab.NOTES -> "Catatan"
        RootTab.CALENDAR -> "Kalender"
        RootTab.SETTINGS -> "Setelan"
    }
    AppLanguage.JA -> when (tab) {
        RootTab.HOME -> "ホーム"
        RootTab.NOTES -> "記録"
        RootTab.CALENDAR -> "カレンダー"
        RootTab.SETTINGS -> "設定"
    }
    AppLanguage.KO -> when (tab) {
        RootTab.HOME -> "홈"
        RootTab.NOTES -> "기록"
        RootTab.CALENDAR -> "달력"
        RootTab.SETTINGS -> "설정"
    }
    AppLanguage.PT_BR -> when (tab) {
        RootTab.HOME -> "Início"
        RootTab.NOTES -> "Notas"
        RootTab.CALENDAR -> "Calendário"
        RootTab.SETTINGS -> "Ajustes"
    }
    AppLanguage.ES -> when (tab) {
        RootTab.HOME -> "Inicio"
        RootTab.NOTES -> "Notas"
        RootTab.CALENDAR -> "Calendario"
        RootTab.SETTINGS -> "Ajustes"
    }
    AppLanguage.TR -> when (tab) {
        RootTab.HOME -> "Ana sayfa"
        RootTab.NOTES -> "Yazılar"
        RootTab.CALENDAR -> "Takvim"
        RootTab.SETTINGS -> "Ayarlar"
    }
    AppLanguage.EN -> when (tab) {
        RootTab.HOME -> "Home"
        RootTab.NOTES -> "Notes"
        RootTab.CALENDAR -> "Calendar"
        RootTab.SETTINGS -> "Settings"
    }
}
