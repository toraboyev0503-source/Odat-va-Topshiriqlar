package com.memora.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.memora.app.i18n.L
import com.memora.app.i18n.S
import com.memora.app.prefs.AppLanguage
import com.memora.app.ui.components.MemoraSelectionCard
import com.memora.app.ui.components.MemoraTopBar

@Composable
fun LanguageScreen(
    current: AppLanguage,
    onBack: () -> Unit,
    onSelect: (AppLanguage) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        MemoraTopBar(
            title = L.t(current, S.LANGUAGE),
            navigation = { IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = null) } }
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(AppLanguage.entries, key = { it.name }) { language ->
                MemoraSelectionCard(
                    title = language.displayName,
                    subtitle = language.tag,
                    selected = language == current,
                    onClick = { onSelect(language) }
                )
            }
        }
    }
}
