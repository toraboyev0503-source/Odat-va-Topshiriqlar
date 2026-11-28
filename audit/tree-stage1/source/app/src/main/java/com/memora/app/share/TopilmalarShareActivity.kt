package com.memora.app.share

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memora.app.MemoraApplication
import com.memora.app.data.repository.TopilmaCaptureRequest
import com.memora.app.data.repository.TopilmaCaptureResult
import com.memora.app.data.repository.TopilmaImageDraft
import com.memora.app.media.TopilmalarMediaStorage
import com.memora.app.prefs.UserSettings
import com.memora.app.ui.theme.MemoraTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TopilmalarShareActivity : ComponentActivity() {
    private lateinit var app: MemoraApplication
    private val state = mutableStateOf<QuickSaveState>(QuickSaveState.Saving)
    private var saved: TopilmaCaptureResult? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setFinishOnTouchOutside(false)
        app = application as MemoraApplication
        setContent {
            val settings by app.preferences.settings.collectAsStateWithLifecycle(UserSettings())
            MemoraTheme(settings) { _, _ ->
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
                    QuickSaveSurface(
                        state = state.value,
                        onAddComment = ::addComment,
                        onAssignSource = ::assignSource,
                        onUndo = ::undo,
                        onKeepDuplicate = { state.value = QuickSaveState.Saved(saved?.sourceId != null) },
                        onClose = ::finish
                    )
                }
            }
        }
        if (savedInstanceState == null) saveImmediately(intent)
    }

    private fun saveImmediately(shareIntent: Intent) {
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                val mediaStorage = TopilmalarMediaStorage(applicationContext)
                val imageUris = sharedUris(shareIntent)
                val imageDrafts = imageUris.map { uri ->
                    val mime = contentResolver.getType(uri) ?: shareIntent.type
                    mediaStorage.storeSharedImage(uri, mime).let { TopilmaImageDraft(it.relativePath, it.mimeType) }
                }
                val sharedText = shareIntent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
                val urlMatch = URL_REGEX.find(sharedText)
                val textWithoutUrl = urlMatch?.let { match ->
                    sharedText.removeRange(match.range).trim()
                }.orEmpty()
                // A URL-only share remains useful as visible card content. For text + URL,
                // the prose is the card and the URL is stored separately as its source link.
                val rawText = if (urlMatch != null && textWithoutUrl.isNotBlank()) textWithoutUrl else sharedText
                val subject = shareIntent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString()
                val title = shareIntent.getCharSequenceExtra(Intent.EXTRA_TITLE)?.toString()
                    ?: subject?.takeIf { rawText.isNotBlank() && it != rawText }
                val sourceUri = urlMatch?.value
                val originPackage = resolveOriginPackage(shareIntent)
                val originName = originPackage?.let { packageName ->
                    runCatching {
                        val info = packageManager.getApplicationInfo(packageName, 0)
                        packageManager.getApplicationLabel(info).toString()
                    }.getOrNull()
                }
                val originIconPath = originPackage?.let { packageName ->
                    runCatching { mediaStorage.storeAppIcon(packageName, packageManager.getApplicationIcon(packageName)) }.getOrNull()
                }
                app.topilmalarRepository.capture(
                    TopilmaCaptureRequest(
                        text = rawText,
                        sourceTitle = title,
                        sourceUri = sourceUri,
                        originAppPackage = originPackage,
                        originAppName = originName,
                        originAppIconPath = originIconPath,
                        images = imageDrafts
                    )
                )
            }.onSuccess { result ->
                saved = result
                val sharedUrl = URL_REGEX.find(shareIntent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty())?.value
                if (result.sourceId != null && sharedUrl != null) {
                    TopilmalarMetadataWorker.enqueue(applicationContext, result.sourceId, sharedUrl)
                }
                withContext(Dispatchers.Main) {
                    state.value = if (result.duplicateOf != null) QuickSaveState.Duplicate else QuickSaveState.Saved(result.sourceId != null)
                }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    state.value = QuickSaveState.Error("Topilma saqlanmadi. Qayta urinib ko‘ring.")
                }
            }
        }
    }

    private fun addComment(text: String) {
        val cardId = saved?.cardId ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { app.topilmalarRepository.addComment(cardId, text) }
                .onSuccess { withContext(Dispatchers.Main) { state.value = QuickSaveState.Saved(saved?.sourceId != null, commentAdded = true) } }
        }
    }

    private fun assignSource(title: String) {
        val cardId = saved?.cardId ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                val sourceId = app.topilmalarRepository.createSource(title)
                app.topilmalarRepository.moveCards(listOf(cardId), sourceId)
                sourceId
            }.onSuccess { sourceId ->
                saved = saved?.copy(sourceId = sourceId)
                withContext(Dispatchers.Main) { state.value = QuickSaveState.Saved(hasSource = true) }
            }
        }
    }

    private fun undo() {
        val cardId = saved?.cardId
        if (cardId == null) return finish()
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { app.topilmalarRepository.softDelete(listOf(cardId)) }
            withContext(Dispatchers.Main) { finish() }
        }
    }

    private fun resolveOriginPackage(shareIntent: Intent): String? {
        callingPackage?.let { return it }
        val referrerUri = if (Build.VERSION.SDK_INT >= 22) referrer else null
        referrerUri?.takeIf { it.scheme == "android-app" }?.host?.let { return it }
        @Suppress("DEPRECATION")
        val extra = shareIntent.getParcelableExtra<Uri>(Intent.EXTRA_REFERRER)
        return extra?.takeIf { it.scheme == "android-app" }?.host
    }

    private fun sharedUris(shareIntent: Intent): List<Uri> {
        val result = mutableListOf<Uri>()
        if (shareIntent.action == Intent.ACTION_SEND_MULTIPLE) {
            @Suppress("DEPRECATION")
            result += shareIntent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
        } else {
            val single = if (Build.VERSION.SDK_INT >= 33) {
                shareIntent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                shareIntent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            single?.let(result::add)
        }
        val clip = shareIntent.clipData
        if (clip != null) for (index in 0 until clip.itemCount) clip.getItemAt(index).uri?.let(result::add)
        return result.distinct()
    }

    private companion object {
        val URL_REGEX = Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE)
    }
}

private sealed interface QuickSaveState {
    data object Saving : QuickSaveState
    data class Saved(val hasSource: Boolean, val commentAdded: Boolean = false) : QuickSaveState
    data object Duplicate : QuickSaveState
    data class Error(val message: String) : QuickSaveState
}

@Composable
private fun QuickSaveSurface(
    state: QuickSaveState,
    onAddComment: (String) -> Unit,
    onAssignSource: (String) -> Unit,
    onUndo: () -> Unit,
    onKeepDuplicate: () -> Unit,
    onClose: () -> Unit
) {
    var showComment by androidx.compose.runtime.remember { mutableStateOf(false) }
    var showSource by androidx.compose.runtime.remember { mutableStateOf(false) }
    var comment by androidx.compose.runtime.remember { mutableStateOf("") }
    var source by androidx.compose.runtime.remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(22.dp)) {
        when (state) {
            QuickSaveState.Saving -> {
                Text("Topilmalarga saqlanmoqda…", style = MaterialTheme.typography.titleMedium)
            }
            is QuickSaveState.Error -> {
                Text("Saqlanmadi", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                Text(state.message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
                TextButton(onClick = onClose) { Text("Yopish") }
            }
            QuickSaveState.Duplicate -> {
                Text("Bu Topilma avval saqlangan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Yangi nusxa vaqtincha saqlandi.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 5.dp))
                Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onUndo) { Text("Bekor qilish") }
                    Button(onClick = onKeepDuplicate) { Text("Baribir saqlash") }
                }
            }
            is QuickSaveState.Saved -> {
                Text("Topilmalarga saqlandi ✓", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    when {
                        state.commentAdded -> "Izoh ham qo‘shildi"
                        state.hasSource -> "Manba bilan saqlandi"
                        else -> "Manbasi aniqlanmagan — keyin biriktirish mumkin"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                if (showComment) {
                    OutlinedTextField(comment, { comment = it }, label = { Text("Izoh") }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                    Button(onClick = { if (comment.isNotBlank()) onAddComment(comment) }, modifier = Modifier.padding(top = 8.dp)) { Text("Izohni qo‘shish") }
                } else if (showSource) {
                    OutlinedTextField(source, { source = it }, label = { Text("Manba nomi") }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                    Button(onClick = { if (source.isNotBlank()) onAssignSource(source) }, modifier = Modifier.padding(top = 8.dp)) { Text("Biriktirish") }
                } else {
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { showComment = true }, modifier = Modifier.weight(1f)) { Text("Izoh") }
                        OutlinedButton(onClick = { showSource = true }, modifier = Modifier.weight(1f)) { Text("Manba") }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onUndo) { Text("Bekor qilish") }
                        TextButton(onClick = onClose) { Text("Yopish") }
                    }
                }
            }
        }
    }
}
