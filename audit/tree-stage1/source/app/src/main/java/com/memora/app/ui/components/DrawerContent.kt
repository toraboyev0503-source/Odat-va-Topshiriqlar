package com.memora.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import com.memora.app.i18n.L
import com.memora.app.i18n.S
import com.memora.app.prefs.AppLanguage
import com.memora.app.ui.theme.MemoraDesign

@Composable
fun DrawerContent(
    language: AppLanguage,
    isDark: Boolean,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onReminders: () -> Unit,
    onTitles: () -> Unit,
    onLanguage: () -> Unit,
    onStatistics: () -> Unit,
    onTheme: () -> Unit,
    onSecurity: () -> Unit,
    onSubscription: () -> Unit,
    onToggleDark: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val drawerWidth = (configuration.screenWidthDp.dp * 0.50f)
        .coerceIn(180.dp, 320.dp)

    ModalDrawerSheet(
        modifier = Modifier
            .fillMaxHeight()
            .width(drawerWidth),
        drawerContainerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            Modifier
                .fillMaxHeight()
                .padding(horizontal = 10.dp)
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Memora",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp, top = 24.dp, bottom = 14.dp)
                )

                DrawerItem(Icons.Rounded.FileDownload, L.t(language, S.SAVE_NOTES), onExport)
                DrawerItem(Icons.Rounded.FileUpload, L.t(language, S.IMPORT_FILE), onImport)
                DrawerItem(Icons.Rounded.WorkspacePremium, L.t(language, S.SUBSCRIPTION), onSubscription)
                DrawerItem(Icons.Rounded.Schedule, L.t(language, S.WRITING_TIME), onReminders)
                DrawerItem(Icons.Rounded.Title, L.t(language, S.TITLES), onTitles)
                DrawerItem(Icons.Rounded.Language, L.t(language, S.LANGUAGE), onLanguage)
                DrawerItem(Icons.Rounded.BarChart, L.t(language, S.STATISTICS), onStatistics)
                DrawerItem(Icons.Rounded.Palette, L.t(language, S.THEME), onTheme)
                DrawerItem(Icons.Rounded.Lock, L.t(language, S.SECURITY), onSecurity)
                Spacer(Modifier.padding(bottom = 10.dp))
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            Surface(
                onClick = onToggleDark,
                shape = RoundedCornerShape(MemoraDesign.radiusSmall),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                border = BorderStroke(0.7.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (isDark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        L.t(language, if (isDark) S.LIGHT else S.DARK),
                        style = MaterialTheme.typography.bodyMedium,
                        softWrap = true,
                        maxLines = 2
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(MemoraDesign.radiusSmall),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
        border = BorderStroke(0.7.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(9.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                softWrap = true,
                maxLines = 3,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
