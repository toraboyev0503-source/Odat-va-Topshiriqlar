package com.memora.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.memora.app.i18n.L
import com.memora.app.i18n.S
import com.memora.app.prefs.AppLanguage
import com.memora.app.ui.theme.MemoraDesign

@Composable
fun SearchLandingScreen(
    language: AppLanguage,
    shortCount: Int,
    longCount: Int,
    onShort: () -> Unit,
    onLong: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                L.t(language, S.SEARCH),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 10.dp)
            )
        }
        Text(
            searchHint(language),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SearchTypeCard(
            icon = Icons.Rounded.Description,
            title = L.t(language, S.SHORT_NOTES),
            count = shortCount,
            language = language,
            onClick = onShort
        )
        SearchTypeCard(
            icon = Icons.Rounded.MenuBook,
            title = L.t(language, S.LONG_NOTES),
            count = longCount,
            language = language,
            onClick = onLong
        )
    }
}

@Composable
private fun SearchTypeCard(
    icon: ImageVector,
    title: String,
    count: Int,
    language: AppLanguage,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(MemoraDesign.radiusMedium),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = MemoraDesign.shadowSubtle,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(MemoraDesign.radiusSmall),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(14.dp)
                )
            }
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
                Text(
                    "$count ${L.t(language, S.NOTES)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Text("›", style = MaterialTheme.typography.headlineMedium)
        }
    }
}

private fun searchHint(language: AppLanguage): String = when (language) {
    AppLanguage.UZ -> "Qaysi yozuv turidan qidirmoqchisiz?"
    AppLanguage.RU -> "В каком типе записей искать?"
    AppLanguage.AR -> "في أي نوع من الملاحظات تريد البحث؟"
    AppLanguage.FR -> "Dans quel type de note voulez-vous chercher ?"
    AppLanguage.DE -> "In welchem Notiztyp möchten Sie suchen?"
    AppLanguage.HI -> "किस प्रकार की नोट में खोजें?"
    AppLanguage.ID -> "Cari di jenis catatan yang mana?"
    AppLanguage.JA -> "どの種類の記録を検索しますか？"
    AppLanguage.KO -> "어떤 기록 유형에서 검색할까요?"
    AppLanguage.PT_BR -> "Em qual tipo de nota deseja buscar?"
    AppLanguage.ES -> "¿En qué tipo de nota quieres buscar?"
    AppLanguage.TR -> "Hangi not türünde aramak istiyorsunuz?"
    AppLanguage.EN -> "Which note type would you like to search?"
}
