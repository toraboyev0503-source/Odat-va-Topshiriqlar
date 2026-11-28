package com.memora.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.memora.app.i18n.L
import com.memora.app.i18n.S
import com.memora.app.prefs.AppLanguage
import com.memora.app.ui.theme.MemoraDesign

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewNoteSheet(
    language: AppLanguage,
    onDismiss: () -> Unit,
    onShort: () -> Unit,
    onAlbum: () -> Unit,
    onLong: () -> Unit
) {
    MemoraSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, bottom = 14.dp)
        ) {
            Text(
                L.t(language, S.NEW_NOTE),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            NewNoteOption(
                icon = { Icon(Icons.Rounded.Description, contentDescription = null) },
                title = L.t(language, S.SHORT_NOTES),
                subtitle = shortSubtitle(language),
                onClick = onShort
            )
            NewNoteOption(
                icon = { Icon(Icons.Rounded.MenuBook, contentDescription = null) },
                title = L.t(language, S.LONG_NOTES),
                subtitle = longSubtitle(language),
                onClick = onLong,
                modifier = Modifier.padding(top = 6.dp)
            )
            NewNoteOption(
                icon = { Icon(Icons.Rounded.AutoStories, contentDescription = null) },
                title = if (language == AppLanguage.UZ) "Kun izi" else "Day Trace",
                subtitle = if (language == AppLanguage.UZ) "Surat va xotira" else "Photo memory",
                onClick = onAlbum,
                modifier = Modifier.padding(top = 6.dp)
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(MemoraDesign.radiusMedium)
            ) {
                Text(if (language == AppLanguage.UZ) "Bekor qilish" else if (language == AppLanguage.RU) "Отмена" else "Cancel")
            }
        }
    }
}

@Composable
private fun NewNoteOption(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(MemoraDesign.radiusMedium),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .62f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(MemoraDesign.radiusSmall),
                color = MaterialTheme.colorScheme.surface
            ) {
                Box(Modifier.padding(8.dp)) { icon() }
            }
            Column(Modifier.weight(1f).padding(start = 11.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Text("›", style = MaterialTheme.typography.titleLarge)
        }
    }
}

private fun shortSubtitle(language: AppLanguage): String = when (language) {
    AppLanguage.UZ -> "Kundalik qisqa qayd — 50 so‘zgacha"
    AppLanguage.RU -> "Короткая запись — до 50 слов"
    AppLanguage.AR -> "ملاحظة يومية قصيرة — حتى 50 كلمة"
    AppLanguage.FR -> "Note courte du jour — jusqu’à 50 mots"
    AppLanguage.DE -> "Kurzer Tagesnotiz — bis 50 Wörter"
    AppLanguage.HI -> "छोटी दैनिक नोट — 50 शब्द तक"
    AppLanguage.ID -> "Catatan singkat harian — hingga 50 kata"
    AppLanguage.JA -> "短い日記 — 50語まで"
    AppLanguage.KO -> "짧은 일상 기록 — 최대 50단어"
    AppLanguage.PT_BR -> "Registro curto — até 50 palavras"
    AppLanguage.ES -> "Nota breve — hasta 50 palabras"
    AppLanguage.TR -> "Kısa günlük not — en fazla 50 kelime"
    AppLanguage.EN -> "Short daily note — up to 50 words"
}

private fun longSubtitle(language: AppLanguage): String = when (language) {
    AppLanguage.UZ -> "Batafsil yozuv — cheklovsiz"
    AppLanguage.RU -> "Подробная запись — без лимита"
    AppLanguage.AR -> "كتابة مفصلة — بلا حد"
    AppLanguage.FR -> "Écriture détaillée — sans limite"
    AppLanguage.DE -> "Ausführlicher Eintrag — ohne Limit"
    AppLanguage.HI -> "विस्तृत लेखन — बिना सीमा"
    AppLanguage.ID -> "Tulisan panjang — tanpa batas"
    AppLanguage.JA -> "詳しい記録 — 制限なし"
    AppLanguage.KO -> "긴 기록 — 제한 없음"
    AppLanguage.PT_BR -> "Texto detalhado — sem limite"
    AppLanguage.ES -> "Escritura detallada — sin límite"
    AppLanguage.TR -> "Ayrıntılı yazı — sınırsız"
    AppLanguage.EN -> "Detailed writing — no limit"
}

private fun topilmaTitle(language: AppLanguage): String = when (language) {
    AppLanguage.UZ -> "Topilma"
    AppLanguage.RU -> "Находка"
    AppLanguage.AR -> "مختارة"
    AppLanguage.FR -> "Trouvaille"
    AppLanguage.DE -> "Fundstück"
    AppLanguage.HI -> "खोज"
    AppLanguage.ID -> "Temuan"
    AppLanguage.JA -> "発見"
    AppLanguage.KO -> "발견"
    AppLanguage.PT_BR -> "Achado"
    AppLanguage.ES -> "Hallazgo"
    AppLanguage.TR -> "Bulgu"
    AppLanguage.EN -> "Find"
}

private fun topilmaSubtitle(language: AppLanguage): String = when (language) {
    AppLanguage.UZ -> "Manbaga bog‘langan fikr yoki iqtibos"
    AppLanguage.RU -> "Мысль или цитата, связанная с источником"
    AppLanguage.AR -> "فكرة أو اقتباس مرتبط بمصدر"
    AppLanguage.FR -> "Idée ou citation liée à une source"
    AppLanguage.DE -> "Gedanke oder Zitat mit Quelle"
    AppLanguage.HI -> "स्रोत से जुड़ा विचार या उद्धरण"
    AppLanguage.ID -> "Gagasan atau kutipan dari sumber"
    AppLanguage.JA -> "出典にひもづく考えや引用"
    AppLanguage.KO -> "출처에 연결된 생각 또는 인용"
    AppLanguage.PT_BR -> "Ideia ou citação ligada a uma fonte"
    AppLanguage.ES -> "Idea o cita vinculada a una fuente"
    AppLanguage.TR -> "Bir kaynağa bağlı fikir veya alıntı"
    AppLanguage.EN -> "A thought or quote linked to a source"
}
