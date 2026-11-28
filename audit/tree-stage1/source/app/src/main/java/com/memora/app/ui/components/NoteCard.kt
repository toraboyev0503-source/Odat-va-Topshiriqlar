package com.memora.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.memora.app.data.local.NoteEntity
import com.memora.app.data.local.NoteType
import com.memora.app.data.model.NoteMood
import com.memora.app.i18n.L
import com.memora.app.i18n.S
import com.memora.app.prefs.AppLanguage
import com.memora.app.util.TextUtils
import com.memora.app.util.HashtagUtils
import com.memora.app.util.TimeUtils
import com.memora.app.ui.theme.MemoraDesign
import org.json.JSONArray
import kotlin.math.max

private data class NoteMediaCount(
    val imageCount: Int,
    val audioCount: Int
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCard(
    note: NoteEntity,
    displayTitle: String,
    language: AppLanguage,
    selected: Boolean,
    previewLines: Int = 2,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDeleteRequest: (() -> Unit)? = null,
    onEditRequest: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    imageCount: Int? = null,
    audioCount: Int? = null
) {
    val mediaCount = remember(note.blocksJson, note.hasImages, note.hasAudio, imageCount, audioCount) {
        if (imageCount != null && audioCount != null) NoteMediaCount(imageCount, audioCount)
        else countMedia(note.blocksJson, note.hasImages, note.hasAudio)
    }
    val hashtags = remember(note.hashtagsJson) { HashtagUtils.decode(note.hashtagsJson) }
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(MemoraDesign.radiusMedium),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        else MaterialTheme.colorScheme.surface,
        border = if (selected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
        } else {
            null
        },
        shadowElevation = MemoraDesign.shadowSubtle,
        tonalElevation = 0.dp
    ) {
        Box(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = if (onEditRequest != null || onDeleteRequest != null) 44.dp else 18.dp, top = 15.dp, bottom = 14.dp)
            ) {
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = TimeUtils.dateTime(note.createdAt, language),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (note.type == NoteType.SHORT.name) L.t(language, S.SHORT) else L.t(language, S.LONG),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    NoteMood.fromStored(note.mood)?.let { mood ->
                        MoodSticker(
                            mood = mood,
                            description = mood.name,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.weight(1f))

                    if (mediaCount.imageCount > 0) {
                        MediaCountIndicator(
                            image = true,
                            count = mediaCount.imageCount
                        )
                    }
                    if (mediaCount.audioCount > 0) {
                        MediaCountIndicator(
                            image = false,
                            count = mediaCount.audioCount
                        )
                    }
                }

                if (note.plainText.isNotBlank()) {
                    Text(
                        text = TextUtils.preview(note.plainText),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.5.sp, lineHeight = 22.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = previewLines.coerceAtLeast(1),
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 7.dp)
                    )
                }

                HashtagChips(
                    tags = hashtags,
                    modifier = Modifier.padding(top = 9.dp)
                )
            }

            if (onEditRequest != null || onDeleteRequest != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 6.dp, end = 4.dp)
                ) {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.Rounded.MoreVert,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        onEditRequest?.let { edit ->
                            DropdownMenuItem(
                                text = { Text(L.t(language, S.EDIT)) },
                                leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    edit()
                                }
                            )
                        }
                        onDeleteRequest?.let { delete ->
                            DropdownMenuItem(
                                text = { Text(L.t(language, S.DELETE)) },
                                leadingIcon = {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
                                },
                                onClick = {
                                    menuExpanded = false
                                    delete()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaCountIndicator(
    image: Boolean,
    count: Int
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            imageVector = if (image) Icons.Rounded.Image else Icons.Rounded.Mic,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun countMedia(
    blocksJson: String,
    legacyHasImages: Boolean,
    legacyHasAudio: Boolean
): NoteMediaCount {
    if (blocksJson.isBlank()) {
        return NoteMediaCount(
            imageCount = if (legacyHasImages) 1 else 0,
            audioCount = if (legacyHasAudio) 1 else 0
        )
    }

    return runCatching {
        val array = JSONArray(blocksJson)
        var imageCount = 0
        var audioCount = 0
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            when (obj.optString("type")) {
                "image" -> imageCount += 1
                "audio" -> audioCount += 1
            }
        }
        NoteMediaCount(
            imageCount = max(imageCount, if (legacyHasImages) 1 else 0),
            audioCount = max(audioCount, if (legacyHasAudio) 1 else 0)
        )
    }.getOrElse {
        NoteMediaCount(
            imageCount = if (legacyHasImages) 1 else 0,
            audioCount = if (legacyHasAudio) 1 else 0
        )
    }
}
