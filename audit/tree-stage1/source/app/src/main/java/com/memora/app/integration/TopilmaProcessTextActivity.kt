package com.memora.app.integration

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.memora.app.MemoraApplication
import com.memora.app.data.local.TopilmaSourceRow
import com.memora.app.media.TopilmalarMediaStorage
import com.memora.app.prefs.TopilmaIntegrationPreferences
import com.memora.app.prefs.AppLanguage
import com.memora.app.i18n.L
import com.memora.app.i18n.S
import com.memora.app.ui.theme.MemoraTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class TopilmaProcessTextActivity : ComponentActivity() {
    private lateinit var selectedText: String
    private var originPackage: String? = null
    private var detectedMetadata: TopilmaExternalCapture.Metadata? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setGravity(Gravity.BOTTOM)
        setFinishOnTouchOutside(true)
        selectedText = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()?.trim().orEmpty()
        if (selectedText.isBlank()) {
            finish()
            return
        }
        originPackage = resolveOriginPackage(intent)
        detectedMetadata = TopilmaExternalCapture.metadataFromProcessIntent(
            context = applicationContext,
            packageName = originPackage,
            intent = intent,
            selectedText = selectedText
        )

        // Read the user's saved theme/language before rendering the external sheet.
        lifecycleScope.launch {
            val settings = (applicationContext as MemoraApplication).preferences.settings.first()
            setContent {
                MemoraTheme(settings) { _, _ ->
                    ProcessTextSourceChooser(
                        text = selectedText,
                        detected = detectedMetadata,
                        language = settings.language,
                        onDismiss = { finish() },
                        onSaved = {
                            Toast.makeText(this@TopilmaProcessTextActivity,
                                L.t(settings.language, S.SAVED), Toast.LENGTH_SHORT).show()
                            finish()
                        },
                        onError = {
                            Toast.makeText(this@TopilmaProcessTextActivity,
                                L.t(settings.language, S.SAVE_FAILED), Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }

    private fun resolveOriginPackage(sourceIntent: Intent): String? {
        callingPackage?.let { return it }
        if (Build.VERSION.SDK_INT >= 22) {
            referrer?.takeIf { it.scheme == "android-app" }?.host?.let { return it }
        }
        @Suppress("DEPRECATION")
        val extra = sourceIntent.getParcelableExtra<Uri>(Intent.EXTRA_REFERRER)
        return extra?.takeIf { it.scheme == "android-app" }?.host
    }
}

@Composable
private fun ProcessTextSourceChooser(
    text: String,
    detected: TopilmaExternalCapture.Metadata?,
    language: AppLanguage,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    onError: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as MemoraApplication
    val prefs = remember(context) { TopilmaIntegrationPreferences(context) }
    val isPreferredOrigin = detected?.packageName?.let(prefs::isEnabled) == true
    val scope = rememberCoroutineScope()
    var sources by remember { mutableStateOf<List<TopilmaSourceRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var showAll by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var mappedDetectedSource by remember { mutableStateOf<TopilmaSourceRow?>(null) }

    fun refresh() {
        scope.launch {
            val fresh = withContext(Dispatchers.IO) { app.topilmalarRepository.observeSources().first() }
            sources = fresh
            val key = detected?.contextKey()
            mappedDetectedSource = if (isPreferredOrigin) {
                key?.let(prefs::sourceForContext)?.let { id -> fresh.firstOrNull { it.id == id } }
            } else null
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }
    BackHandler {
        if (showAll) showAll = false else onDismiss()
    }

    fun saveToSource(source: TopilmaSourceRow) {
        if (saving) return
        saving = true
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    app.topilmalarRepository.createManualCard(source.id, text)
                    detected?.contextKey()?.let { prefs.rememberContextSource(it, source.id) }
                }
            }
            saving = false
            if (result.isSuccess) onSaved() else onError()
        }
    }

    fun saveDetected(metadata: TopilmaExternalCapture.Metadata) {
        if (saving) return
        saving = true
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val saved = TopilmaExternalCapture.save(applicationContext = context.applicationContext, text = text, metadata = metadata)
                    val key = metadata.contextKey()
                    if (saved.sourceId != null && key != null) prefs.rememberContextSource(key, saved.sourceId)
                    saved
                }
            }
            saving = false
            if (result.isSuccess) onSaved() else onError()
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 10.dp
    ) {
        if (showAll) {
            AllSourcesPanel(
                sources = sources,
                query = query,
                onQuery = { query = it },
                onBack = { showAll = false },
                onSelect = ::saveToSource,
                saving = saving
            )
        } else {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (language == AppLanguage.UZ) "Topilmaga saqlash" else if (language == AppLanguage.RU) "Сохранить находку" else "Save to Topilmalar", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (language == AppLanguage.UZ) "Manbani tanlang" else if (language == AppLanguage.RU) "Выберите источник" else "Select a source",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, contentDescription = "Yopish") }
                }

                if (loading) {
                    Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                    }
                    return@Column
                }

                // The selected-app setting controls fast source suggestions only.
                // Explicit Android PROCESS_TEXT remains usable from every application.
                val meaningfulDetected = if (isPreferredOrigin) {
                    detected?.takeIf { it.isMeaningfulSourceSuggestion() }
                        ?: detected?.takeIf { sources.isEmpty() && !it.sourceTitle.isNullOrBlank() }
                } else null
                if (meaningfulDetected != null) {
                    val mapped = mappedDetectedSource
                    Surface(
                        onClick = {
                            if (mapped != null) saveToSource(mapped) else saveDetected(meaningfulDetected)
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        shape = RoundedCornerShape(15.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = .08f)
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                Text(
                                    if (mapped == null) "Joriy manba · Yangi manba" else "Joriy manba",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    mapped?.title ?: meaningfulDetected.sourceTitle.orEmpty(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.Medium
                                )
                                val sub = mapped?.author ?: meaningfulDetected.author ?: meaningfulDetected.sourceUri
                                sub?.takeIf { it.isNotBlank() }?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }

                val primary = sources.firstOrNull()
                if (primary != null) {
                    Text(
                        "Oxirgi manba",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)
                    )
                    PrimarySourceTile(primary, enabled = !saving, onClick = { saveToSource(primary) })
                }

                val recent = sources.drop(1).take(5)
                if (recent.isNotEmpty()) {
                    Text(
                        "Yaqindagi manbalar",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 14.dp, bottom = 7.dp)
                    )
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        recent.forEach { source ->
                            CompactSourceTile(source, enabled = !saving, onClick = { saveToSource(source) })
                        }
                    }
                }

                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { showAll = true }, enabled = sources.isNotEmpty() && !saving) {
                        Text(if (language == AppLanguage.UZ) "Barcha manbalar" else if (language == AppLanguage.RU) "Все источники" else "All sources")
                    }
                }
            }
        }
    }
}

@Composable
private fun PrimarySourceTile(source: TopilmaSourceRow, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .42f)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            SourceArt(source, Modifier.size(width = 62.dp, height = 76.dp), radius = 11)
            Column(Modifier.weight(1f).padding(start = 13.dp)) {
                Text(source.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                source.author?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun CompactSourceTile(source: TopilmaSourceRow, enabled: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.size(width = 68.dp, height = 92.dp).clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SourceArt(source, Modifier.size(width = 48.dp, height = 60.dp), radius = 8)
        Text(
            source.title,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun SourceArt(source: TopilmaSourceRow, modifier: Modifier, radius: Int) {
    val context = LocalContext.current
    val relative = source.coverPath ?: source.originAppIconPath
    val bitmap by produceState<Bitmap?>(null, relative) {
        value = relative?.let { path ->
            withContext(Dispatchers.IO) {
                runCatching { decodeThumb(TopilmalarMediaStorage(context).resolve(path), 220) }.getOrNull()
            }
        }
    }
    Surface(modifier = modifier, shape = RoundedCornerShape(radius.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .10f)) {
        if (bitmap != null) {
            Image(bitmap!!.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Book, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun AllSourcesPanel(
    sources: List<TopilmaSourceRow>,
    query: String,
    onQuery: (String) -> Unit,
    onBack: () -> Unit,
    onSelect: (TopilmaSourceRow) -> Unit,
    saving: Boolean
) {
    val filtered = remember(sources, query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) sources else sources.filter { it.title.lowercase().contains(q) || it.author.orEmpty().lowercase().contains(q) }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Orqaga") }
            Text("Barcha manbalar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            placeholder = { Text("Manbani qidiring") }
        )
        LazyColumn(modifier = Modifier.height(360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(filtered, key = { it.id }) { source ->
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable(enabled = !saving) { onSelect(source) },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .30f)
                ) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        SourceArt(source, Modifier.size(width = 38.dp, height = 48.dp), radius = 7)
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(source.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            source.author?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                }
            }
        }
    }
}

private fun decodeThumb(file: File, target: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > target * 2 || bounds.outHeight / sample > target * 2) sample *= 2
    return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
}
