package com.memora.app.ui.screens

import com.memora.app.ui.components.MemoraDialog as AlertDialog
import com.memora.app.ui.components.MemoraSheet
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.TextSnippet
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.produceState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.memora.app.data.local.NoteEntity
import com.memora.app.data.repository.MemoraRepository
import com.memora.app.data.model.NoteBlock
import com.memora.app.data.model.NoteBlocksCodec
import com.memora.app.data.model.NoteMood
import com.memora.app.i18n.L
import com.memora.app.i18n.S
import com.memora.app.media.MediaStorage
import com.memora.app.prefs.AppLanguage
import com.memora.app.prefs.FontChoice
import com.memora.app.ui.components.AudioBlockView
import com.memora.app.ui.components.ImageBlockView
import com.memora.app.ui.components.HashtagChips
import com.memora.app.ui.components.FloatingNoteChrome
import com.memora.app.ui.components.NoteDatesFooter
import com.memora.app.ui.components.NotePaper
import com.memora.app.ui.components.NoteStatusBarEffect
import com.memora.app.ui.components.stableNoteStatusInset
import com.memora.app.ui.components.FloatingNoteControl
import com.memora.app.ui.components.rememberNoteChromeVisibility
import com.memora.app.ui.components.showNoteChromeOnTap
import com.memora.app.ui.components.MemoraConfirmationDialog
import com.memora.app.ui.components.ReaderSelectionParagraph
import com.memora.app.ui.components.ReaderActionsLabels
import com.memora.app.ui.components.SelectionExportLabels
import com.memora.app.ui.components.MoodSticker
import com.memora.app.ui.theme.MemoraDesign
import com.memora.app.ui.theme.ParagraphMetrics
import com.memora.app.ui.theme.fontFamily
import com.memora.app.util.HashtagUtils
import com.memora.app.util.TimeUtils
import com.memora.app.share.ReaderShareManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.memora.app.performance.MemoraPerformance

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    note: NoteEntity?,
    repository: MemoraRepository,
    language: AppLanguage,
    writingFont: FontChoice,
    canEdit: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onWriteRequired: () -> Unit
) {
    if (note == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            FloatingNoteChrome(visible = true, backLabel = readerBackLabel(language), onBack = onBack) {}
        }
        return
    }

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val storage = remember { MediaStorage(context.applicationContext) }
    // JSON decoding can be expensive for long notes. Do not execute it on the UI thread.
    // Cancellation on changed note JSON discards stale work; renderer observes the final result.
    val blocks by produceState<List<NoteBlock>>(
        initialValue = emptyList(),
        key1 = note.id,
        key2 = note.blocksJson,
        key3 = note.plainText
    ) {
        val json = note.blocksJson
        val fallback = note.plainText
        value = withContext(Dispatchers.Default) {
            MemoraPerformance.traced("reader_decode") { NoteBlocksCodec.decode(json, fallback) }
        }
    }
    val noteMood = remember(note.mood) { NoteMood.fromStored(note.mood) }
    val density = LocalDensity.current
    val paragraphGap = with(density) { (ParagraphMetrics.READER_LINE_HEIGHT_SP * ParagraphMetrics.GAP_RATIO).sp.toDp() }
    var shareError by remember { mutableStateOf<String?>(null) }
    var selectedImageFiles by remember { mutableStateOf<List<java.io.File>>(emptyList()) }
    var selectionPage by remember { mutableStateOf(0) }
    var selectionImageLoading by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }
    val shareManager = remember(context) { ReaderShareManager(context) }
    val shareScope = rememberCoroutineScope()
    val selectionLabels = SelectionExportLabels.forLanguage(language)
    val selectionImageSave = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("image/png")
    ) { uri ->
        val file = selectedImageFiles.singleOrNull()
        if (uri != null && file != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { input -> input.copyTo(out) }
                } ?: error("Cannot open image destination")
            }.onSuccess {
                android.widget.Toast.makeText(context, selectionLabels.saved, android.widget.Toast.LENGTH_SHORT).show()
            }.onFailure { shareError = it.message ?: "Save failed" }
        }
    }
    val selectionZipSave = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null && selectedImageFiles.isNotEmpty()) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    java.util.zip.ZipOutputStream(stream).use { zip ->
                        selectedImageFiles.forEachIndexed { index, file ->
                            zip.putNextEntry(java.util.zip.ZipEntry("Memora-${index + 1}.png"))
                            file.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                } ?: error("Cannot open ZIP destination")
            }.onSuccess {
                android.widget.Toast.makeText(context, selectionLabels.savedMultiple, android.widget.Toast.LENGTH_SHORT).show()
            }.onFailure { shareError = it.message ?: "Save failed" }
        }
    }

    val favoritePrefs = remember(context) {
        context.getSharedPreferences("memora_reader_favorites", 0)
    }
    var favorite by remember(note.id) { mutableStateOf(favoritePrefs.getBoolean(note.id, false)) }
    val imageCount = remember(blocks) { blocks.count { it is NoteBlock.Image } }
    val audioCount = remember(blocks) { blocks.count { it is NoteBlock.Audio } }
    val hashtags = remember(note.hashtagsJson) { HashtagUtils.decode(note.hashtagsJson) }
    val readerScroll = rememberLazyListState()
    val chromeVisible = rememberNoteChromeVisibility(readerScroll)
    val actionLabels = ReaderActionsLabels.forLanguage(language)
    NoteStatusBarEffect(readerScroll)
    // Preserve approximate viewport position when transitioning from reading to editing.
    val editorPositions = remember(context) { context.getSharedPreferences("memora_editor_position", 0) }
    fun openEditorAtCurrentPosition() {
        if (!canEdit) {
            onWriteRequired()
            return
        }
        val count = readerScroll.layoutInfo.totalItemsCount.coerceAtLeast(1)
        val fraction = (readerScroll.firstVisibleItemIndex.toFloat() / count)
            .coerceIn(0f, 1f)
        editorPositions.edit().putFloat("${note.id}.readerFraction", fraction).apply()
        onEdit()
    }

    Box(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .showNoteChromeOnTap { chromeVisible.value = true }
    ) {
        // A single scrollable Paper document. LazyColumn only composes visible paragraphs,
        // so opening a 1,000-paragraph note does not allocate 1,000 Compose text layouts.
        LazyColumn(
            state = readerScroll,
            modifier = Modifier.fillMaxSize()
                .semantics { testTagsAsResourceId = true }
                .testTag("memora_reader_content"),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)
        ) {
            item("paper-top-spacing") { Spacer(Modifier.height(stableNoteStatusInset() + 66.dp)) }
            item("paper-title") {
                ReaderPaperSegment(top = true) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = note.title.ifBlank { L.t(language, S.UNTITLED) },
                                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 39.sp, lineHeight = 48.sp),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        noteMood?.let { mood ->
                            Surface(
                                shape = RoundedCornerShape(MemoraDesign.radiusMedium),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .52f),
                                modifier = Modifier.padding(start = 12.dp)
                            ) {
                                Column(Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally) {
                                    MoodSticker(mood = mood,
                                        description = readerMoodName(language, mood),
                                        modifier = Modifier.size(42.dp))
                                    Text(readerMoodName(language, mood),
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(top = 5.dp))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                }
            }
            itemsIndexed(blocks, key = { _, block -> block.id }) { index, block ->
                ReaderPaperSegment {
                    when (block) {
                        is NoteBlock.Text -> if (block.text.isNotBlank()) {
                            ReaderSelectionParagraph(
                                block = block,
                                fontChoice = writingFont,
                                language = language,
                                query = "",
                                focusedMatchOffset = null,
                                canHighlight = canEdit,
                                onHighlight = { blockId, start, end, shade ->
                                    shareScope.launch {
                                        val saved = runCatching {
                                            repository.setNoteHighlight(note.id, blockId, start, end, shade)
                                        }.getOrDefault(false)
                                        if (!saved) shareError = if (language == AppLanguage.UZ)
                                            "Marker belgisi saqlanmadi. Qayta urinib ko‘ring."
                                        else "Highlight could not be saved. Try again."
                                    }
                                },
                                onShare = { selected, asImage ->
                                    if (asImage) shareScope.launch {
                                        selectionImageLoading = true
                                        runCatching { shareManager.renderSelectionImages(selected) }
                                            .onSuccess { files ->
                                                selectedImageFiles = files
                                                selectionPage = 0
                                            }
                                            .onFailure { shareError = it.message ?: "Image export failed" }
                                        selectionImageLoading = false
                                    } else shareManager.shareSelection(selected, readerShareLabel(language))
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        is NoteBlock.Image -> ImageBlockView(
                            block = block,
                            file = storage.resolve(block.relativePath),
                            editable = false
                        )
                        is NoteBlock.Audio -> AudioBlockView(
                            block = block,
                            file = storage.resolve(block.relativePath),
                            editable = false,
                            language = language
                        )
                    }
                    val next = blocks.getOrNull(index + 1)
                    if (next != null) {
                        val gap = if (block is NoteBlock.Text && next is NoteBlock.Text) paragraphGap else 14.dp
                        Spacer(Modifier.height(gap))
                    }
                }
            }
            item("paper-footer") {
                ReaderPaperSegment(bottom = true) {
                    if (imageCount > 0 || audioCount > 0 || noteMood != null) {
                        HorizontalDivider(
                            modifier = Modifier.padding(top = 18.dp, bottom = 14.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .08f)
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (imageCount > 0) ReaderSummaryChip(
                                Icons.Rounded.Image, readerImageCount(language, imageCount), Modifier.weight(1f))
                            if (audioCount > 0) ReaderSummaryChip(
                                Icons.Rounded.Mic, readerAudioCount(language, audioCount), Modifier.weight(1f))
                            noteMood?.let { mood -> ReaderMoodSummaryChip(language, mood, Modifier.weight(1.35f)) }
                        }
                    }
                    HashtagChips(tags = hashtags, modifier = Modifier.padding(top = 14.dp))
                    NoteDatesFooter(
                        createdLabel = readerWrittenText(language, note.createdAt),
                        editedLabel = if (note.updatedAt > note.createdAt + 1000L)
                            readerEditedText(language, note.updatedAt) else null
                    )
                }
            }
            item("reader-actions") {
                Column {
                    Spacer(Modifier.height(14.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(MemoraDesign.radiusLarge),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = .96f),
                        shadowElevation = MemoraDesign.shadowSubtle
                    ) {
                        Row(Modifier.fillMaxWidth()) {
                            ReaderAction(
                                icon = if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                label = readerFavoriteLabel(language),
                                onClick = {
                                    favorite = !favorite
                                    favoritePrefs.edit().putBoolean(note.id, favorite).apply()
                                },
                                modifier = Modifier.weight(1f)
                            )
                            ReaderAction(
                                icon = Icons.Rounded.ContentCopy,
                                label = actionLabels.copy,
                                onClick = { clipboard.setText(AnnotatedString(note.plainText)) },
                                modifier = Modifier.weight(1f)
                            )
                            ReaderAction(
                                icon = Icons.Rounded.Share,
                                label = readerShareLabel(language),
                                onClick = { showShareSheet = true },
                                modifier = Modifier.weight(1f)
                            )
                            ReaderAction(
                                icon = Icons.Rounded.DeleteOutline,
                                label = L.t(language, S.DELETE),
                                onClick = { if (canEdit) confirmDelete = true else onWriteRequired() },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(Modifier.height(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 26.dp))
                }
            }
        }
        FloatingNoteChrome(
            visible = chromeVisible.value,
            backLabel = readerBackLabel(language),
            onBack = onBack
        ) {
            FloatingNoteControl(
                icon = Icons.Rounded.Edit,
                description = readerEditLabel(language),
                onClick = ::openEditorAtCurrentPosition
            )
        }
    }

    if (confirmDelete) {
        MemoraConfirmationDialog(
            language = language,
            title = if (language == AppLanguage.UZ) "Yozuvni o‘chirish" else "Delete note",
            text = if (language == AppLanguage.UZ) "Bu yozuv qayta tiklanmaydi. O‘chirishni tasdiqlaysizmi?" else "This note cannot be restored. Delete it?",
            onConfirm = { confirmDelete = false; onDelete() },
            onDismiss = { confirmDelete = false }
        )
    }

    if (shareError != null) {
        AlertDialog(
            onDismissRequest = { shareError = null },
            title = { Text(selectionLabels.share) },
            text = { Text(shareError.orEmpty()) },
            confirmButton = { TextButton(onClick = { shareError = null }) { Text("OK") } }
        )
    }
    if (selectionImageLoading) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { },
            title = { Text(selectionLabels.preparing) },
            text = { androidx.compose.material3.CircularProgressIndicator() },
            confirmButton = {}
        )
    }
    if (selectedImageFiles.isNotEmpty()) {
        val file = selectedImageFiles.getOrNull(selectionPage)
        val bitmap = remember(file) {
            file?.let { android.graphics.BitmapFactory.decodeFile(it.absolutePath)?.asImageBitmap() }
        }
        AlertDialog(
            onDismissRequest = { selectedImageFiles = emptyList() },
            title = { Text(selectionLabels.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    bitmap?.let { image ->
                        androidx.compose.foundation.Image(
                            bitmap = image,
                            contentDescription = "Memora Paper",
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)
                        )
                    }
                    if (selectedImageFiles.size > 1) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${selectionPage + 1} / ${selectedImageFiles.size}", modifier = Modifier.weight(1f))
                            IconButton(onClick = { selectionPage = (selectionPage - 1 + selectedImageFiles.size) % selectedImageFiles.size }) {
                                Icon(Icons.Rounded.ChevronLeft, contentDescription = null)
                            }
                            IconButton(onClick = { selectionPage = (selectionPage + 1) % selectedImageFiles.size }) {
                                Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                            }
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedImageFiles = emptyList() }) {
                    Text(selectionLabels.close)
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        val stamp = System.currentTimeMillis()
                        if (selectedImageFiles.size == 1) selectionImageSave.launch("Memora-$stamp.png")
                        else selectionZipSave.launch("Memora-$stamp.zip")
                    }) { Text(selectionLabels.save) }
                    TextButton(onClick = {
                        runCatching { shareManager.shareSelectionFiles(selectedImageFiles, readerShareLabel(language)) }
                            .onFailure { shareError = it.message ?: "Share failed" }
                    }) { Text(selectionLabels.share) }
                }
            }
        )
    }
    if (showShareSheet) {
        MemoraSheet(onDismissRequest = { showShareSheet = false }) {
            Column(Modifier.fillMaxWidth().heightIn(max = 630.dp).verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, bottom = 26.dp)) {
                Text(readerShareLabel(language), style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 10.dp))
                // The same content hierarchy as the NotePaper reader, scaled for preview.
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    shape = RoundedCornerShape(15.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                ) {
                    Column(Modifier.padding(10.dp)) {
                        NotePaper {
                            Text(note.title, style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            Text(note.plainText.take(220) + if (note.plainText.length > 220) "…" else "",
                                style = MaterialTheme.typography.bodySmall)
                            NoteDatesFooter(createdLabel = readerWrittenText(language, note.createdAt),
                                editedLabel = if (note.updatedAt > note.createdAt + 1000) readerEditedText(language, note.updatedAt) else null)
                        }
                        Text("Memora Paper · ${actionLabels.preview}", style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 4.dp))
                    }
                }
                ShareSheetAction(Icons.Rounded.TextSnippet, readerShareTextLabel(language)) {
                    showShareSheet = false
                    shareManager.shareText(note, readerShareLabel(language))
                }
                ShareSheetAction(Icons.Rounded.Image, readerShareImageLabel(language)) {
                    showShareSheet = false
                    shareScope.launch { runCatching { shareManager.shareImages(note, language, readerShareLabel(language)) }
                        .onFailure { shareError = it.message ?: "Share failed" } }
                }
                ShareSheetAction(Icons.Rounded.PictureAsPdf, actionLabels.pdf) {
                    showShareSheet = false
                    shareScope.launch { runCatching { shareManager.sharePdf(note, language, readerShareLabel(language)) }
                        .onFailure { shareError = it.message ?: "Share failed" } }
                }
                ShareSheetAction(Icons.Rounded.Code, "HTML") {
                    showShareSheet = false
                    shareScope.launch { runCatching { shareManager.shareHtml(note, language, readerShareLabel(language)) }
                        .onFailure { shareError = it.message ?: "Share failed" } }
                }
            }
        }
    }

}

/** A visually continuous Paper card with individually virtualized blocks. */
@Composable
private fun ReaderPaperSegment(
    top: Boolean = false,
    bottom: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(
            topStart = if (top) 18.dp else 0.dp,
            topEnd = if (top) 18.dp else 0.dp,
            bottomStart = if (bottom) 18.dp else 0.dp,
            bottomEnd = if (bottom) 18.dp else 0.dp
        )
    ) {
        Column(
            Modifier.fillMaxWidth().padding(
                start = 18.dp, end = 18.dp,
                top = if (top) 22.dp else 0.dp,
                bottom = if (bottom) 22.dp else 0.dp
            ), content = content
        )
    }
}

@Composable
private fun ReaderSummaryChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(MemoraDesign.radiusMedium),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .50f)
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 7.dp))
        }
    }
}

@Composable
private fun ReaderMoodSummaryChip(language: AppLanguage, mood: NoteMood, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(MemoraDesign.radiusMedium),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .50f)
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            MoodSticker(mood, readerMoodName(language, mood), Modifier.size(24.dp))
            Text(
                "${readerMoodLabel(language)}: ${readerMoodName(language, mood)}",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 7.dp)
            )
        }
    }
}

@Composable
private fun ReaderAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 15.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 5.dp)
        )
    }
}

@Composable
private fun ShareSheetAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(MemoraDesign.radiusMedium),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .34f)
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 14.dp))
        }
    }
}

private fun readerShareTextLabel(l: AppLanguage) = if (l == AppLanguage.UZ) "Matn" else if (l == AppLanguage.RU) "Текст" else "Text"
private fun readerShareImageLabel(l: AppLanguage) = if (l == AppLanguage.UZ) "Rasm" else if (l == AppLanguage.RU) "Изображение" else "Image"

private fun readerEditLabel(l: AppLanguage) = if (l == AppLanguage.UZ) "Tahrirlash" else if (l == AppLanguage.RU) "Изменить" else "Edit"
private fun readerFavoriteLabel(l: AppLanguage) = if (l == AppLanguage.UZ) "Sevimlilarga" else if (l == AppLanguage.RU) "Избранное" else "Favorite"
private fun readerShareLabel(l: AppLanguage) = if (l == AppLanguage.UZ) "Ulashish" else if (l == AppLanguage.RU) "Поделиться" else "Share"
private fun readerMoodLabel(l: AppLanguage) = if (l == AppLanguage.UZ) "Kayfiyat" else if (l == AppLanguage.RU) "Настроение" else "Mood"
private fun readerImageCount(l: AppLanguage, n: Int) = if (l == AppLanguage.UZ) "$n rasm" else "$n images"
private fun readerAudioCount(l: AppLanguage, n: Int) = if (l == AppLanguage.UZ) "$n audio" else "$n audio"
private fun readerWrittenText(l: AppLanguage, time: Long) = when (l) {
    AppLanguage.UZ -> "Yozilgan: ${TimeUtils.dateTime(time, l)}"
    AppLanguage.RU -> "Создано: ${TimeUtils.dateTime(time, l)}"
    else -> "Written: ${TimeUtils.dateTime(time, l)}"
}
private fun readerEditedText(l: AppLanguage, time: Long) = when (l) {
    AppLanguage.UZ -> "Tahrirlandi: ${TimeUtils.dateTime(time, l)}"
    AppLanguage.RU -> "Изменено: ${TimeUtils.dateTime(time, l)}"
    else -> "Edited: ${TimeUtils.dateTime(time, l)}"
}
private fun readerMoodName(l: AppLanguage, mood: NoteMood): String = when (mood) {
    NoteMood.GREAT -> if (l == AppLanguage.UZ) "Zo‘r" else "Great"
    NoteMood.GOOD -> if (l == AppLanguage.UZ) "Yaxshi" else "Good"
    NoteMood.NEUTRAL -> if (l == AppLanguage.UZ) "O‘rtacha" else "Neutral"
    NoteMood.BAD -> if (l == AppLanguage.UZ) "Yomon" else "Bad"
    NoteMood.VERY_BAD -> if (l == AppLanguage.UZ) "Juda yomon" else "Very bad"
}

private fun readerBackLabel(language: AppLanguage) = when (language) {
    AppLanguage.UZ -> "Orqaga"
    AppLanguage.RU -> "Назад"
    else -> "Back"
}
private fun readerMoreLabel(language: AppLanguage) = when (language) {
    AppLanguage.UZ -> "Boshqa amallar"
    AppLanguage.RU -> "Ещё"
    else -> "More actions"
}
