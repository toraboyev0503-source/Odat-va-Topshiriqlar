package com.memora.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.memora.app.data.local.TopilmaCardRow
import com.memora.app.share.TopilmaShareManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun TopilmaCardRow.tagNames(): List<String> = tagNamesCsv
    ?.split(' ')
    ?.map { it.trim().removePrefix("#") }
    ?.filter { it.isNotBlank() }
    ?.distinct()
    .orEmpty()

/**
 * Interactive shell around the exact same bitmap used by Android Share and
 * Obsidian. No second Compose recreation of the card is allowed: this removes
 * typography, spacing and icon drift by construction.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
@Suppress("UNUSED_PARAMETER")
fun TopilmaCard(
    row: TopilmaCardRow,
    canEdit: Boolean,
    onShare: () -> Unit,
    onLike: () -> Unit,
    onComments: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onOpen: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onToggleFavorite: (() -> Unit)? = null,
    onToggleReview: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onOpenSource: (() -> Unit)? = null,
    forceExpanded: Boolean = false,
    showExpandControl: Boolean = true
) {
    val context = LocalContext.current
    val outerBackgroundArgb = MaterialTheme.colorScheme.background.toArgb()
    var menuOpen by remember { mutableStateOf(false) }
    val bitmap by produceState<Bitmap?>(null, row, outerBackgroundArgb) {
        value = withContext(Dispatchers.IO) {
            // 768 px is comfortably above the physical card width on supported
            // phones and keeps long 100–150-word feed cards memory-safe. Share and
            // Obsidian use the same renderer at its 1536 px default.
            TopilmaShareManager.renderCardBitmap(
                context.applicationContext,
                row,
                outerBackgroundArgb,
                targetWidth = 768
            )
        }
    }

    DisposableEffect(bitmap) {
        val current = bitmap
        onDispose { if (current != null && !current.isRecycled) current.recycle() }
    }

    val aspect = bitmap?.let { it.width.toFloat() / it.height.toFloat() }
        ?: TopilmaCardVisualSpec.CANVAS_WIDTH.toFloat() / TopilmaCardVisualSpec.MIN_CANVAS_HEIGHT
    val outerShape = RoundedCornerShape(18.dp)
    val cardGesture = onOpen != null || onLongPress != null
    val selectedBorder = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .border(if (selected) 2.dp else 0.dp, selectedBorder, outerShape)
            .then(
                if (cardGesture) {
                    Modifier.combinedClickable(
                        onClick = { onOpen?.invoke() },
                        onLongClick = { onLongPress?.invoke() }
                    )
                } else Modifier
            )
    ) {
        val current = bitmap
        if (current == null) {
            Surface(
                color = Color(outerBackgroundArgb),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                }
            }
            return@BoxWithConstraints
        }

        Image(
            bitmap = current.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )

        // The visible glyphs are part of the canonical bitmap. These transparent
        // hit targets provide native interaction and accessibility without changing pixels.
        val referenceWidth = TopilmaCardVisualSpec.CANVAS_WIDTH.toFloat()
        val actionRowHeight = maxWidth * (190f / referenceWidth)
        val actionBottomPadding = maxWidth * (118f / referenceWidth)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(
                    start = maxWidth * (TopilmaCardVisualSpec.CARD_LEFT / referenceWidth),
                    end = maxWidth * ((referenceWidth - TopilmaCardVisualSpec.CARD_RIGHT) / referenceWidth),
                    bottom = actionBottomPadding
                )
                .height(actionRowHeight)
        ) {
            TopilmaHitTarget("Ulashish", true, onShare, Modifier.weight(1f))
            TopilmaHitTarget("Yoqtirish", canEdit, onLike, Modifier.weight(1f))
            TopilmaHitTarget("Izohlar", true, onComments, Modifier.weight(1f))
        }

        if (onEdit != null || onToggleFavorite != null || onToggleReview != null || onDelete != null || onOpenSource != null) {
            val menuSize = maxWidth * (126f / referenceWidth)
            val menuX = maxWidth * ((TopilmaCardVisualSpec.MENU_CENTER_X - 63f) / referenceWidth)
            val menuY = maxWidth * ((TopilmaCardVisualSpec.MENU_CENTER_Y - 63f) / referenceWidth)
            Box(
                modifier = Modifier
                    .offset(x = menuX, y = menuY)
                    .size(menuSize)
                    .semantics {
                        contentDescription = "Ko‘proq"
                        role = Role.Button
                    }
                    .combinedClickable(onClick = { menuOpen = true }, onLongClick = {})
            ) {
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    onOpenSource?.let { action ->
                        DropdownMenuItem(
                            text = { Text("Manbaga o‘tish") },
                            leadingIcon = { Icon(Icons.Rounded.OpenInNew, null) },
                            onClick = { menuOpen = false; action() }
                        )
                    }
                    onEdit?.let { action ->
                        DropdownMenuItem(
                            text = { Text("Tahrirlash") },
                            leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                            enabled = canEdit,
                            onClick = { menuOpen = false; action() }
                        )
                    }
                    onToggleFavorite?.let { action ->
                        DropdownMenuItem(
                            text = { Text(if (row.favorite) "Sevimlidan olib tashlash" else "Sevimli") },
                            leadingIcon = { Icon(if (row.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null) },
                            enabled = canEdit,
                            onClick = { menuOpen = false; action() }
                        )
                    }
                    onToggleReview?.let { action ->
                        DropdownMenuItem(
                            text = { Text(if (row.excludedFromReview) "Qayta ko‘rishga qo‘shish" else "Qayta ko‘rsatma") },
                            leadingIcon = { Icon(if (row.excludedFromReview) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff, null) },
                            enabled = canEdit,
                            onClick = { menuOpen = false; action() }
                        )
                    }
                    onDelete?.let { action ->
                        DropdownMenuItem(
                            text = { Text("O‘chirish") },
                            leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null) },
                            enabled = canEdit,
                            onClick = { menuOpen = false; action() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopilmaHitTarget(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics {
                contentDescription = label
                role = Role.Button
            }
            .combinedClickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = {}
            )
    )
}
