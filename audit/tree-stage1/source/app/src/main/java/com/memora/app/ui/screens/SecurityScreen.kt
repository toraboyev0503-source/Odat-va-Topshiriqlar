package com.memora.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.memora.app.i18n.L
import com.memora.app.i18n.S
import com.memora.app.prefs.LockInterval
import com.memora.app.prefs.UserSettings
import com.memora.app.ui.components.MemoraSelectionCard
import com.memora.app.ui.components.MemoraTopBar

@Composable
fun SecurityScreen(
    settings: UserSettings,
    onBack: () -> Unit,
    onEnabled: (Boolean) -> Unit,
    onInterval: (LockInterval) -> Unit
) {
    val language = settings.language
    Column(Modifier.fillMaxSize()) {
        MemoraTopBar(
            title = L.t(language, S.SECURITY),
            navigation = { IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = null) } }
        )
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        if (settings.lockEnabled) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(Modifier.weight(1f)) {
                        Text(L.t(language, S.APP_LOCK), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            L.t(language, if (settings.lockEnabled) S.ENABLED else S.DISABLED),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = settings.lockEnabled, onCheckedChange = onEnabled)
                }
            }
            if (settings.lockEnabled) {
                Text(
                    L.t(language, S.LOCK_INTERVAL),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 14.dp, bottom = 2.dp)
                )
                LockInterval.entries.forEach { interval ->
                    MemoraSelectionCard(
                        title = L.t(language, when (interval) {
                            LockInterval.IMMEDIATELY -> S.IMMEDIATELY
                            LockInterval.ONE_MINUTE -> S.ONE_MINUTE
                            LockInterval.FIVE_MINUTES -> S.FIVE_MINUTES
                            LockInterval.FIFTEEN_MINUTES -> S.FIFTEEN_MINUTES
                            LockInterval.THIRTY_MINUTES -> S.THIRTY_MINUTES
                        }),
                        selected = settings.lockInterval == interval,
                        onClick = { onInterval(interval) }
                    )
                }
            }
        }
    }
}
