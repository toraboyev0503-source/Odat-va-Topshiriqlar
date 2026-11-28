package com.memora.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memora.app.data.local.TopilmaCommentEntity
import com.memora.app.data.repository.TopilmalarRepository
import com.memora.app.share.TopilmaShareManager
import com.memora.app.ui.components.TopilmaCard
import com.memora.app.ui.theme.MemoraDesign
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@Composable
fun TopilmaCommentsScreen(
    cardId: String,
    repository: TopilmalarRepository,
    canEdit: Boolean,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val outerBackgroundArgb = MaterialTheme.colorScheme.background.toArgb()
    val row by repository.observeCardRow(cardId).collectAsStateWithLifecycle(null)
    val comments by repository.observeComments(cardId).collectAsStateWithLifecycle(emptyList())
    var newComment by remember(cardId) { mutableStateOf("") }
    val composerFocus = remember { FocusRequester() }

    Scaffold(
        topBar = {
            Surface(shadowElevation = MemoraDesign.shadowSubtle) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Orqaga") }
                    Text(
                        text = "Izohlar · ${comments.size}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        },
        bottomBar = {
            Surface(shadowElevation = MemoraDesign.shadowRaised) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = newComment,
                        onValueChange = { newComment = it },
                        placeholder = { Text("Fikr yozing…") },
                        minLines = 1,
                        maxLines = 5,
                        modifier = Modifier.weight(1f).focusRequester(composerFocus),
                        enabled = canEdit
                    )
                    IconButton(
                        onClick = {
                            val text = newComment.trim()
                            if (text.isNotBlank()) scope.launch {
                                repository.addComment(cardId, text)
                                newComment = ""
                            }
                        },
                        enabled = canEdit && newComment.isNotBlank(),
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Icon(Icons.Rounded.Send, contentDescription = "Yuborish")
                    }
                }
            }
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            row?.let { card ->
                item(key = "card-${card.id}") {
                    TopilmaCard(
                        row = card,
                        canEdit = canEdit,
                        onShare = {
                            scope.launch {
                                TopilmaShareManager.shareCardImage(context, card, outerBackgroundArgb)
                            }
                        },
                        onLike = { scope.launch { repository.incrementLike(card.id) } },
                        onComments = { composerFocus.requestFocus() },
                        onToggleFavorite = { scope.launch { repository.setFavorite(listOf(card.id), !card.favorite) } },
                        onToggleReview = { scope.launch { repository.setCardReviewExcluded(card.id, !card.excludedFromReview) } },
                        forceExpanded = true,
                        showExpandControl = false
                    )
                }
            }
            items(comments, key = { it.id }) { comment ->
                CommentBubble(
                    comment = comment,
                    canEdit = canEdit,
                    onEdit = { updated -> scope.launch { repository.editComment(comment, updated) } },
                    onDelete = { scope.launch { repository.deleteComment(comment.id) } }
                )
            }
        }
    }
}

@Composable
private fun CommentBubble(
    comment: TopilmaCommentEntity,
    canEdit: Boolean,
    onEdit: (String) -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var editText by remember(comment.id, comment.text) { mutableStateOf(comment.text) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .42f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 13.dp, vertical = 11.dp)) {
            if (editing) {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.align(Alignment.End)) {
                    TextButton(onClick = { editing = false; editText = comment.text }) { Text("Bekor") }
                    TextButton(
                        onClick = { onEdit(editText); editing = false },
                        enabled = editText.isNotBlank()
                    ) { Text("Saqlash") }
                }
            } else {
                Row(verticalAlignment = Alignment.Top) {
                    Text(comment.text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    if (canEdit) {
                        androidx.compose.foundation.layout.Box {
                            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = "Izoh amallari", modifier = Modifier.size(18.dp))
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Tahrirlash") },
                                    leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                                    onClick = { menuOpen = false; editing = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("O‘chirish") },
                                    leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null) },
                                    onClick = { menuOpen = false; onDelete() }
                                )
                            }
                        }
                    }
                }
                Text(
                    text = buildString {
                        append(commentDateTime(comment.createdAt))
                        if (comment.editedAt != null) append(" · tahrirlangan")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .78f),
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

private fun commentDateTime(epoch: Long): String = Instant.ofEpochMilli(epoch)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("dd.MM.yyyy · HH:mm"))
