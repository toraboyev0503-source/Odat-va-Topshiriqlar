package com.memora.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.memora.app.ui.theme.MemoraDesign
import kotlinx.coroutines.delay

enum class MemoraFeedbackKind { LOADING, SUCCESS, ERROR, INFO }

data class MemoraFeedbackMessage(
    val id: Long = System.nanoTime(),
    val text: String,
    val kind: MemoraFeedbackKind
)

/**
 * Shared compact status/snackbar surface used by long-running and result actions.
 * Loading stays visible until replaced; result messages dismiss themselves.
 */
@Composable
fun MemoraFeedbackHost(
    message: MemoraFeedbackMessage?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(message?.id) {
        val current = message ?: return@LaunchedEffect
        if (current.kind != MemoraFeedbackKind.LOADING) {
            delay(if (current.kind == MemoraFeedbackKind.ERROR) 4200 else 2800)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        val current = message ?: return@AnimatedVisibility
        val tint = when (current.kind) {
            MemoraFeedbackKind.ERROR -> MaterialTheme.colorScheme.error
            MemoraFeedbackKind.SUCCESS -> MaterialTheme.colorScheme.primary
            MemoraFeedbackKind.LOADING,
            MemoraFeedbackKind.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        Surface(
            shape = RoundedCornerShape(MemoraDesign.radiusMedium),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 5.dp,
            shadowElevation = 5.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (current.kind) {
                    MemoraFeedbackKind.LOADING -> CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    MemoraFeedbackKind.SUCCESS -> Icon(Icons.Rounded.CheckCircle, null, tint = tint)
                    MemoraFeedbackKind.ERROR -> Icon(Icons.Rounded.ErrorOutline, null, tint = tint)
                    MemoraFeedbackKind.INFO -> Icon(Icons.Rounded.Info, null, tint = tint)
                }
                Text(
                    text = current.text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (current.kind == MemoraFeedbackKind.ERROR) tint else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
