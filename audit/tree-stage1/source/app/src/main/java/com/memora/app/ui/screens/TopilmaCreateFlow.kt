package com.memora.app.ui.screens

import com.memora.app.ui.components.MemoraDialog as AlertDialog
import com.memora.app.ui.components.MemoraSheet
import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.memora.app.data.local.TopilmaSourceRow
import com.memora.app.data.local.TopilmaSourceType
import com.memora.app.data.repository.TopilmalarRepository
import com.memora.app.media.TopilmalarMediaStorage
import com.memora.app.ui.components.HashtagVisualTransformation
import com.memora.app.ui.theme.MemoraDesign
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Direct Topilma composer. The last-used source is supplied by the parent and can be changed
 * without leaving the writing surface. Source and copy actions intentionally live in compact
 * sheets so the writing area stays visually quiet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopilmaComposerScreen(
    sources: List<TopilmaSourceRow>,
    selectedSourceId: String?,
    text: String,
    cardId: String?,
    repository: TopilmalarRepository,
    canEdit: Boolean,
    onSourceSelected: (String) -> Unit,
    onTextChange: (String) -> Unit,
    onCardIdChange: (String?) -> Unit,
    onCamera: () -> Unit,
    onGalleryFile: (File) -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = context as? Activity
    val selectedSource = remember(sources, selectedSourceId) { sources.firstOrNull { it.id == selectedSourceId } }
    val saveMutex = remember { Mutex() }
    var saving by remember { mutableStateOf(false) }
    var sourceSheet by remember { mutableStateOf(false) }
    var copySheet by remember { mutableStateOf(false) }
    var showCreateSource by remember { mutableStateOf(false) }
    var pendingCreatedSourceId by remember { mutableStateOf<String?>(null) }
    var copyError by remember { mutableStateOf<String?>(null) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var leaving by remember { mutableStateOf(false) }

    LaunchedEffect(sources, pendingCreatedSourceId) {
        val pending = pendingCreatedSourceId ?: return@LaunchedEffect
        if (sources.any { it.id == pending }) {
            onSourceSelected(pending)
            pendingCreatedSourceId = null
            sourceSheet = false
        }
    }

    suspend fun persist(snapshotText: String = text, sourceId: String? = selectedSourceId) {
        if (!canEdit || sourceId == null) return
        saveMutex.withLock {
            withContext(NonCancellable) {
                val clean = snapshotText.trim()
                if (clean.isBlank()) {
                    cardId?.let { repository.softDelete(listOf(it)) }
                    if (cardId != null) onCardIdChange(null)
                    saving = false
                    return@withContext
                }
                saving = true
                val currentId = cardId
                if (currentId == null) {
                    onCardIdChange(repository.createManualCard(sourceId, clean))
                } else {
                    val existing = repository.card(currentId)
                    if (existing != null) {
                        repository.updateCard(existing, clean)
                        if (existing.sourceId != sourceId) repository.moveCards(listOf(currentId), sourceId)
                    }
                }
                saving = false
            }
        }
    }

    LaunchedEffect(text, selectedSourceId) {
        if (text.isBlank() && cardId == null) return@LaunchedEffect
        if (selectedSourceId == null) return@LaunchedEffect
        delay(700)
        persist()
    }

    fun finish() {
        if (leaving) return
        leaving = true
        scope.launch {
            val result = runCatching { persist() }
            if (result.isFailure) {
                saveError = "Saqlashda xato. Qayta urinib ko‘ring."
                leaving = false
                return@launch
            }
            // createManualCard/updateCard already enqueue Obsidian sync in the repository.
            // Do not force synchronous Obsidian I/O here: Back must return immediately after
            // the safe local write completes.
            onBack()
        }
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val file = withContext(Dispatchers.IO) { copyUriToComposerOcrCache(context, uri) }
                if (file != null) {
                    copySheet = false
                    onGalleryFile(file)
                } else copyError = "Rasmni ochib bo‘lmadi."
            }
        }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            copySheet = false
            onCamera()
        } else {
            copyError = if (activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)) {
                "Kamera ruxsati telefon sozlamalarida o‘chirilgan."
            } else "Kameradan foydalanish uchun ruxsat kerak."
        }
    }

    BackHandler {
        when {
            showCreateSource -> showCreateSource = false
            copySheet -> copySheet = false
            sourceSheet -> sourceSheet = false
            else -> finish()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 10.dp, top = 6.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = ::finish) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Orqaga") }
            Surface(
                onClick = { sourceSheet = true },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(MemoraDesign.radiusMedium),
                color = MaterialTheme.colorScheme.primary.copy(alpha = .065f)
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectedSource != null) {
                        SourcePickerCover(selectedSource, Modifier.size(width = 30.dp, height = 38.dp))
                    } else {
                        Surface(
                            shape = RoundedCornerShape(7.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = .12f),
                            modifier = Modifier.size(width = 30.dp, height = 38.dp)
                        ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Book, null, modifier = Modifier.size(18.dp)) } }
                    }
                    Column(Modifier.weight(1f).padding(start = 9.dp)) {
                        Text(
                            selectedSource?.title ?: "Manba tanlang",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        selectedSource?.author?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                    Text("⌄", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = { copyError = null; copySheet = true }) {
                Icon(Icons.Rounded.DocumentScanner, contentDescription = "Skaner", tint = MaterialTheme.colorScheme.primary)
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                when {
                    saving -> "Saqlanmoqda…"
                    cardId != null -> "Saqlandi"
                    else -> "Autosave"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (selectedSource == null) {
                Text("Avval manba tanlang", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }

        saveError?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
            )
        }

        TextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = { Text("Topilmangizni yozing…  #teg", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 6.dp),
            enabled = canEdit,
            minLines = 12,
            textStyle = MaterialTheme.typography.bodyLarge,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            visualTransformation = HashtagVisualTransformation(
                color = MaterialTheme.colorScheme.primary,
                background = MaterialTheme.colorScheme.primary.copy(alpha = .10f)
            ),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            )
        )
    }

    if (sourceSheet) {
        TopilmaSourcePickerSheet(
            sources = sources,
            selectedSourceId = selectedSourceId,
            canEdit = canEdit,
            onDismiss = { sourceSheet = false },
            onSelect = {
                onSourceSelected(it.id)
                sourceSheet = false
            },
            onCreate = { showCreateSource = true }
        )
    }

    if (copySheet) {
        MemoraSheet(onDismissRequest = { copySheet = false }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
                Text("Nusxalash", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "Kitob yoki rasmdagi kerakli parchani ko‘chiring",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp, bottom = 12.dp)
                )
                CopyActionRow(Icons.Rounded.DocumentScanner, "Skaner") {
                    copySheet = false
                    onCamera()
                }
                CopyActionRow(Icons.Rounded.PhotoLibrary, "Galereya") {
                    gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                copyError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
                Spacer(Modifier.height(22.dp))
            }
        }
    }

    if (showCreateSource) {
        CreateSourceDialog(
            repository = repository,
            onDismiss = { showCreateSource = false },
            onCreated = { id ->
                pendingCreatedSourceId = id
                showCreateSource = false
            }
        )
    }
}

@Composable
private fun CopyActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(MemoraDesign.radiusMedium),
        color = MaterialTheme.colorScheme.primary.copy(alpha = .065f)
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(MemoraDesign.radiusSmall), color = MaterialTheme.colorScheme.primary.copy(alpha = .12f)) {
                Box(Modifier.padding(9.dp)) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) }
            }
            Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 13.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopilmaSourcePickerSheet(
    sources: List<TopilmaSourceRow>,
    selectedSourceId: String?,
    canEdit: Boolean,
    onDismiss: () -> Unit,
    onSelect: (TopilmaSourceRow) -> Unit,
    onCreate: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val visible = remember(sources, query) {
        val needle = query.trim().lowercase()
        sources.sortedByDescending { it.lastAddedAt }
            .filter { needle.isBlank() || it.title.lowercase().contains(needle) || it.author.orEmpty().lowercase().contains(needle) }
    }
    MemoraSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
            Text("Manba", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Manba yoki muallifni qidiring") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp)
            )
            if (visible.isEmpty()) {
                Text(
                    if (sources.isEmpty()) "Hozircha manba yo‘q" else "Mos manba topilmadi",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 18.dp).align(Alignment.CenterHorizontally)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    contentPadding = PaddingValues(bottom = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    items(visible, key = { it.id }) { source ->
                        SourcePickerRow(source, selected = source.id == selectedSourceId) { onSelect(source) }
                    }
                }
            }
            Surface(
                onClick = { if (canEdit) onCreate() },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 18.dp),
                shape = RoundedCornerShape(MemoraDesign.radiusMedium),
                color = MaterialTheme.colorScheme.primary.copy(alpha = .085f)
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = .14f), modifier = Modifier.size(32.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Add, "Yangi manba", modifier = Modifier.size(19.dp)) }
                    }
                    Text("Yangi manba", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 11.dp))
                }
            }
        }
    }
}

@Composable
private fun SourcePickerRow(source: TopilmaSourceRow, selected: Boolean = false, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(MemoraDesign.radiusMedium),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .10f) else MaterialTheme.colorScheme.surface
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            SourcePickerCover(source, Modifier.size(width = 40.dp, height = 50.dp))
            Column(Modifier.weight(1f).padding(start = 11.dp)) {
                Text(source.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                source.author?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Text(source.cardCount.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SourcePickerCover(source: TopilmaSourceRow, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val path = source.coverPath
    val bitmap by produceState<android.graphics.Bitmap?>(null, path) {
        value = path?.let { relativePath ->
            withContext(Dispatchers.IO) {
                runCatching { decodeSourcePickerThumbnail(TopilmalarMediaStorage(context).resolve(relativePath), 240) }.getOrNull()
            }
        }
    }
    Surface(shape = RoundedCornerShape(7.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .10f), modifier = modifier) {
        if (bitmap != null) Image(bitmap!!.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Book, null, tint = MaterialTheme.colorScheme.primary) }
    }
}

@Composable
private fun CreateSourceDialog(
    repository: TopilmalarRepository,
    onDismiss: () -> Unit,
    onCreated: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var uri by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(TopilmaSourceType.BOOK) }
    var typeMenu by remember { mutableStateOf(false) }
    var coverUri by remember { mutableStateOf<Uri?>(null) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { coverUri = it }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yangi manba") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Nomi") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(author, { author = it }, label = { Text("Muallif / yaratuvchi") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(uri, { uri = it }, label = { Text("URL / URI (ixtiyoriy)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Box {
                    OutlinedButton(onClick = { typeMenu = true }) { Text(createSourceTypeLabel(type)) }
                    DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                        TopilmaSourceType.entries.forEach { option ->
                            DropdownMenuItem(text = { Text(createSourceTypeLabel(option)) }, onClick = { type = option; typeMenu = false })
                        }
                    }
                }
                OutlinedButton(onClick = { gallery.launch("image/*") }) {
                    Icon(Icons.Rounded.PhotoLibrary, null)
                    Text(if (coverUri == null) " Muqova rasmini tanlash" else " Muqova tanlandi ✓")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        val id = repository.createSource(
                            title = title,
                            author = author.ifBlank { null },
                            type = type,
                            uri = uri.ifBlank { null }
                        )
                        coverUri?.let { selected ->
                            runCatching {
                                TopilmalarMediaStorage(context).storeSharedImage(selected, context.contentResolver.getType(selected)).relativePath
                            }.getOrNull()?.let { repository.setSourceCover(id, it) }
                        }
                        onCreated(id)
                    }
                },
                enabled = title.isNotBlank()
            ) { Text("Qo‘shish") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Bekor qilish") } }
    )
}

private fun createSourceTypeLabel(type: TopilmaSourceType) = when (type) {
    TopilmaSourceType.BOOK -> "Kitob"
    TopilmaSourceType.ARTICLE_WEBSITE -> "Maqola / sayt"
    TopilmaSourceType.CHAT -> "Chat"
    TopilmaSourceType.SOCIAL -> "Ijtimoiy tarmoq"
    TopilmaSourceType.DOCUMENT -> "Hujjat"
    TopilmaSourceType.MESSAGE -> "Xabar"
    TopilmaSourceType.OTHER -> "Boshqa"
}

private fun decodeSourcePickerThumbnail(file: File, target: Int): android.graphics.Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > target * 2 || bounds.outHeight / sample > target * 2) sample *= 2
    return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
}

private fun copyUriToComposerOcrCache(context: android.content.Context, uri: Uri): File? =
    com.memora.app.share.OcrCacheStorage.copyUri(context, uri)
