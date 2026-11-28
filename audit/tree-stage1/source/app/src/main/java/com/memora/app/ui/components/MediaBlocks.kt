package com.memora.app.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.memora.app.data.model.NoteBlock
import com.memora.app.media.ImageSampling
import com.memora.app.media.DisplayBitmapCache
import com.memora.app.prefs.AppLanguage
import com.memora.app.ui.theme.MemoraDesign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.max

@Composable
fun ImageBlockView(
    block: NoteBlock.Image,
    file: File,
    editable: Boolean,
    onDelete: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val fileKey = remember(file.absolutePath, file.lastModified(), file.length()) {
        "${file.absolutePath}:${file.lastModified()}:${file.length()}"
    }
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = fileKey) {
        value = withContext(Dispatchers.IO) {
            DisplayBitmapCache.get(fileKey) ?: decodeDisplayBitmap(file)?.also {
                DisplayBitmapCache.put(fileKey, it)
            }
        }
    }
    var showFullScreen by rememberSaveable(fileKey) { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MemoraDesign.radiusSmall),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        val current = bitmap
        if (current == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.6f),
                contentAlignment = Alignment.Center
            ) {
                // Deliberately quiet placeholder while the sampled decode runs off the UI thread.
            }
        } else {
            Box {
                Image(
                    bitmap = current.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio((current.width.toFloat() / current.height.coerceAtLeast(1)).coerceIn(0.65f, 1.8f))
                        .clickable { showFullScreen = true },
                    contentScale = ContentScale.Fit
                )
                if (editable) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
                    }
                }
            }
        }
    }

    if (showFullScreen) {
        Dialog(
            onDismissRequest = { showFullScreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            var zoom by remember { mutableStateOf(1f) }
            var pan by remember { mutableStateOf(Offset.Zero) }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, panChange, zoomChange, _ ->
                            val nextZoom = (zoom * zoomChange).coerceIn(1f, 5f)
                            zoom = nextZoom
                            pan = if (nextZoom <= 1.01f) Offset.Zero else pan + panChange
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        bitmap?.let { full ->
                            Image(
                                bitmap = full.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = zoom
                                        scaleY = zoom
                                        translationX = pan.x
                                        translationY = pan.y
                                    }
                            )
                        }
                        IconButton(
                            onClick = { showFullScreen = false },
                            modifier = Modifier.align(Alignment.TopEnd).padding(18.dp)
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = null, tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}

private fun decodeDisplayBitmap(file: File): Bitmap? = runCatching {
    if (!file.exists() || file.length() <= 0L) return@runCatching null
    val bounds = BitmapFactory.Options().also { options ->
        options.inJustDecodeBounds = true
        BitmapFactory.decodeFile(file.absolutePath, options)
    }
    require(bounds.outWidth > 0 && bounds.outHeight > 0)
    BitmapFactory.decodeFile(
        file.absolutePath,
        BitmapFactory.Options().apply {
            inSampleSize = ImageSampling.sampleSize(
                bounds.outWidth,
                bounds.outHeight,
                ImageSampling.DISPLAY_MAX_DIMENSION,
                ImageSampling.DISPLAY_MAX_PIXELS
            )
            inPreferredConfig = Bitmap.Config.RGB_565
            inDither = true
            inScaled = false
        }
    )
}.getOrNull()

@Composable
fun AudioBlockView(
    block: NoteBlock.Audio,
    file: File,
    editable: Boolean,
    isRecording: Boolean = false,
    isRecordingPaused: Boolean = false,
    recordingElapsedMs: Long = block.durationMs,
    language: AppLanguage = AppLanguage.EN,
    onPauseRecording: () -> Unit = {},
    onResumeRecording: () -> Unit = {},
    onStopRecording: () -> Unit = {},
    onDelete: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var player by remember(file.absolutePath) { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember(file.absolutePath) { mutableStateOf(false) }
    var currentPositionMs by remember(file.absolutePath) { mutableLongStateOf(0L) }
    var durationMs by remember(file.absolutePath, block.durationMs) {
        mutableLongStateOf(block.durationMs.coerceAtLeast(1L))
    }

    fun ensurePlayer(): MediaPlayer? {
        if (!file.exists()) return null
        val existing = player
        if (existing != null) return existing
        return runCatching {
            MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                durationMs = max(block.durationMs, duration.toLong()).coerceAtLeast(1L)
                setOnCompletionListener {
                    playing = false
                    currentPositionMs = 0L
                    runCatching { seekTo(0) }
                }
            }
        }.getOrNull()?.also { player = it }
    }

    LaunchedEffect(playing, player) {
        while (playing) {
            val active = player
            if (active != null) {
                currentPositionMs = runCatching { active.currentPosition.toLong() }.getOrDefault(currentPositionMs)
                durationMs = max(durationMs, runCatching { active.duration.toLong() }.getOrDefault(durationMs))
            }
            delay(200)
        }
    }

    DisposableEffect(file.absolutePath) {
        onDispose {
            runCatching { player?.stop() }
            player?.release()
            player = null
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MemoraDesign.radiusSmall),
        color = if (isRecording) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Rounded.Mic, contentDescription = null)
                Column(Modifier.weight(1f)) {
                    Text(
                        when (language) {
                            AppLanguage.RU -> if (isRecordingPaused) "Пауза" else if (isRecording) "Запись…" else "Аудио"
                            AppLanguage.UZ -> if (isRecordingPaused) "Pauza" else if (isRecording) "Yozilmoqda…" else "Audio"
                            else -> if (isRecordingPaused) "Paused" else if (isRecording) "Recording…" else "Audio"
                        },
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        if (isRecording) formatDuration(recordingElapsedMs)
                        else "${formatDuration(currentPositionMs)} / ${formatDuration(durationMs)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isRecording) {
                    IconButton(onClick = if (isRecordingPaused) onResumeRecording else onPauseRecording) {
                        Icon(
                            if (isRecordingPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                            contentDescription = null
                        )
                    }
                    IconButton(onClick = onStopRecording) {
                        Icon(Icons.Rounded.Stop, contentDescription = null)
                    }
                } else if (file.exists()) {
                    IconButton(
                        onClick = {
                            if (playing) {
                                player?.pause()
                                playing = false
                            } else {
                                val active = ensurePlayer()
                                if (active != null) {
                                    runCatching {
                                        active.seekTo(currentPositionMs.coerceIn(0L, durationMs).toInt())
                                        active.start()
                                    }.onSuccess { playing = true }
                                }
                            }
                        }
                    ) {
                        Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null)
                    }
                }

                if (editable && !isRecording) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
                    }
                }
            }

            if (!isRecording && file.exists() && durationMs > 0L) {
                Slider(
                    value = currentPositionMs.coerceIn(0L, durationMs).toFloat(),
                    onValueChange = { value ->
                        currentPositionMs = value.toLong().coerceIn(0L, durationMs)
                    },
                    onValueChangeFinished = {
                        ensurePlayer()?.let { active ->
                            runCatching { active.seekTo(currentPositionMs.toInt()) }
                        }
                    },
                    valueRange = 0f..durationMs.toFloat(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs.coerceAtLeast(0L) / 1000L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
