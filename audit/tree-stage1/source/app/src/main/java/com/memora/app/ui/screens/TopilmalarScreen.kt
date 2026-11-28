@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.memora.app.ui.screens

import com.memora.app.ui.components.MemoraDialog as AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Merge
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memora.app.data.local.TopilmaCardRow
import com.memora.app.data.local.TopilmaReviewRating
import com.memora.app.data.local.TopilmaSourceEntity
import com.memora.app.data.local.TopilmaSourceRow
import com.memora.app.data.local.TopilmaSourceType
import com.memora.app.data.local.TopilmaTagEntity
import com.memora.app.data.model.NoteBlock
import com.memora.app.data.repository.TopilmaCaptureRequest
import com.memora.app.data.repository.TopilmaReviewSessionState
import com.memora.app.data.repository.TopilmaImageDraft
import com.memora.app.data.repository.TopilmalarRepository
import com.memora.app.media.TopilmalarMediaStorage
import com.memora.app.prefs.AppLanguage
import com.memora.app.prefs.UserPreferences
import com.memora.app.prefs.UserSettings
import com.memora.app.share.TopilmaShareManager
import com.memora.app.share.TopilmaOcrManager
import com.memora.app.ui.components.ImageBlockView
import com.memora.app.ui.components.TopilmaCard
import com.memora.app.ui.theme.MemoraDesign
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.io.File
import kotlinx.coroutines.launch

private enum class TopilmalarTab { SOURCES, ALL, REVIEW }
private enum class SourceSort { RECENT, AZ, MOST, NEWEST }
private enum class TopilmaCreateStep { COMPOSER, CAMERA, OCR }

@Composable
fun TopilmalarScreen(
    repository: TopilmalarRepository,
    preferences: UserPreferences,
    settings: UserSettings,
    canEdit: Boolean,
    onRequestNotifications: () -> Unit,
    onRequestExactTiming: () -> Unit,
    onOpenComments: (String) -> Unit,
    onCreateFlowExit: () -> Unit = {},
    openReviewInitially: Boolean = false,
    openCreateInitially: Boolean = false
) {
    val language = settings.language
    val scope = rememberCoroutineScope()
    val sources by repository.observeSources().collectAsStateWithLifecycle(emptyList())
    val archivedSources by repository.observeArchivedSources().collectAsStateWithLifecycle(emptyList())
    var pageLimit by remember { mutableIntStateOf(300) }
    val allCards by remember(pageLimit) { repository.observeCards(pageLimit) }.collectAsStateWithLifecycle(emptyList())
    val allTags by repository.observeTags().collectAsStateWithLifecycle(emptyList())
    val monthStart = remember { LocalDate.now().withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }
    val stats by repository.observeStats(monthStart).collectAsStateWithLifecycle(null)
    var tab by remember(openReviewInitially) { mutableStateOf(if (openReviewInitially) TopilmalarTab.REVIEW else TopilmalarTab.SOURCES) }
    var selectedSource by remember { mutableStateOf<TopilmaSourceRow?>(null) }
    var selectedCard by remember { mutableStateOf<TopilmaCardRow?>(null) }
    var createFlow by remember(openCreateInitially) { mutableStateOf(openCreateInitially) }
    var createStep by remember(openCreateInitially) { mutableStateOf(TopilmaCreateStep.COMPOSER) }
    var createSourceId by remember { mutableStateOf<String?>(null) }
    var createDraftText by remember { mutableStateOf("") }
    var createCardId by remember { mutableStateOf<String?>(null) }
    var ocrImageFile by remember { mutableStateOf<java.io.File?>(null) }
    var ocrFromCamera by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val searchResults by remember(query) { repository.search(query) }.collectAsStateWithLifecycle(emptyList())
    var showStats by remember { mutableStateOf(false) }
    var showTags by remember { mutableStateOf(false) }
    var lastDeletedGlobal by remember { mutableStateOf<List<String>>(emptyList()) }
    var reviewStreak by remember { mutableIntStateOf(0) }
    LaunchedEffect(showStats) { if (showStats) reviewStreak = repository.reviewStreak() }

    LaunchedEffect(createFlow, sources) {
        if (createFlow && createSourceId == null && sources.isNotEmpty()) {
            createSourceId = sources.maxByOrNull { it.lastAddedAt }?.id
        }
    }

    if (createFlow) {
        fun cleanupOcrImage() {
            ocrImageFile?.let { runCatching { it.delete() } }
            ocrImageFile = null
        }
        val leaveCreateFlow: () -> Unit = {
            cleanupOcrImage()
            createSourceId = null
            createDraftText = ""
            createCardId = null
            createStep = TopilmaCreateStep.COMPOSER
            createFlow = false
            if (openCreateInitially) onCreateFlowExit()
        }
        when (createStep) {
            TopilmaCreateStep.COMPOSER -> TopilmaComposerScreen(
                sources = sources,
                selectedSourceId = createSourceId,
                text = createDraftText,
                cardId = createCardId,
                repository = repository,
                canEdit = canEdit,
                onSourceSelected = { createSourceId = it },
                onTextChange = { createDraftText = it },
                onCardIdChange = { createCardId = it },
                onCamera = {
                    cleanupOcrImage()
                    ocrFromCamera = true
                    createStep = TopilmaCreateStep.CAMERA
                },
                onGalleryFile = { file ->
                    cleanupOcrImage()
                    ocrImageFile = file
                    ocrFromCamera = false
                    createStep = TopilmaCreateStep.OCR
                },
                onBack = leaveCreateFlow
            )

            TopilmaCreateStep.CAMERA -> TopilmaCameraCaptureScreen(
                onBack = { createStep = TopilmaCreateStep.COMPOSER },
                onCaptured = { file ->
                    cleanupOcrImage()
                    ocrImageFile = file
                    ocrFromCamera = true
                    createStep = TopilmaCreateStep.OCR
                }
            )

            TopilmaCreateStep.OCR -> {
                val file = ocrImageFile
                if (file == null) createStep = TopilmaCreateStep.COMPOSER
                else TopilmaOcrSelectionScreen(
                    imageFile = file,
                    onBack = {
                        cleanupOcrImage()
                        createStep = if (ocrFromCamera) TopilmaCreateStep.CAMERA else TopilmaCreateStep.COMPOSER
                    },
                    onRetryCamera = {
                        cleanupOcrImage()
                        ocrFromCamera = true
                        createStep = TopilmaCreateStep.CAMERA
                    },
                    onReplaceImage = { replacement ->
                        cleanupOcrImage()
                        ocrImageFile = replacement
                        ocrFromCamera = false
                    },
                    onConfirm = { recognized ->
                        createDraftText = listOf(createDraftText.trim(), recognized.trim())
                            .filter { it.isNotBlank() }
                            .joinToString("\n\n")
                        cleanupOcrImage()
                        createStep = TopilmaCreateStep.COMPOSER
                    }
                )
            }
        }
        return
    }

    BackHandler(
        enabled = selectedCard != null || showStats || showTags || searchOpen || selectedSource != null
    ) {
        when {
            selectedCard != null -> selectedCard = null
            showStats -> showStats = false
            showTags -> showTags = false
            searchOpen -> { searchOpen = false; query = "" }
            selectedSource != null -> selectedSource = null
        }
    }

    if (selectedSource != null) {
        SourceDetailScreen(
            source = selectedSource!!,
            allSources = sources,
            repository = repository,
            language = language,
            canEdit = canEdit,
            onBack = { selectedSource = null },
            onCard = { selectedCard = it },
            onComments = { onOpenComments(it.id) },
            onSingleDelete = { row -> lastDeletedGlobal = listOf(row.id); scope.launch { repository.softDelete(listOf(row.id)) } },
            undoIds = lastDeletedGlobal,
            onUndo = { ids -> scope.launch { repository.undoDelete(ids) }; lastDeletedGlobal = emptyList() }
        )
    } else {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 14.dp, top = 20.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Topilmalar", style = MaterialTheme.typography.headlineLarge.copy(fontSize = 36.sp), fontWeight = FontWeight.SemiBold)
                    Text(
                        "${stats?.sourceCount ?: 0} manba · ${stats?.cardCount ?: 0} topilma",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                IconButton(onClick = { searchOpen = !searchOpen }) { Icon(if (searchOpen) Icons.Rounded.Close else Icons.Rounded.Search, null) }
                IconButton(onClick = { showStats = true }) { Icon(Icons.Rounded.BarChart, null) }
                IconButton(onClick = { showTags = true }) { Icon(Icons.Rounded.Tag, null) }
                IconButton(onClick = { createStep = TopilmaCreateStep.COMPOSER; createFlow = true }, enabled = canEdit) { Icon(Icons.Rounded.Add, null) }
            }
            if (lastDeletedGlobal.isNotEmpty()) {
                UndoDeleteBanner(lastDeletedGlobal) { ids -> scope.launch { repository.undoDelete(ids) }; lastDeletedGlobal = emptyList() }
            }
            if (searchOpen) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Manba, matn, izoh yoki teg") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)
                )
                if (query.isNotBlank()) {
                    CardList(
                        cards = searchResults,
                        repository = repository,
                        canEdit = canEdit,
                        onOpen = { selectedCard = it },
                        onComments = { onOpenComments(it.id) },
                        onOpenSource = { id -> selectedSource = sources.firstOrNull { it.id == id } },
                        onDelete = { row -> lastDeletedGlobal = listOf(row.id); scope.launch { repository.softDelete(listOf(row.id)) } },
                        modifier = Modifier.weight(1f)
                    )
                    return@Column
                }
            }
            TabRow(selectedTabIndex = tab.ordinal) {
                Tab(selected = tab == TopilmalarTab.SOURCES, onClick = { tab = TopilmalarTab.SOURCES }, text = { Text("Manbalar") })
                Tab(selected = tab == TopilmalarTab.ALL, onClick = { tab = TopilmalarTab.ALL }, text = { Text("Barcha topilmalar") })
                Tab(selected = tab == TopilmalarTab.REVIEW, onClick = { tab = TopilmalarTab.REVIEW }, text = { Text("Qayta ko‘rish") })
            }
            when (tab) {
                TopilmalarTab.SOURCES -> SourcesTab(
                    sources = sources,
                    archivedSources = archivedSources,
                    repository = repository,
                    canEdit = canEdit,
                    onOpen = { selectedSource = it },
                    modifier = Modifier.weight(1f)
                )
                TopilmalarTab.ALL -> AllCardsTab(
                    cards = allCards,
                    sources = sources,
                    repository = repository,
                    canEdit = canEdit,
                    onOpen = { selectedCard = it },
                    onComments = { onOpenComments(it.id) },
                    onOpenSource = { id -> selectedSource = sources.firstOrNull { it.id == id } },
                    onSingleDelete = { row -> lastDeletedGlobal = listOf(row.id); scope.launch { repository.softDelete(listOf(row.id)) } },
                    onLoadMore = { pageLimit = (pageLimit + 300).coerceAtMost(10_000) },
                    canLoadMore = allCards.size >= pageLimit && pageLimit < 10_000,
                    modifier = Modifier.weight(1f)
                )
                TopilmalarTab.REVIEW -> ReviewTab(
                    repository = repository,
                    preferences = preferences,
                    settings = settings,
                    canEdit = canEdit,
                    onRequestNotifications = onRequestNotifications,
                    onRequestExactTiming = onRequestExactTiming,
                    onOpenCard = { cardId -> scope.launch { selectedCard = repository.cardRow(cardId) } },
                    onOpenComments = onOpenComments,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    selectedCard?.let { row ->
        CardDetailDialog(
            row = row,
            repository = repository,
            language = language,
            canEdit = canEdit,
            onDismiss = { selectedCard = null },
            onChanged = { selectedCard = allCards.firstOrNull { it.id == row.id } ?: selectedCard }
        )
    }
    if (showStats) {
        AlertDialog(
            onDismissRequest = { showStats = false },
            title = { Text("Topilmalar statistikasi") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Jami kartochka: ${stats?.cardCount ?: 0}")
                    Text("Jami manba: ${stats?.sourceCount ?: 0}")
                    Text("Shu oy qo‘shildi: ${stats?.addedThisMonth ?: 0}")
                    Text("Qayta ko‘rilgan: ${stats?.reviewedCount ?: 0}")
                    Text("Sevimli: ${stats?.favoriteCount ?: 0}")
                    if (reviewStreak > 0) Text("Sokin davomiylik: $reviewStreak kun")
                    if (sources.isNotEmpty()) {
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        Text("Eng ko‘p ishlatilgan manbalar", fontWeight = FontWeight.SemiBold)
                        sources.sortedByDescending { it.cardCount }.take(5).forEach { Text("${it.title} · ${it.cardCount}") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showStats = false }) { Text("Yopish") } }
        )
    }
    if (showTags) TagManagerDialog(allTags, repository, canEdit) { showTags = false }
}

@Composable
private fun SourcesTab(
    sources: List<TopilmaSourceRow>,
    archivedSources: List<TopilmaSourceRow>,
    repository: TopilmalarRepository,
    canEdit: Boolean,
    onOpen: (TopilmaSourceRow) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var sort by remember { mutableStateOf(SourceSort.RECENT) }
    var filter by remember { mutableStateOf<TopilmaSourceType?>(null) }
    var showArchived by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var filterMenu by remember { mutableStateOf(false) }
    val base = if (showArchived) archivedSources else sources
    val visible = remember(base, sort, filter) {
        base.filter { filter == null || it.sourceType == filter!!.name }.let { list ->
            when (sort) {
                SourceSort.RECENT -> list.sortedByDescending { it.lastAddedAt }
                SourceSort.AZ -> list.sortedBy { it.title.lowercase() }
                SourceSort.MOST -> list.sortedByDescending { it.cardCount }
                SourceSort.NEWEST -> list.sortedByDescending { it.createdAt }
            }
        }
    }
    Column(modifier) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 9.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                AssistChip(onClick = { sortMenu = true }, label = { Text(sortLabel(sort)) })
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    SourceSort.entries.forEach { option -> DropdownMenuItem(text = { Text(sortLabel(option)) }, onClick = { sort = option; sortMenu = false }) }
                }
            }
            Box {
                AssistChip(onClick = { filterMenu = true }, label = { Text(filter?.let(::sourceTypeLabel) ?: "Barcha turlar") })
                DropdownMenu(filterMenu, { filterMenu = false }) {
                    DropdownMenuItem({ Text("Barcha turlar") }, onClick = { filter = null; filterMenu = false })
                    TopilmaSourceType.entries.forEach { option -> DropdownMenuItem({ Text(sourceTypeLabel(option)) }, onClick = { filter = option; filterMenu = false }) }
                }
            }
            FilterChip(selected = showArchived, onClick = { showArchived = !showArchived }, label = { Text("Arxiv") })
        }
        if (visible.isEmpty()) {
            EmptyTopilmalar("Hozircha manba yo‘q", "Matn yoki rasmni Android Share orqali Memora’ga yuboring.", Modifier.weight(1f))
        } else {
            LazyColumn(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.weight(1f)) {
                items(visible, key = { it.id }) { source ->
                    SourceCard(source, onOpen) {
                        if (canEdit) scope.launch { repository.setSourceArchived(source.id, !source.archived) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceCard(source: TopilmaSourceRow, onOpen: (TopilmaSourceRow) -> Unit, onArchive: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { onOpen(source) }, onLongClick = onArchive),
        shape = RoundedCornerShape(MemoraDesign.radiusMedium),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = MemoraDesign.shadowSubtle
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            SourceVisual(source, Modifier.size(54.dp))
            Column(Modifier.weight(1f).padding(start = 13.dp)) {
                Text(source.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                source.author?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
                Text("${source.cardCount} topilma · ${dateLabel(source.lastAddedAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Rounded.MoreVert, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SourceVisual(source: TopilmaSourceRow, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coverPath = source.coverPath
    val iconPath = source.originAppIconPath
    val cover by produceState<android.graphics.Bitmap?>(null, coverPath) {
        value = coverPath?.let { runCatching { BitmapFactory.decodeFile(TopilmalarMediaStorage(context).resolve(it).absolutePath) }.getOrNull() }
    }
    val appIcon by produceState<android.graphics.Bitmap?>(null, iconPath) {
        value = iconPath?.let { runCatching { BitmapFactory.decodeFile(TopilmalarMediaStorage(context).resolve(it).absolutePath) }.getOrNull() }
    }
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .10f), modifier = modifier) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val primary = cover ?: appIcon
            if (primary != null) Image(primary.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = if (cover != null) ContentScale.Crop else ContentScale.Fit)
            else Icon(sourceIcon(source.sourceType), null, tint = MaterialTheme.colorScheme.primary)
            if (cover != null && appIcon != null) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface, modifier = Modifier.align(Alignment.BottomEnd).size(20.dp)) {
                    Image(appIcon!!.asImageBitmap(), null, Modifier.fillMaxSize().padding(2.dp), contentScale = ContentScale.Fit)
                }
            }
        }
    }
}

@Composable
private fun CardSourceVisual(card: TopilmaCardRow, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val path = card.sourceCoverPath
    val bitmap by produceState<android.graphics.Bitmap?>(null, path) {
        value = path?.let { runCatching { BitmapFactory.decodeFile(TopilmalarMediaStorage(context).resolve(it).absolutePath) }.getOrNull() }
    }
    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .10f), modifier = modifier) {
        val current = bitmap
        if (current != null) Image(current.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Box(contentAlignment = Alignment.Center) { Icon(sourceIcon(card.sourceType.orEmpty()), null, tint = MaterialTheme.colorScheme.primary) }
    }
}

@Composable
private fun AllCardsTab(
    cards: List<TopilmaCardRow>,
    sources: List<TopilmaSourceRow>,
    repository: TopilmalarRepository,
    canEdit: Boolean,
    onOpen: (TopilmaCardRow) -> Unit,
    onComments: (TopilmaCardRow) -> Unit,
    onOpenSource: (String) -> Unit,
    onSingleDelete: (TopilmaCardRow) -> Unit,
    onLoadMore: () -> Unit,
    canLoadMore: Boolean,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var favoriteOnly by remember { mutableStateOf(false) }
    var sourceLessOnly by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var moveMenu by remember { mutableStateOf(false) }
    var bulkTag by remember { mutableStateOf("") }
    val visible = cards.filter { (!favoriteOnly || it.favorite) && (!sourceLessOnly || it.sourceId == null) }
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(favoriteOnly, { favoriteOnly = !favoriteOnly }, label = { Text("Sevimli") }, leadingIcon = { Icon(Icons.Rounded.FavoriteBorder, null, Modifier.size(18.dp)) })
            FilterChip(sourceLessOnly, { sourceLessOnly = !sourceLessOnly }, label = { Text("Manbasiz") })
            if (selected.isNotEmpty()) Text("${selected.size} tanlandi", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        if (selected.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledTonalButton(onClick = { scope.launch { repository.setFavorite(selected.toList(), true); selected = emptySet() } }, enabled = canEdit) { Icon(Icons.Rounded.Favorite, null); Text("Sevimli") }
                Box {
                    FilledTonalButton(onClick = { moveMenu = true }, enabled = canEdit) { Text("Ko‘chirish") }
                    DropdownMenu(moveMenu, { moveMenu = false }) {
                        DropdownMenuItem(text = { Text("Manbasiz") }, onClick = { scope.launch { repository.moveCards(selected.toList(), null); selected = emptySet() }; moveMenu = false })
                        sources.forEach { source -> DropdownMenuItem(text = { Text(source.title) }, onClick = { scope.launch { repository.moveCards(selected.toList(), source.id); selected = emptySet() }; moveMenu = false }) }
                    }
                }
                FilledTonalButton(onClick = { confirmDelete = true }, enabled = canEdit) { Icon(Icons.Rounded.DeleteOutline, null); Text("O‘chirish") }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(bulkTag, { bulkTag = it }, label = { Text("Teg qo‘shish") }, singleLine = true, modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    if (bulkTag.isNotBlank()) scope.launch {
                        selected.forEach { repository.addTagToCard(it, bulkTag) }
                        bulkTag = ""
                        selected = emptySet()
                    }
                }, enabled = canEdit) { Icon(Icons.Rounded.Tag, null) }
            }
        }
        if (visible.isEmpty()) EmptyTopilmalar("Topilma yo‘q", "Share orqali saqlangan Topilmalar shu yerda chiqadi.", Modifier.weight(1f))
        else CardList(
            cards = visible,
            repository = repository,
            canEdit = canEdit,
            selected = selected,
            onSelect = { id -> selected = if (id in selected) selected - id else selected + id },
            onOpen = onOpen,
            onComments = onComments,
            onOpenSource = onOpenSource,
            onDelete = onSingleDelete,
            modifier = Modifier.weight(1f)
        )
        if (canLoadMore) TextButton(onClick = onLoadMore, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Ko‘proq yuklash") }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Tanlangan Topilmalar o‘chirilsinmi?") },
        text = { Text("${selected.size} ta kartochka o‘chiriladi.") },
        confirmButton = { TextButton(onClick = { scope.launch { repository.softDelete(selected.toList()); selected = emptySet() }; confirmDelete = false }) { Text("O‘chirish") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Bekor qilish") } }
    )
}

@Composable
private fun CardList(
    cards: List<TopilmaCardRow>,
    repository: TopilmalarRepository,
    canEdit: Boolean,
    onOpen: (TopilmaCardRow) -> Unit,
    onComments: (TopilmaCardRow) -> Unit,
    onDelete: (TopilmaCardRow) -> Unit,
    onOpenSource: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
    selected: Set<String> = emptySet(),
    onSelect: (String) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val outerBackgroundArgb = MaterialTheme.colorScheme.background.toArgb()
    LazyColumn(
        modifier,
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(cards, key = { it.id }) { row ->
            TopilmaCard(
                row = row,
                canEdit = canEdit && selected.isEmpty(),
                selected = row.id in selected,
                onOpen = { if (selected.isEmpty()) onOpen(row) else onSelect(row.id) },
                onLongPress = { onSelect(row.id) },
                onShare = {
                    scope.launch {
                        TopilmaShareManager.shareCardImage(context, row, outerBackgroundArgb)
                    }
                },
                onLike = { scope.launch { repository.incrementLike(row.id) } },
                onComments = { if (selected.isEmpty()) onComments(row) },
                onEdit = { if (selected.isEmpty()) onOpen(row) },
                onToggleFavorite = { scope.launch { repository.setFavorite(listOf(row.id), !row.favorite) } },
                onOpenSource = row.sourceId?.let { id -> { if (selected.isEmpty()) onOpenSource?.invoke(id) } },
                onToggleReview = { scope.launch { repository.setCardReviewExcluded(row.id, !row.excludedFromReview) } },
                onDelete = { if (selected.isEmpty()) onDelete(row) }
            )
        }
    }
}

@Composable
private fun ReviewTab(
    repository: TopilmalarRepository,
    preferences: UserPreferences,
    settings: UserSettings,
    canEdit: Boolean,
    onRequestNotifications: () -> Unit,
    onRequestExactTiming: () -> Unit,
    onOpenCard: (String) -> Unit,
    onOpenComments: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val outerBackgroundArgb = MaterialTheme.colorScheme.background.toArgb()
    var session by remember { mutableStateOf<TopilmaReviewSessionState?>(null) }
    var currentRow by remember { mutableStateOf<TopilmaCardRow?>(null) }
    var loadingError by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var randomText by remember { mutableStateOf<String?>(null) }
    var reviewComment by remember { mutableStateOf("") }
    LaunchedEffect(settings.topilmalarDailyCount, refresh, canEdit) {
        runCatching { repository.getOrCreateDailySession(settings.topilmalarDailyCount) }
            .onSuccess { session = it; loadingError = null }
            .onFailure { loadingError = "Qayta ko‘rishni hozir ochib bo‘lmadi." }
    }
    LaunchedEffect(session?.session?.id, session?.currentIndex) {
        val index = session?.currentIndex ?: -1
        val currentCard = session?.cards?.getOrNull(index)
        currentRow = currentCard?.let { repository.cardRow(it.id) }
    }
    Column(modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Bugungi qayta ko‘rish", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text("Bosim emas — topilmalaringiz bilan yana uchrashuv.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { showSettings = true }) { Icon(Icons.Rounded.Settings, null) }
            IconButton(onClick = { scope.launch { randomText = repository.randomEligibleCard()?.text ?: "Hozircha qayta ko‘rish uchun topilma yo‘q" } }) { Icon(Icons.Rounded.Shuffle, null) }
        }
        val currentSession = session
        when {
            loadingError != null -> EmptyTopilmalar("Qayta ko‘rish ochilmadi", loadingError.orEmpty(), Modifier.weight(1f))
            currentSession == null -> EmptyTopilmalar("Tayyorlanmoqda…", "", Modifier.weight(1f))
            currentSession.items.isEmpty() -> EmptyTopilmalar("Hozircha qayta ko‘rish uchun topilma yo‘q", "Yangi kartochka 1–3 kun ichida ilk qayta ko‘rishga tushadi.", Modifier.weight(1f))
            currentSession.completed -> EmptyTopilmalar("Bugungi qayta ko‘rish tugallandi ✓", "Bugun ${currentSession.items.size} ta topilma qayta ko‘rildi.", Modifier.weight(1f))
            else -> {
                val index = currentSession.currentIndex
                val card = currentSession.cards.getOrNull(index)
                Text("${index + 1} / ${currentSession.items.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 18.dp))
                if (card != null) {
                    val reviewRow = currentRow
                    if (reviewRow != null) {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 14.dp),
                            contentPadding = PaddingValues(bottom = 8.dp)
                        ) {
                            item {
                                TopilmaCard(
                                    row = reviewRow,
                                    canEdit = canEdit,
                                    forceExpanded = true,
                                    showExpandControl = false,
                                    onOpen = { onOpenCard(card.id) },
                                    onShare = {
                                        scope.launch {
                                            TopilmaShareManager.shareCardImage(context, reviewRow, outerBackgroundArgb)
                                        }
                                    },
                                    onLike = { scope.launch { repository.incrementLike(reviewRow.id); currentRow = repository.cardRow(reviewRow.id) } },
                                    onComments = { onOpenComments(reviewRow.id) },
                                    onEdit = { onOpenCard(reviewRow.id) },
                                    onToggleFavorite = { scope.launch { repository.setFavorite(listOf(reviewRow.id), !reviewRow.favorite); currentRow = repository.cardRow(reviewRow.id) } },
                                    onToggleReview = { scope.launch { repository.setCardReviewExcluded(reviewRow.id, !reviewRow.excludedFromReview); currentRow = repository.cardRow(reviewRow.id) } }
                                )
                            }
                        }
                    } else {
                        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { Text("Tayyorlanmoqda…") }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReviewButton("Tezroq", Modifier.weight(1f), canEdit) { scope.launch { repository.rateReview(currentSession.session.id, card.id, TopilmaReviewRating.SOONER); refresh++ } }
                        ReviewButton("Odatiy", Modifier.weight(1f), canEdit) { scope.launch { repository.rateReview(currentSession.session.id, card.id, TopilmaReviewRating.NORMAL); refresh++ } }
                        ReviewButton("Keyinroq", Modifier.weight(1f), canEdit) { scope.launch { repository.rateReview(currentSession.session.id, card.id, TopilmaReviewRating.LATER); refresh++ } }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(reviewComment, { reviewComment = it }, label = { Text("Izoh qo‘shish") }, singleLine = true, modifier = Modifier.weight(1f))
                        IconButton(onClick = { if (reviewComment.isNotBlank()) scope.launch { repository.addComment(card.id, reviewComment); reviewComment = "" } }, enabled = canEdit) { Icon(Icons.Rounded.Add, null) }
                    }
                }
            }
        }
    }
    if (randomText != null) AlertDialog(onDismissRequest = { randomText = null }, title = { Text("Tasodifiy topilma") }, text = { Text(randomText.orEmpty()) }, confirmButton = { TextButton(onClick = { randomText = null }) { Text("Yopish") } })
    if (showSettings) ReviewSettingsDialog(settings, preferences, onRequestNotifications, onRequestExactTiming) { showSettings = false }
}

@Composable
private fun ReviewButton(label: String, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier, enabled = enabled, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 12.dp)) { Text(label, maxLines = 1) }
}

@Composable
private fun ReviewSettingsDialog(
    settings: UserSettings,
    preferences: UserPreferences,
    onRequestNotifications: () -> Unit,
    onRequestExactTiming: () -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var hour by remember { mutableStateOf(settings.topilmalarReminderHour.toString()) }
    var minute by remember { mutableStateOf(settings.topilmalarReminderMinute.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Qayta ko‘rish sozlamalari") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Kunlik son")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(3, 5, 10, 15, 20).forEach { count -> FilterChip(settings.topilmalarDailyCount == count, { scope.launch { preferences.setTopilmalarDailyCount(count) } }, label = { Text(count.toString()) }) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Qayta ko‘rish bildirishnomasi", modifier = Modifier.weight(1f))
                    Switch(settings.topilmalarReminderEnabled, { enabled ->
                        if (enabled) {
                            onRequestNotifications()
                            onRequestExactTiming()
                        }
                        scope.launch { preferences.setTopilmalarReminderEnabled(enabled) }
                    })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(hour, { hour = it.filter(Char::isDigit).take(2) }, label = { Text("Soat") }, singleLine = true, modifier = Modifier.weight(1f))
                    Text(":")
                    OutlinedTextField(minute, { minute = it.filter(Char::isDigit).take(2) }, label = { Text("Daqiqa") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                TextButton(onClick = {
                    val h = hour.toIntOrNull()
                    val m = minute.toIntOrNull()
                    if (h != null && m != null && h in 0..23 && m in 0..59) scope.launch { preferences.setTopilmalarReminderTime(h, m) }
                }) { Text("Vaqtni saqlash") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tayyor") } }
    )
}

@Composable
private fun SourceDetailScreen(
    source: TopilmaSourceRow,
    allSources: List<TopilmaSourceRow>,
    repository: TopilmalarRepository,
    language: AppLanguage,
    canEdit: Boolean,
    onBack: () -> Unit,
    onCard: (TopilmaCardRow) -> Unit,
    onComments: (TopilmaCardRow) -> Unit,
    onSingleDelete: (TopilmaCardRow) -> Unit,
    undoIds: List<String>,
    onUndo: (List<String>) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val currentSource by repository.observeSource(source.id).collectAsStateWithLifecycle(null)
    val sourceTags by repository.observeSourceTags(source.id).collectAsStateWithLifecycle(emptyList())
    val cards by repository.observeSourceCards(source.id).collectAsStateWithLifecycle(emptyList())
    var oldestFirst by remember { mutableStateOf(false) }
    var manualSession by remember { mutableStateOf<TopilmaReviewSessionState?>(null) }
    var showEdit by remember { mutableStateOf(false) }
    var showMerge by remember { mutableStateOf(false) }
    var confirmDeleteSource by remember { mutableStateOf(false) }
    var sourceOpenError by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var sourceTag by remember { mutableStateOf("") }
    fun saveCover(uri: Uri) {
        scope.launch {
            val stored = TopilmalarMediaStorage(context).storeSharedImage(uri, context.contentResolver.getType(uri))
            repository.setSourceCover(source.id, stored.relativePath)
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let(::saveCover) }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(::saveCover) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success -> if (success) pendingCameraUri?.let(::saveCover) }
    val ordered = if (oldestFirst) cards.sortedBy { it.createdAt } else cards
    val displaySource = source.copy(
        title = currentSource?.title ?: source.title,
        author = currentSource?.author ?: source.author,
        coverPath = currentSource?.coverPath ?: source.coverPath,
        canonicalUri = currentSource?.canonicalUri ?: source.canonicalUri,
        originAppName = currentSource?.originAppName ?: source.originAppName,
        originAppIconPath = currentSource?.originAppIconPath ?: source.originAppIconPath,
        sourceType = currentSource?.sourceType ?: source.sourceType,
        excludedFromReview = currentSource?.excludedFromReview ?: source.excludedFromReview
    )
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, null) }
            SourceVisual(displaySource, Modifier.size(50.dp))
            Column(Modifier.weight(1f).padding(start = 11.dp)) {
                Text(displaySource.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${cards.size} topilma", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { showEdit = true }, enabled = canEdit) { Icon(Icons.Rounded.Edit, null) }
            IconButton(onClick = { oldestFirst = !oldestFirst }) { Icon(Icons.Rounded.Refresh, null) }
        }
        displaySource.author?.let { Text(it, Modifier.padding(horizontal = 20.dp, vertical = 2.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Text(
            listOfNotNull(sourceTypeLabel(runCatching { TopilmaSourceType.valueOf(displaySource.sourceType) }.getOrDefault(TopilmaSourceType.OTHER)), displaySource.originAppName).joinToString(" · "),
            Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!displaySource.canonicalUri.isNullOrBlank()) {
            OutlinedButton(
                onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(displaySource.canonicalUri))) }.onFailure { sourceOpenError = true } },
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 5.dp)
            ) { Icon(Icons.Rounded.OpenInNew, null); Text("Manbani ochish") }
        }
        if (sourceTags.isNotEmpty()) Row(Modifier.padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) { sourceTags.forEach { tag -> AssistChip(onClick = { if (canEdit) scope.launch { repository.removeTagFromSource(source.id, tag.id) } }, label = { Text("#${tag.name}") }) } }
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(sourceTag, { sourceTag = it }, label = { Text("Manba tegi") }, singleLine = true, modifier = Modifier.weight(1f))
            IconButton(onClick = { if (sourceTag.isNotBlank()) scope.launch { repository.addTagToSource(source.id, sourceTag); sourceTag = "" } }, enabled = canEdit) { Icon(Icons.Rounded.Tag, null) }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = { if (canEdit) scope.launch { manualSession = repository.createSourceReviewSession(source.id) } }, label = { Text("Shu manbani qayta ko‘rish") })
            AssistChip(onClick = { if (canEdit) scope.launch { repository.setSourceReviewExcluded(source.id, !displaySource.excludedFromReview) } }, label = { Text(if (displaySource.excludedFromReview) "Qayta ko‘rishga qo‘shish" else "Qayta ko‘rsatma") })
            AssistChip(onClick = { if (canEdit) scope.launch { repository.setSourceArchived(source.id, true); onBack() } }, label = { Text("Arxiv") }, leadingIcon = { Icon(Icons.Rounded.Archive, null, Modifier.size(18.dp)) })
            AssistChip(onClick = { showMerge = true }, enabled = canEdit, label = { Text("Birlashtirish") }, leadingIcon = { Icon(Icons.Rounded.Merge, null, Modifier.size(18.dp)) })
        }
        if (undoIds.isNotEmpty()) UndoDeleteBanner(undoIds, onUndo)
        CardList(cards = ordered, repository = repository, canEdit = canEdit, onOpen = onCard, onComments = onComments, onDelete = onSingleDelete, modifier = Modifier.weight(1f))
    }
    manualSession?.let { current ->
        ManualReviewDialog(current, repository, onDismiss = { manualSession = null }, onReload = { session -> manualSession = session })
    }
    if (showEdit && currentSource != null) {
        SourceEditDialog(
            source = currentSource!!,
            onDismiss = { showEdit = false },
            onSave = { updated -> scope.launch { repository.updateSource(updated); showEdit = false } },
            onCamera = {
                val file = File(context.cacheDir, "camera/topilma-cover-${source.id}.jpg").apply { parentFile?.mkdirs() }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                pendingCameraUri = uri
                cameraLauncher.launch(uri)
            },
            onGallery = { galleryLauncher.launch("image/*") },
            onFile = { fileLauncher.launch(arrayOf("image/*")) },
            onResetCover = { scope.launch { repository.setSourceCover(source.id, null) } },
            onDelete = { confirmDeleteSource = true }
        )
    }
    if (showMerge) {
        AlertDialog(
            onDismissRequest = { showMerge = false },
            title = { Text("Manbalarni birlashtirish") },
            text = { Column { allSources.filter { it.id != source.id }.forEach { target -> TextButton(onClick = { scope.launch { repository.mergeSources(source.id, target.id); showMerge = false; onBack() } }) { Text(target.title) } } } },
            confirmButton = { TextButton(onClick = { showMerge = false }) { Text("Bekor qilish") } }
        )
    }
    if (confirmDeleteSource) {
        AlertDialog(
            onDismissRequest = { confirmDeleteSource = false },
            title = { Text("Manba o‘chirilsinmi?") },
            text = { Text("Kartochkalar o‘chmaydi; ular Manbasiz bo‘limiga o‘tadi.") },
            confirmButton = { TextButton(onClick = { scope.launch { repository.deleteSourceKeepCards(source.id); confirmDeleteSource = false; showEdit = false; onBack() } }) { Text("O‘chirish") } },
            dismissButton = { TextButton(onClick = { confirmDeleteSource = false }) { Text("Bekor qilish") } }
        )
    }
    if (sourceOpenError) {
        val sourceUri = currentSource?.canonicalUri ?: source.canonicalUri.orEmpty()
        AlertDialog(
            onDismissRequest = { sourceOpenError = false },
            title = { Text("Manbani ochib bo‘lmadi") },
            text = { Text("Havolani tekshiring yoki tahrirlang. U saqlanganicha qoladi.") },
            confirmButton = { TextButton(onClick = { clipboard.setText(AnnotatedString(sourceUri)); sourceOpenError = false }) { Text("URLni nusxalash") } },
            dismissButton = { TextButton(onClick = { sourceOpenError = false; showEdit = true }) { Text("Tahrirlash") } }
        )
    }
}

@Composable
private fun ManualReviewDialog(
    state: TopilmaReviewSessionState,
    repository: TopilmalarRepository,
    onDismiss: () -> Unit,
    onReload: (TopilmaReviewSessionState) -> Unit
) {
    val scope = rememberCoroutineScope()
    val index = state.currentIndex
    val card = state.cards.getOrNull(index)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (state.completed) "Qayta ko‘rish tugallandi ✓" else "${index + 1} / ${state.items.size}") },
        text = { Text(card?.text ?: if (state.items.isEmpty()) "Bu manbada Topilma yo‘q" else "Tugallandi") },
        confirmButton = {
            if (card == null) TextButton(onClick = onDismiss) { Text("Yopish") }
            else Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(
                    "Tezroq" to TopilmaReviewRating.SOONER,
                    "Odatiy" to TopilmaReviewRating.NORMAL,
                    "Keyinroq" to TopilmaReviewRating.LATER
                ).forEach { (label, rating) ->
                    TextButton(onClick = {
                        scope.launch {
                            repository.rateReview(state.session.id, card.id, rating)
                            val refreshedItems = state.items.map { if (it.cardId == card.id) it.copy(ratedAt = System.currentTimeMillis(), rating = rating.name) else it }
                            onReload(state.copy(items = refreshedItems))
                        }
                    }) { Text(label) }
                }
            }
        }
    )
}

@Composable
private fun SourceEditDialog(
    source: TopilmaSourceEntity,
    onDismiss: () -> Unit,
    onSave: (TopilmaSourceEntity) -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onFile: () -> Unit,
    onResetCover: () -> Unit,
    onDelete: () -> Unit
) {
    var title by remember(source.id) { mutableStateOf(source.title) }
    var author by remember(source.id) { mutableStateOf(source.author.orEmpty()) }
    var uri by remember(source.id) { mutableStateOf(source.canonicalUri.orEmpty()) }
    var type by remember(source.id) { mutableStateOf(runCatching { TopilmaSourceType.valueOf(source.sourceType) }.getOrDefault(TopilmaSourceType.OTHER)) }
    var typeMenu by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manbani tahrirlash") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Nomi") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(author, { author = it }, label = { Text("Muallif / yaratuvchi") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(uri, { uri = it }, label = { Text("URL / URI") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Box {
                    OutlinedButton(onClick = { typeMenu = true }) { Text(sourceTypeLabel(type)) }
                    DropdownMenu(typeMenu, { typeMenu = false }) {
                        TopilmaSourceType.entries.forEach { option -> DropdownMenuItem({ Text(sourceTypeLabel(option)) }, onClick = { type = option; typeMenu = false }) }
                    }
                }
                Text("Muqova", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    OutlinedButton(onClick = onCamera) { Icon(Icons.Rounded.CameraAlt, null); Text("Kamera") }
                    OutlinedButton(onClick = onGallery) { Icon(Icons.Rounded.PhotoLibrary, null); Text("Galereya") }
                    OutlinedButton(onClick = onFile) { Icon(Icons.Rounded.FolderOpen, null); Text("Fayl") }
                }
                if (source.coverPath != null) TextButton(onClick = onResetCover) { Text("Standart belgiga qaytarish") }
                TextButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, null); Text("Manbani o‘chirish") }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(source.copy(title = title, author = author.ifBlank { null }, canonicalUri = uri.ifBlank { null }, sourceType = type.name)) },
                enabled = title.isNotBlank()
            ) { Text("Saqlash") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Bekor qilish") } }
    )
}

@Composable
private fun TopilmaCommentsDialog(
    row: TopilmaCardRow,
    repository: TopilmalarRepository,
    canEdit: Boolean,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val comments by repository.observeComments(row.id).collectAsStateWithLifecycle(emptyList())
    var newComment by remember(row.id) { mutableStateOf("") }
    var editingId by remember { mutableStateOf<String?>(null) }
    var editingText by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Izohlar · ${comments.size}") },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                item {
                    Text(row.sourceTitle ?: "Manbasi aniqlanmagan", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(row.text.ifBlank { "Rasmli Topilma" }, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                }
                items(comments, key = { it.id }) { comment ->
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)) {
                        Column(Modifier.padding(10.dp)) {
                            if (editingId == comment.id) {
                                OutlinedTextField(editingText, { editingText = it }, modifier = Modifier.fillMaxWidth())
                                Row {
                                    TextButton(onClick = { editingId = null }) { Text("Bekor") }
                                    TextButton(
                                        onClick = { scope.launch { repository.editComment(comment, editingText); editingId = null } },
                                        enabled = editingText.isNotBlank()
                                    ) { Text("Saqlash") }
                                }
                            } else {
                                Text(comment.text)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        if (comment.editedAt != null) "${dateLabel(comment.createdAt)} · tahrirlandi" else dateLabel(comment.createdAt),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (canEdit) {
                                        IconButton(onClick = { editingId = comment.id; editingText = comment.text }, modifier = Modifier.size(30.dp)) { Icon(Icons.Rounded.Edit, null, Modifier.size(16.dp)) }
                                        IconButton(onClick = { scope.launch { repository.deleteComment(comment.id) } }, modifier = Modifier.size(30.dp)) { Icon(Icons.Rounded.DeleteOutline, null, Modifier.size(16.dp)) }
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(newComment, { newComment = it }, label = { Text("Yangi izoh") }, modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = { if (newComment.isNotBlank()) scope.launch { repository.addComment(row.id, newComment); newComment = "" } },
                            enabled = canEdit
                        ) { Icon(Icons.Rounded.Add, null) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Yopish") } }
    )
}

@Composable
private fun CardDetailDialog(
    row: TopilmaCardRow,
    repository: TopilmalarRepository,
    language: AppLanguage,
    canEdit: Boolean,
    onDismiss: () -> Unit,
    onChanged: () -> Unit
) {
    val context = LocalContext.current
    val outerBackgroundArgb = MaterialTheme.colorScheme.background.toArgb()
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val comments by repository.observeComments(row.id).collectAsStateWithLifecycle(emptyList())
    val images by repository.observeImages(row.id).collectAsStateWithLifecycle(emptyList())
    val cardTags by repository.observeCardTags(row.id).collectAsStateWithLifecycle(emptyList())
    val reviewHistory by repository.observeReviewHistory(row.id).collectAsStateWithLifecycle(emptyList())
    var edit by remember { mutableStateOf(false) }
    var text by remember(row.id) { mutableStateOf(row.text) }
    var comment by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf("") }
    var shareMenu by remember { mutableStateOf(false) }
    var editingCommentId by remember { mutableStateOf<String?>(null) }
    var editingCommentText by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(row.sourceTitle ?: "Manbasi aniqlanmagan", modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (canEdit) IconButton(onClick = { edit = !edit }) { Icon(Icons.Rounded.Edit, null) }
            }
        },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 590.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    if (edit) {
                        OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth(), minLines = 5)
                        Button(onClick = { scope.launch { repository.card(row.id)?.let { repository.updateCard(it, text) }; edit = false; onChanged() } }, enabled = text.isNotBlank()) { Text("Saqlash") }
                    } else Text(row.text.ifBlank { "Rasmli Topilma" }, style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 25.sp))
                }
                items(images, key = { it.id }) { image ->
                    val file = remember(image.relativePath) { TopilmalarMediaStorage(context).resolve(image.relativePath) }
                    ImageBlockView(NoteBlock.Image(id = image.id, relativePath = image.relativePath, mimeType = image.mimeType), file, editable = false)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { TopilmaOcrManager.open(context, file, image.mimeType) }) { Text("Matnni aniqlash") }
                        OutlinedButton(onClick = {
                            clipboard.getText()?.text?.takeIf { it.isNotBlank() }?.let { recognized ->
                                scope.launch {
                                    repository.card(row.id)?.let { card -> repository.updateCard(card, listOf(card.text, recognized).filter { it.isNotBlank() }.joinToString("\n\n")) }
                                    onChanged()
                                }
                            }
                        }, enabled = canEdit) { Text("Natijani joylash") }
                    }
                }
                if (cardTags.isNotEmpty()) item { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { cardTags.forEach { AssistChip(onClick = {}, label = { Text("#${it.name}") }) } } }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(tag, { tag = it }, label = { Text("Teg") }, singleLine = true, modifier = Modifier.weight(1f))
                        IconButton(onClick = { if (tag.isNotBlank()) scope.launch { repository.addTagToCard(row.id, tag); tag = "" } }, enabled = canEdit) { Icon(Icons.Rounded.Tag, null) }
                    }
                }
                item { HorizontalDivider() }
                if (reviewHistory.isNotEmpty()) item {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Qayta ko‘rish tarixi", fontWeight = FontWeight.SemiBold)
                        reviewHistory.take(10).forEach { event ->
                            Text("${dateLabel(event.reviewedAt)} · ${reviewRatingLabel(event.rating)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                items(comments, key = { it.id }) { item ->
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)) {
                        Column(Modifier.padding(10.dp)) {
                            if (editingCommentId == item.id) {
                                OutlinedTextField(editingCommentText, { editingCommentText = it }, modifier = Modifier.fillMaxWidth())
                                Row {
                                    TextButton(onClick = { editingCommentId = null }) { Text("Bekor") }
                                    TextButton(onClick = { scope.launch { repository.editComment(item, editingCommentText); editingCommentId = null } }, enabled = editingCommentText.isNotBlank()) { Text("Saqlash") }
                                }
                            } else {
                                Text(item.text)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (item.editedAt != null) "${dateLabel(item.createdAt)} · tahrirlandi" else dateLabel(item.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                    if (canEdit) {
                                        IconButton(onClick = { editingCommentId = item.id; editingCommentText = item.text }, modifier = Modifier.size(30.dp)) { Icon(Icons.Rounded.Edit, null, Modifier.size(16.dp)) }
                                        IconButton(onClick = { scope.launch { repository.deleteComment(item.id) } }, modifier = Modifier.size(30.dp)) { Icon(Icons.Rounded.DeleteOutline, null, Modifier.size(16.dp)) }
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(comment, { comment = it }, label = { Text("Yangi izoh") }, modifier = Modifier.weight(1f))
                        IconButton(onClick = { if (comment.isNotBlank()) scope.launch { repository.addComment(row.id, comment); comment = "" } }, enabled = canEdit) { Icon(Icons.Rounded.Add, null) }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (!row.sourceUri.isNullOrBlank()) OutlinedButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(row.sourceUri))) } }) { Icon(Icons.Rounded.OpenInNew, null); Text("Manbani ochish") }
                        OutlinedButton(onClick = { clipboard.setText(AnnotatedString(row.text)) }) { Icon(Icons.Rounded.ContentCopy, null); Text("Nusxa olish") }
                        OutlinedButton(onClick = { scope.launch { repository.setFavorite(listOf(row.id), !row.favorite); onChanged() } }, enabled = canEdit) { Icon(if (row.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null); Text("Sevimli") }
                        OutlinedButton(onClick = { scope.launch { repository.setCardReviewExcluded(row.id, !row.excludedFromReview) } }, enabled = canEdit) { Text(if (row.excludedFromReview) "Qayta qo‘shish" else "Qayta ko‘rsatma") }
                        Box {
                            OutlinedButton(onClick = { shareMenu = true }) { Icon(Icons.Rounded.Share, null); Text("Ulashish") }
                            DropdownMenu(shareMenu, { shareMenu = false }) {
                                DropdownMenuItem({ Text("Faqat matn") }, onClick = { TopilmaShareManager.shareText(context, row, false); shareMenu = false })
                                DropdownMenuItem({ Text("Matn + manba") }, onClick = { TopilmaShareManager.shareText(context, row, true); shareMenu = false })
                                DropdownMenuItem(
                                    { Text("Kartochka rasmi") },
                                    onClick = {
                                        scope.launch {
                                            TopilmaShareManager.shareCardImage(context, row, outerBackgroundArgb)
                                        }
                                        shareMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Yopish") } }
    )
}

@Composable
private fun NewTopilmaDialog(
    sources: List<TopilmaSourceRow>,
    onDismiss: () -> Unit,
    onSave: (String, String?, List<TopilmaImageDraft>) -> Unit,
    onCreateSource: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var sourceId by remember { mutableStateOf<String?>(null) }
    var sourceMenu by remember { mutableStateOf(false) }
    var newSourceTitle by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var imageDrafts by remember { mutableStateOf<List<TopilmaImageDraft>>(emptyList()) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    fun addImages(uris: List<Uri>) {
        imageDrafts = imageDrafts + uris.mapNotNull { uri ->
            runCatching {
                TopilmalarMediaStorage(context).storeSharedImage(uri, context.contentResolver.getType(uri)).let { TopilmaImageDraft(it.relativePath, it.mimeType) }
            }.getOrNull()
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { addImages(it) }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { addImages(it) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { if (it) pendingCameraUri?.let { uri -> addImages(listOf(uri)) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yangi Topilma") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(text, { text = it }, label = { Text("Matn") }, minLines = 6, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { clipboard.getText()?.text?.let { text = it } }) { Icon(Icons.Rounded.ContentCopy, null); Text("Clipboarddan joylash") }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    OutlinedButton(onClick = {
                        val file = File(context.cacheDir, "camera/topilma-${System.currentTimeMillis()}.jpg").apply { parentFile?.mkdirs() }
                        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file).also { uri -> pendingCameraUri = uri; camera.launch(uri) }
                    }) { Icon(Icons.Rounded.CameraAlt, null); Text("Kamera") }
                    OutlinedButton(onClick = { gallery.launch("image/*") }) { Icon(Icons.Rounded.PhotoLibrary, null); Text("Galereya") }
                    OutlinedButton(onClick = { files.launch(arrayOf("image/*")) }) { Icon(Icons.Rounded.FolderOpen, null); Text("Fayl") }
                }
                if (imageDrafts.isNotEmpty()) Text("${imageDrafts.size} ta rasm tanlandi", color = MaterialTheme.colorScheme.primary)
                Box {
                    OutlinedButton(onClick = { sourceMenu = true }) { Text(sources.firstOrNull { it.id == sourceId }?.title ?: "Manbasiz") }
                    DropdownMenu(sourceMenu, { sourceMenu = false }) {
                        DropdownMenuItem({ Text("Manbasiz") }, onClick = { sourceId = null; sourceMenu = false })
                        sources.forEach { source -> DropdownMenuItem({ Text(source.title) }, onClick = { sourceId = source.id; sourceMenu = false }) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(newSourceTitle, { newSourceTitle = it }, label = { Text("Yangi manba") }, singleLine = true, modifier = Modifier.weight(1f))
                    IconButton(onClick = { if (newSourceTitle.isNotBlank()) { onCreateSource(newSourceTitle); newSourceTitle = "" } }) { Icon(Icons.Rounded.Add, null) }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(text, sourceId, imageDrafts) }, enabled = text.isNotBlank() || imageDrafts.isNotEmpty()) { Text("Qo‘shish") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Bekor qilish") } }
    )
}

@Composable
private fun UndoDeleteBanner(ids: List<String>, onUndo: (List<String>) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Topilma o‘chirildi", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { onUndo(ids) }) { Text("Qaytarish") }
    }
}

@Composable
private fun EmptyTopilmalar(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(30.dp)) {
            Icon(Icons.Rounded.AutoStories, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(46.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 5.dp))
        }
    }
}

@Composable
private fun TagManagerDialog(
    tags: List<TopilmaTagEntity>,
    repository: TopilmalarRepository,
    canEdit: Boolean,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<TopilmaTagEntity?>(null) }
    var editName by remember { mutableStateOf("") }
    var mergeFrom by remember { mutableStateOf<TopilmaTagEntity?>(null) }
    var mergeTo by remember { mutableStateOf<TopilmaTagEntity?>(null) }
    var fromMenu by remember { mutableStateOf(false) }
    var toMenu by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Teglarni boshqarish") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (editing != null) {
                    OutlinedTextField(editName, { editName = it }, label = { Text("Yangi nom") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row {
                        TextButton(onClick = { editing = null }) { Text("Bekor") }
                        TextButton(onClick = { editing?.let { tag -> scope.launch { repository.renameTag(tag, editName); editing = null } } }, enabled = editName.isNotBlank()) { Text("Nomini o‘zgartirish") }
                    }
                }
                LazyColumn(Modifier.heightIn(max = 260.dp)) {
                    items(tags, key = { it.id }) { tag ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("#${tag.name}", modifier = Modifier.weight(1f))
                            IconButton(onClick = { editing = tag; editName = tag.name }, enabled = canEdit) { Icon(Icons.Rounded.Edit, null) }
                            IconButton(onClick = { scope.launch { repository.deleteTag(tag.id) } }, enabled = canEdit) { Icon(Icons.Rounded.DeleteOutline, null) }
                        }
                    }
                }
                if (tags.size >= 2) {
                    HorizontalDivider()
                    Text("Teglarni birlashtirish", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.weight(1f)) {
                            OutlinedButton(onClick = { fromMenu = true }, modifier = Modifier.fillMaxWidth()) { Text(mergeFrom?.name ?: "Birinchi") }
                            DropdownMenu(fromMenu, { fromMenu = false }) { tags.forEach { tag -> DropdownMenuItem({ Text(tag.name) }, onClick = { mergeFrom = tag; fromMenu = false }) } }
                        }
                        Box(Modifier.weight(1f)) {
                            OutlinedButton(onClick = { toMenu = true }, modifier = Modifier.fillMaxWidth()) { Text(mergeTo?.name ?: "Ikkinchi") }
                            DropdownMenu(toMenu, { toMenu = false }) { tags.forEach { tag -> DropdownMenuItem({ Text(tag.name) }, onClick = { mergeTo = tag; toMenu = false }) } }
                        }
                    }
                    Button(
                        onClick = { val from = mergeFrom; val to = mergeTo; if (from != null && to != null) scope.launch { repository.mergeTags(from.id, to.id); mergeFrom = null; mergeTo = null } },
                        enabled = canEdit && mergeFrom != null && mergeTo != null && mergeFrom?.id != mergeTo?.id
                    ) { Text("Birlashtirish") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Yopish") } }
    )
}

private fun sortLabel(sort: SourceSort) = when (sort) {
    SourceSort.RECENT -> "Oxirgi faol"
    SourceSort.AZ -> "A–Z"
    SourceSort.MOST -> "Eng ko‘p Topilma"
    SourceSort.NEWEST -> "Yangi manba"
}

private fun sourceIcon(type: String) = when (type) {
    TopilmaSourceType.BOOK.name -> Icons.Rounded.Book
    TopilmaSourceType.CHAT.name -> Icons.Rounded.ChatBubbleOutline
    TopilmaSourceType.ARTICLE_WEBSITE.name -> Icons.Rounded.Link
    TopilmaSourceType.SOCIAL.name -> Icons.Rounded.Share
    TopilmaSourceType.DOCUMENT.name -> Icons.Rounded.Description
    TopilmaSourceType.MESSAGE.name -> Icons.Rounded.Email
    else -> Icons.Rounded.AutoStories
}

private fun sourceTypeLabel(type: TopilmaSourceType) = when (type) {
    TopilmaSourceType.BOOK -> "Kitob"
    TopilmaSourceType.ARTICLE_WEBSITE -> "Maqola / sayt"
    TopilmaSourceType.CHAT -> "Chat"
    TopilmaSourceType.SOCIAL -> "Ijtimoiy tarmoq"
    TopilmaSourceType.DOCUMENT -> "Hujjat"
    TopilmaSourceType.MESSAGE -> "Xabar"
    TopilmaSourceType.OTHER -> "Boshqa"
}

private fun dateLabel(epoch: Long): String = Instant.ofEpochMilli(epoch)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))

private fun reviewRatingLabel(value: String) = when (value) {
    TopilmaReviewRating.SOONER.name -> "Tezroq"
    TopilmaReviewRating.LATER.name -> "Keyinroq"
    else -> "Odatiy"
}
