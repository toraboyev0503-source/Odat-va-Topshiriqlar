package com.memora.app.ui.screens

import com.memora.app.ui.components.MemoraDialog as AlertDialog
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.memora.app.data.local.SavedTitleEntity
import com.memora.app.i18n.L
import com.memora.app.i18n.S
import com.memora.app.prefs.AppLanguage
import com.memora.app.ui.components.MemoraConfirmationDialog
import com.memora.app.ui.components.MemoraTopBar
import com.memora.app.ui.theme.MemoraDesign

@Composable
fun TitlesScreen(
    titles: List<SavedTitleEntity>,
    language: AppLanguage,
    onBack: () -> Unit,
    onAdd: (String) -> Unit,
    onDelete: (SavedTitleEntity) -> Unit,
    onEdit: (SavedTitleEntity, String, Boolean) -> Unit,
    onPin: (SavedTitleEntity, Boolean) -> Unit
) {
    var value by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<SavedTitleEntity?>(null) }
    var editedValue by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<SavedTitleEntity?>(null) }

    Column(Modifier.fillMaxSize()) {
        MemoraTopBar(
            title = L.t(language, S.TITLES),
            navigation = {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = null) }
            }
        )

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(L.t(language, S.ADD_TITLE)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = RoundedCornerShape(MemoraDesign.radiusMedium)
            )
            IconButton(
                onClick = {
                    if (value.isNotBlank()) {
                        onAdd(value)
                        value = ""
                    }
                },
                modifier = Modifier.padding(start = 8.dp)
            ) { Icon(Icons.Rounded.Add, contentDescription = L.t(language, S.ADD_TITLE)) }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)
        ) {
            items(titles, key = { it.id }) { item ->
                TitleCard(
                    value = item.value,
                    pinned = item.pinnedAt != null,
                    language = language,
                    onDelete = { pendingDelete = item },
                    onEdit = {
                        editing = item
                        editedValue = item.value
                    },
                    onPin = { onPin(item, item.pinnedAt == null) }
                )
            }
        }
    }

    pendingDelete?.let { item ->
        MemoraConfirmationDialog(
            language = language,
            title = if (language == AppLanguage.UZ) "Sarlavhani o‘chirasizmi?" else "Delete saved title?",
            text = if (language == AppLanguage.UZ) "Bu faqat saqlangan sarlavha namunasini o‘chiradi. Mavjud yozuvlar o‘zgarmaydi." else "This deletes only the saved title template. Existing notes stay unchanged.",
            onConfirm = { onDelete(item); pendingDelete = null },
            onDismiss = { pendingDelete = null }
        )
    }

    val editItem = editing
    if (editItem != null) {
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(L.t(language, S.EDIT_TITLE)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = editedValue,
                        onValueChange = { editedValue = it },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        if (language == AppLanguage.UZ) "Bu faqat saqlangan sarlavha namunasini o‘zgartiradi. Eski yozuvlar o‘zgarmaydi."
                        else "This changes only the saved title template. Existing notes stay unchanged.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (editedValue.isNotBlank()) {
                        onEdit(editItem, editedValue, false)
                        editing = null
                    }
                }) { Text(L.t(language, S.SAVE)) }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(L.t(language, S.CANCEL)) } }
        )
    }
}

@Composable
private fun TitleCard(
    value: String,
    pinned: Boolean,
    language: AppLanguage,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onPin: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(MemoraDesign.radiusSmall),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().padding(bottom = 9.dp)
    ) {
        Row(
            Modifier.padding(start = 18.dp, end = 6.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(value, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            IconButton(onClick = onPin) {
                Icon(
                    Icons.Rounded.PushPin,
                    contentDescription = L.t(language, if (pinned) S.UNPIN else S.PIN),
                    tint = if (pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                )
            }
            IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, contentDescription = L.t(language, S.EDIT_TITLE)) }
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, contentDescription = L.t(language, S.DELETE)) }
        }
    }
}
