package com.memora.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.memora.app.i18n.L
import com.memora.app.i18n.S
import com.memora.app.prefs.AppLanguage
import com.memora.app.prefs.FontChoice
import com.memora.app.prefs.PaletteChoice
import com.memora.app.prefs.ThemeMode
import com.memora.app.prefs.UserSettings
import com.memora.app.ui.components.MemoraTopBar
import com.memora.app.ui.theme.fontFamily
import com.memora.app.ui.theme.palette
import kotlin.math.roundToInt

@Composable
fun ThemeScreen(
    settings: UserSettings,
    onBack: () -> Unit,
    onPalette: (PaletteChoice) -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onUiFont: (FontChoice) -> Unit,
    onWritingFont: (FontChoice) -> Unit,
    onUiScale: (Float) -> Unit
) {
    val language = settings.language
    var scalePreview by remember(settings.uiScale) { mutableFloatStateOf(settings.uiScale) }
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    Column(Modifier.fillMaxSize()) {
        MemoraTopBar(
            title = L.t(language, S.THEME),
            navigation = { IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = null) } }
        )
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp)
        ) {
            SettingsSectionTitle(themeModeLabel(language))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEach { mode ->
                    ModePreview(
                        mode = mode,
                        title = themeModeName(language, mode),
                        selected = settings.themeMode == mode,
                        modifier = Modifier.weight(1f),
                        onClick = { onThemeMode(mode) }
                    )
                }
            }
            SettingsSectionTitle(L.t(language, S.BACKGROUND))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                items(PaletteChoice.entries, key = { it.name }) { choice ->
                    PalettePreview(
                        choice = choice,
                        title = L.t(language, when (choice) {
                            PaletteChoice.PAPER -> S.PAPER
                            PaletteChoice.IVORY -> S.IVORY
                            PaletteChoice.MIST -> S.MIST
                            PaletteChoice.SAGE -> S.SAGE
                            PaletteChoice.SLATE -> S.SLATE
                        }),
                        selected = settings.palette == choice,
                        dark = dark,
                        onClick = { onPalette(choice) }
                    )
                }
            }
            SettingsSectionTitle(L.t(language, S.SCALE))
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${(scalePreview * 100).roundToInt()}%", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { scalePreview = 1f; onUiScale(1f) }) {
                            Text(resetLabel(language))
                        }
                    }
                    Slider(
                        value = scalePreview,
                        onValueChange = { scalePreview = it },
                        onValueChangeFinished = { onUiScale(scalePreview) },
                        valueRange = .8f..1.5f,
                        steps = 13
                    )
                }
            }
            SettingsSectionTitle(L.t(language, S.UI_FONT))
            FontPreviews(FontChoice.entries, settings.uiFont, language, onUiFont)
            SettingsSectionTitle(L.t(language, S.WRITING_FONT))
            FontPreviews(FontChoice.entries, settings.writingFont, language, onWritingFont)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsSectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 22.dp, bottom = 10.dp))
}

@Composable
private fun ModePreview(mode: ThemeMode, title: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) cs.surfaceVariant else cs.surface,
        border = BorderStroke(1.dp, if (selected) cs.primary else cs.outlineVariant),
        tonalElevation = 0.dp
    ) {
        Column(Modifier.fillMaxWidth().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val paper = when (mode) { ThemeMode.DARK -> Color(0xFF292C2D); else -> Color(0xFFF7F0E6) }
            val ink = when (mode) { ThemeMode.DARK -> Color(0xFFDFDFDB); else -> Color(0xFF42545A) }
            Row(
                Modifier.fillMaxWidth().height(45.dp).background(paper, RoundedCornerShape(10.dp))
                    .padding(9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                when (mode) {
                    ThemeMode.SYSTEM -> {
                        Box(Modifier.size(15.dp).background(Color(0xFF637E83), RoundedCornerShape(5.dp)))
                        Spacer(Modifier.width(5.dp))
                        Box(Modifier.size(15.dp).background(Color(0xFF292C2D), RoundedCornerShape(5.dp)))
                    }
                    ThemeMode.LIGHT -> Icon(Icons.Rounded.LightMode, contentDescription = null, tint = ink, modifier = Modifier.size(21.dp))
                    ThemeMode.DARK -> Icon(Icons.Rounded.DarkMode, contentDescription = null, tint = ink, modifier = Modifier.size(21.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}

@Composable
private fun PalettePreview(choice: PaletteChoice, title: String, selected: Boolean, dark: Boolean, onClick: () -> Unit) {
    val p = palette(choice, dark)
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = Modifier.width(94.dp),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) cs.surfaceVariant else cs.surface,
        border = BorderStroke(1.dp, if (selected) cs.primary else cs.outlineVariant),
        tonalElevation = 0.dp
    ) {
        Column(Modifier.padding(7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().height(57.dp).background(p.background, RoundedCornerShape(10.dp)).padding(7.dp)) {
                Column(Modifier.fillMaxSize().background(p.surface, RoundedCornerShape(6.dp)).padding(6.dp)) {
                    Box(Modifier.fillMaxWidth(.8f).height(5.dp).background(p.accent, RoundedCornerShape(3.dp)))
                    Spacer(Modifier.height(5.dp))
                    Box(Modifier.fillMaxWidth(.65f).height(4.dp).background(p.muted.copy(alpha = .5f), RoundedCornerShape(3.dp)))
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.fillMaxWidth(.8f).height(4.dp).background(p.muted.copy(alpha = .3f), RoundedCornerShape(3.dp)))
                }
            }
            Spacer(Modifier.height(7.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selected) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = cs.primary, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(3.dp))
                }
                Text(title, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}

@Composable
private fun FontPreviews(values: List<FontChoice>, selected: FontChoice, language: AppLanguage, onSelect: (FontChoice) -> Unit) {
    val cs = MaterialTheme.colorScheme
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(values, key = { it.name }) { choice ->
            Surface(
                onClick = { onSelect(choice) },
                modifier = Modifier.width(114.dp),
                shape = RoundedCornerShape(14.dp),
                color = if (selected == choice) cs.surfaceVariant else cs.surface,
                border = BorderStroke(1.dp, if (selected == choice) cs.primary else cs.outlineVariant)
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 11.dp)) {
                    Text("Aa", style = MaterialTheme.typography.headlineMedium.copy(fontFamily = fontFamily(choice)))
                    Spacer(Modifier.height(5.dp))
                    Text(L.t(language, when (choice) {
                        FontChoice.SANS -> S.SANS
                        FontChoice.SERIF -> S.SERIF
                        FontChoice.MONO -> S.MONO
                        FontChoice.CURSIVE -> S.CURSIVE
                    }), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
            }
        }
    }
}

private fun resetLabel(language: AppLanguage): String = when (language) {
    AppLanguage.UZ -> "Standart"
    AppLanguage.RU -> "Сброс"
    AppLanguage.AR -> "إعادة الضبط"
    AppLanguage.FR -> "Réinitialiser"
    AppLanguage.DE -> "Zurücksetzen"
    AppLanguage.HI -> "रीसेट"
    AppLanguage.ID -> "Atur ulang"
    AppLanguage.JA -> "リセット"
    AppLanguage.KO -> "초기화"
    AppLanguage.PT_BR -> "Redefinir"
    AppLanguage.ES -> "Restablecer"
    AppLanguage.TR -> "Sıfırla"
    AppLanguage.EN -> "Reset"
}

private fun themeModeLabel(language: AppLanguage): String = when (language) {
    AppLanguage.UZ -> "Ko‘rinish rejimi"
    AppLanguage.RU -> "Режим оформления"
    AppLanguage.AR -> "وضع المظهر"
    AppLanguage.FR -> "Mode d’affichage"
    AppLanguage.DE -> "Darstellung"
    AppLanguage.HI -> "दिखावट"
    AppLanguage.ID -> "Tampilan"
    AppLanguage.JA -> "表示モード"
    AppLanguage.KO -> "화면 모드"
    AppLanguage.PT_BR -> "Aparência"
    AppLanguage.ES -> "Apariencia"
    AppLanguage.TR -> "Görünüm"
    AppLanguage.EN -> "Appearance"
}

private fun themeModeName(language: AppLanguage, mode: ThemeMode): String {
    val labels = when (language) {
        AppLanguage.UZ -> listOf("Tizim", "Yorug‘", "Tungi")
        AppLanguage.RU -> listOf("Система", "Светлая", "Тёмная")
        AppLanguage.AR -> listOf("النظام", "فاتح", "داكن")
        AppLanguage.FR -> listOf("Système", "Clair", "Sombre")
        AppLanguage.DE -> listOf("System", "Hell", "Dunkel")
        AppLanguage.HI -> listOf("सिस्टम", "हल्का", "गहरा")
        AppLanguage.ID -> listOf("Sistem", "Terang", "Gelap")
        AppLanguage.JA -> listOf("システム", "ライト", "ダーク")
        AppLanguage.KO -> listOf("시스템", "라이트", "다크")
        AppLanguage.PT_BR -> listOf("Sistema", "Claro", "Escuro")
        AppLanguage.ES -> listOf("Sistema", "Claro", "Oscuro")
        AppLanguage.TR -> listOf("Sistem", "Açık", "Koyu")
        AppLanguage.EN -> listOf("System", "Light", "Dark")
    }
    return labels[when (mode) {
        ThemeMode.SYSTEM -> 0
        ThemeMode.LIGHT -> 1
        ThemeMode.DARK -> 2
    }]
}
