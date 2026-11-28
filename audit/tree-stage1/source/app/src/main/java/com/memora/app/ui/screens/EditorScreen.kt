package com.memora.app.ui.screens

import com.memora.app.ui.components.MemoraSheet
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Mood
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memora.app.data.local.NoteType
import com.memora.app.data.local.SavedTitleEntity
import com.memora.app.data.model.NoteBlock
import com.memora.app.data.model.NoteMood
import com.memora.app.ui.components.MoodSticker
import com.memora.app.ui.components.MemoraConfirmationDialog
import com.memora.app.ui.components.MemoraPermissionDialog
import com.memora.app.ui.components.MemoraFeedbackHost
import com.memora.app.ui.components.MemoraFeedbackKind
import com.memora.app.ui.components.MemoraFeedbackMessage
import com.memora.app.data.model.NoteBlocksCodec
import com.memora.app.data.repository.MemoraRepository
import com.memora.app.data.repository.NoteSaveResult
import com.memora.app.diagnostics.LocalCrashDiagnostics
import com.memora.app.i18n.L
import com.memora.app.i18n.S
import com.memora.app.media.AudioRecorderController
import com.memora.app.media.MediaStorage
import com.memora.app.prefs.AppLanguage
import com.memora.app.prefs.FontChoice
import com.memora.app.recovery.EditorRecoveryStore
import com.memora.app.ui.components.AudioBlockView
import com.memora.app.ui.components.ImageBlockView
import com.memora.app.ui.components.HashtagChips
import com.memora.app.ui.components.PlainTextEditor
import com.memora.app.ui.components.FloatingNoteChrome
import com.memora.app.ui.components.NotePaper
import com.memora.app.ui.components.NoteDatesFooter
import com.memora.app.ui.components.NoteStatusBarEffect
import com.memora.app.ui.components.stableNoteStatusInset
import com.memora.app.ui.components.FloatingNoteControl
import com.memora.app.ui.components.rememberNoteChromeVisibility
import com.memora.app.ui.components.showNoteChromeOnTap
import com.memora.app.ui.theme.ParagraphMetrics
import com.memora.app.util.TextUtils
import com.memora.app.util.HashtagUtils
import com.memora.app.util.TimeUtils
import com.memora.app.ui.theme.MemoraDesign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private data class RecordingUiState(
    val audioBlockId: String,
    val relativePath: String,
    val startedAt: Long
)

private enum class EditorPermissionKind { CAMERA, MICROPHONE }

private data class EditorSnapshot(
    val title: String,
    val blocks: List<NoteBlock>,
    val plainText: String,
    val mood: NoteMood?
) {
    val encodedBlocks: String = NoteBlocksCodec.encode(blocks)
    val signature: String = title + "\u0000" + encodedBlocks + "\u0000" + (mood?.name ?: "")
    val isEmpty: Boolean = title.isBlank() && plainText.isBlank() &&
        !NoteBlocksCodec.hasImages(blocks) && !NoteBlocksCodec.hasAudio(blocks) && mood == null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    noteId: String?,
    type: NoteType,
    repository: MemoraRepository,
    savedTitles: List<SavedTitleEntity>,
    language: AppLanguage,
    writingFont: FontChoice,
    uiScale: Float,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val storage = remember { MediaStorage(context.applicationContext) }
    val recorder = remember { AudioRecorderController(context.applicationContext) }
    val recoveryStore = remember { EditorRecoveryStore(context.applicationContext) }
    val entryRecoveryKey = remember(noteId, type) { EditorRecoveryStore.sessionKey(noteId, type) }
    val saveMutex = remember(noteId, type) { Mutex() }
    val existingNoteFlow = remember(noteId) {
        noteId?.let(repository::observeNote)
            ?: kotlinx.coroutines.flow.flowOf<com.memora.app.data.local.NoteEntity?>(null)
    }
    val existingNote by existingNoteFlow.collectAsStateWithLifecycle(initialValue = null)

    var actualId by remember(noteId) { mutableStateOf(noteId) }
    var title by remember(noteId) { mutableStateOf("") }
    var blocks by remember(noteId) { mutableStateOf(NoteBlocksCodec.legacy("")) }
    var mood by remember(noteId) { mutableStateOf<NoteMood?>(null) }
    var createdAt by remember(noteId) { mutableLongStateOf(System.currentTimeMillis()) }
    var updatedAt by remember(noteId) { mutableLongStateOf(createdAt) }
    var initialized by remember(noteId) { mutableStateOf(false) }
    var lastPersistedSignature by remember(noteId) { mutableStateOf<String?>(null) }
    var titleMenu by remember { mutableStateOf(false) }
    var titleRequiredError by remember(noteId) { mutableStateOf(false) }
    var saveErrorShown by remember(noteId) { mutableStateOf(false) }
    var activeTextId by remember(noteId) { mutableStateOf(blocks.filterIsInstance<NoteBlock.Text>().firstOrNull()?.id) }
    var activeCursor by remember(noteId) { mutableIntStateOf(0) }
    var requestedFocusId by remember(noteId) { mutableStateOf<String?>(null) }
    var requestedFocusCursor by remember(noteId) { mutableStateOf<Int?>(null) }
    var recording by remember(noteId) { mutableStateOf<RecordingUiState?>(null) }
    var recordingElapsed by remember(noteId) { mutableLongStateOf(0L) }
    var recordingPaused by remember(noteId) { mutableStateOf(false) }
    var startAfterPermission by remember { mutableStateOf(false) }
    var imageSourceDialog by remember { mutableStateOf(false) }
    var toolsSheet by remember { mutableStateOf(false) }
    var permissionPrompt by remember { mutableStateOf<EditorPermissionKind?>(null) }
    var permissionSettings by remember { mutableStateOf<EditorPermissionKind?>(null) }
    var editorFeedback by remember { mutableStateOf<MemoraFeedbackMessage?>(null) }
    var pendingCameraCapture by remember { mutableStateOf<MediaStorage.CameraCapture?>(null) }
    var pendingMediaDelete by remember { mutableStateOf<NoteBlock?>(null) }
    // Deletion must only happen after a successful database commit.
    var removedMediaPaths by remember(noteId) { mutableStateOf(setOf<String>()) }
    var leaving by remember(noteId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val editorScrollState = rememberScrollState()
    val positionPrefs = remember(context) { context.getSharedPreferences("memora_editor_position", 0) }
    val density = LocalDensity.current
    val paragraphGap = with(density) { (ParagraphMetrics.EDITOR_LINE_HEIGHT_SP * uiScale * ParagraphMetrics.GAP_RATIO).sp.toDp() }
    val paragraphMinHeight = with(density) { (ParagraphMetrics.EDITOR_LINE_HEIGHT_SP * uiScale).sp.toDp() }
    // Only a note that already existed when this editor session opened is considered an edit.
    // Autosaves made while creating a new note must not manufacture an "edited" timestamp.
    val editingExistingSession = remember(noteId) { noteId != null }

    LaunchedEffect(existingNote, noteId, type) {
        if (initialized) return@LaunchedEffect
        // All initial block parsing is on Default, never in the composable/UI thread.
        suspend fun parseBlocks(json: String, fallback: String): List<NoteBlock> =
            withContext(Dispatchers.Default) { NoteBlocksCodec.decode(json, fallback) }
        val draft = withContext(Dispatchers.IO) { recoveryStore.read(entryRecoveryKey) }
        if (noteId == null) {
            if (draft != null && draft.type == type) {
                val persistedDraftNote = draft.noteId?.let { recoveredId ->
                    withContext(Dispatchers.IO) { repository.getNote(recoveredId) }
                }
                if (persistedDraftNote != null) {
                    actualId = persistedDraftNote.id
                    createdAt = persistedDraftNote.createdAt
                    updatedAt = persistedDraftNote.updatedAt
                    val persistedBlocks = parseBlocks(
                        persistedDraftNote.blocksJson,
                        persistedDraftNote.plainText
                    )
                    lastPersistedSignature = EditorSnapshot(
                        persistedDraftNote.title,
                        persistedBlocks,
                        persistedDraftNote.plainText,
                        NoteMood.fromStored(persistedDraftNote.mood)
                    ).signature
                } else {
                    createdAt = draft.createdAt
                    updatedAt = draft.savedAt
                }
                title = draft.title
                blocks = parseBlocks(draft.blocksJson, persistedDraftNote?.plainText.orEmpty())
                mood = NoteMood.fromStored(draft.mood)
            }
            val firstText = blocks.filterIsInstance<NoteBlock.Text>().firstOrNull()
            activeTextId = firstText?.id
            activeCursor = firstText?.text?.length ?: 0
            requestedFocusId = firstText?.id
            requestedFocusCursor = firstText?.text?.length ?: 0
            initialized = true
            return@LaunchedEffect
        }

        val note = existingNote ?: return@LaunchedEffect
        val persistedBlocks = parseBlocks(note.blocksJson, note.plainText)
        val persistedMood = NoteMood.fromStored(note.mood)
        val persistedSnapshot = EditorSnapshot(note.title, persistedBlocks, note.plainText, persistedMood)
        lastPersistedSignature = persistedSnapshot.signature
        title = note.title
        blocks = persistedBlocks
        mood = persistedMood
        createdAt = note.createdAt
        updatedAt = note.updatedAt
        actualId = note.id

        if (draft != null && draft.type == type && draft.savedAt > note.updatedAt) {
            title = draft.title
            blocks = parseBlocks(draft.blocksJson, note.plainText)
            mood = NoteMood.fromStored(draft.mood)
        }

        val firstText = blocks.filterIsInstance<NoteBlock.Text>().firstOrNull()
        val storedBlockId = positionPrefs.getString("${note.id}.block", null)
        val restoredText = blocks.filterIsInstance<NoteBlock.Text>().firstOrNull { it.id == storedBlockId } ?: firstText
        val storedCursor = positionPrefs.getInt("${note.id}.cursor", restoredText?.text?.length ?: 0)
        activeTextId = restoredText?.id
        activeCursor = storedCursor.coerceIn(0, restoredText?.text?.length ?: 0)
        requestedFocusId = restoredText?.id
        requestedFocusCursor = activeCursor
        initialized = true
    }

    LaunchedEffect(initialized, noteId) {
        if (initialized && noteId != null) {
            val readerFraction = positionPrefs.getFloat("${noteId}.readerFraction", -1f)
            val savedScroll = positionPrefs.getInt("${noteId}.scroll", 0).coerceAtLeast(0)
            // Reader and Editor have different text metrics: map proportional position.
            // This is a one-time hand-off, not a permanent override of editor's own position.
            if (readerFraction >= 0f) positionPrefs.edit().remove("${noteId}.readerFraction").apply()
            if (readerFraction >= 0f || savedScroll > 0) {
                repeat(3) { androidx.compose.runtime.withFrameNanos { } }
                val max = editorScrollState.maxValue
                val target = if (readerFraction >= 0f) (readerFraction.coerceIn(0f, 1f) * max).toInt()
                             else savedScroll.coerceAtMost(max)
                editorScrollState.scrollTo(target)
            }
        }
    }

    // The on-screen word counter/tags may lag by 180 ms; autosave always uses the exact
    // current snapshot. No full-document string scan is done on the UI for every keypress.
    val plain by produceState(initialValue = "", key1 = blocks) {
        delay(180)
        value = withContext(Dispatchers.Default) { NoteBlocksCodec.plainText(blocks) }
    }
    val wordCount = remember(plain) { TextUtils.countWords(plain) }
    val hashtags = remember(plain) { HashtagUtils.extract(plain) }
    val completedAudioDuration = remember(blocks) { NoteBlocksCodec.audioDuration(blocks) }
    val shortAudioWarning = type == NoteType.SHORT &&
        completedAudioDuration + (recordingElapsed.takeIf { recording != null } ?: 0L) > 5 * 60_000L
    fun snapshotNow(): EditorSnapshot {
        val currentBlocks = blocks
        return EditorSnapshot(
            title = title,
            blocks = currentBlocks,
            plainText = NoteBlocksCodec.plainText(currentBlocks),
            mood = mood
        )
    }

    fun activeRecoveryKey(): String = EditorRecoveryStore.sessionKey(actualId ?: noteId, type)

    fun persistEditorPosition() {
        val id = actualId ?: noteId ?: return
        positionPrefs.edit()
            .putInt("${id}.scroll", editorScrollState.value)
            .putString("${id}.block", activeTextId)
            .putInt("${id}.cursor", activeCursor)
            .apply()
    }

    suspend fun saveNow(snapshot: EditorSnapshot = snapshotNow()): NoteSaveResult? = saveMutex.withLock {
        if (!initialized || (actualId == null && snapshot.isEmpty)) return@withLock null
        if (snapshot.title.isBlank()) return@withLock NoteSaveResult.TitleRequired
        val now = System.currentTimeMillis()
        val persistedBefore = actualId != null
        val changed = snapshot.signature != lastPersistedSignature
        val storedUpdatedAt = when {
            !persistedBefore -> now
            !editingExistingSession -> createdAt
            changed -> now
            else -> updatedAt
        }
        val result = repository.saveNote(
            id = actualId,
            type = type,
            title = snapshot.title,
            html = TextUtils.plainTextToHtml(snapshot.plainText),
            plainText = snapshot.plainText,
            createdAt = if (persistedBefore) createdAt else null,
            updatedAt = storedUpdatedAt,
            blocksJson = snapshot.encodedBlocks,
            hasImages = NoteBlocksCodec.hasImages(snapshot.blocks),
            hasAudio = NoteBlocksCodec.hasAudio(snapshot.blocks),
            mood = snapshot.mood?.name
        )
        when (result) {
            is NoteSaveResult.Success -> {
                actualId = result.noteId
                if (!persistedBefore) {
                    createdAt = now
                    updatedAt = now
                } else if (editingExistingSession && changed) {
                    updatedAt = storedUpdatedAt
                } else if (!editingExistingSession) {
                    updatedAt = createdAt
                }
                lastPersistedSignature = snapshot.signature
                // First commit references, then safely remove orphaned media. A concurrent
                // unsaved editor state can still reference a path: never delete that path.
                val currentlyUsed = snapshotNow().blocks.mapNotNull {
                    when (it) {
                        is NoteBlock.Image -> it.relativePath
                        is NoteBlock.Audio -> it.relativePath
                        else -> null
                    }
                }.toSet()
                val eligible = removedMediaPaths - currentlyUsed
                withContext(Dispatchers.IO) {
                    eligible.forEach { path ->
                        if (runCatching { !repository.isMediaReferenced(path) }.getOrDefault(false)) {
                            storage.delete(path)
                        }
                    }
                }
                removedMediaPaths = removedMediaPaths - eligible
                saveErrorShown = false
                // Do not let a slower, older save erase a recovery file written for newer edits.
                if (snapshotNow().signature == snapshot.signature) {
                    val canonicalKey = activeRecoveryKey()
                    withContext(Dispatchers.IO) {
                        recoveryStore.clear(canonicalKey)
                        if (canonicalKey != entryRecoveryKey) recoveryStore.clear(entryRecoveryKey)
                    }
                }
            }
            NoteSaveResult.TitleRequired -> titleRequiredError = true
            NoteSaveResult.ReadOnly -> Unit
            is NoteSaveResult.Failed -> {
                LocalCrashDiagnostics.recordNonFatal(context, "editor-save", result.cause)
            }
        }
        result
    }

    fun finishRecording() {
        val state = recording ?: return
        val duration = recorder.stop()
        val file = storage.resolve(state.relativePath)
        blocks = if (duration != null && file.exists() && file.length() > 0L) {
            blocks.map { block ->
                if (block is NoteBlock.Audio && block.id == state.audioBlockId) {
                    block.copy(durationMs = duration)
                } else block
            }
        } else {
            NoteBlocksCodec.remove(blocks, state.audioBlockId)
        }
        recording = null
        recordingElapsed = 0L
        recordingPaused = false
    }

    fun insertMedia(media: NoteBlock) {
        val (next, focusId) = NoteBlocksCodec.insertAtCursor(blocks, activeTextId, activeCursor, media)
        blocks = next
        requestedFocusCursor = 0
        requestedFocusId = focusId
        activeTextId = focusId
        activeCursor = 0
    }

    fun showImageImportError() {
        editorFeedback = MemoraFeedbackMessage(
            text = L.t(language, S.IMPORT_FAILED),
            kind = MemoraFeedbackKind.ERROR
        )
    }

    fun importImage(load: () -> NoteBlock.Image) {
        scope.launch {
            try {
                val image = withContext(Dispatchers.IO) { load() }
                insertMedia(image)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: OutOfMemoryError) {
                // A pathological source image must fail gracefully instead of taking the process down.
                showImageImportError()
            } catch (_: Exception) {
                showImageImportError()
            }
        }
    }

    fun startRecording() {
        if (recording != null) return
        runCatching {
            val session = recorder.start(storage)
            val audio = NoteBlock.Audio(relativePath = session.relativePath, durationMs = 0L)
            val (next, focusId) = NoteBlocksCodec.insertAtCursor(blocks, activeTextId, activeCursor, audio)
            blocks = next
            activeTextId = focusId
            activeCursor = 0
            requestedFocusCursor = 0
            requestedFocusId = focusId
            recording = RecordingUiState(audio.id, session.relativePath, session.startedAt)
            recordingElapsed = 0L
            recordingPaused = false
        }
    }

    fun pauseRecording() {
        if (recording == null || recordingPaused) return
        if (recorder.pause()) {
            recordingPaused = true
            recordingElapsed = recorder.elapsedMs()
        }
    }

    fun resumeRecording() {
        if (recording == null || !recordingPaused) return
        if (recorder.resume()) {
            recordingPaused = false
            recordingElapsed = recorder.elapsedMs()
        }
    }

    LaunchedEffect(startAfterPermission) {
        if (startAfterPermission) {
            startAfterPermission = false
            startRecording()
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            // The system Photo Picker / gallery grants temporary read access. Memora immediately
            // copies the selected image into app-private storage, so persistable document access
            // is unnecessary and the user sees a photo-focused picker instead of the Files app.
            importImage { storage.importImage(uri) }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val capture = pendingCameraCapture
        pendingCameraCapture = null
        if (capture != null) {
            if (success) {
                importImage { storage.importCameraImage(capture) }
            } else {
                storage.discardCameraCapture(capture)
            }
        }
    }

    fun launchCameraCapture() {
        runCatching { storage.createCameraCapture() }
            .onSuccess { capture ->
                pendingCameraCapture = capture
                cameraLauncher.launch(capture.uri)
            }
            .onFailure { showImageImportError() }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCameraCapture()
        } else if (activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)) {
            permissionSettings = EditorPermissionKind.CAMERA
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startAfterPermission = true
        } else if (activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)) {
            permissionSettings = EditorPermissionKind.MICROPHONE
        }
    }

    fun requestCameraForImage() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCameraCapture()
        } else {
            permissionPrompt = EditorPermissionKind.CAMERA
        }
    }

    fun requestMicrophone() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startRecording()
        } else {
            permissionPrompt = EditorPermissionKind.MICROPHONE
        }
    }

    LaunchedEffect(recording?.audioBlockId, recordingPaused) {
        val state = recording ?: return@LaunchedEffect
        while (recording?.audioBlockId == state.audioBlockId) {
            recordingElapsed = recorder.elapsedMs()
            delay(if (recordingPaused) 750 else 350)
        }
    }

    fun showSaveFailure() {
        if (saveErrorShown) return
        saveErrorShown = true
        editorFeedback = MemoraFeedbackMessage(
            text = L.t(language, S.SAVE_FAILED),
            kind = MemoraFeedbackKind.ERROR
        )
    }

    fun leaveEditor() {
        if (leaving) return
        if (recording != null) finishRecording()
        val snapshot = snapshotNow()
        if (snapshot.title.isBlank() && (actualId != null || !snapshot.isEmpty)) {
            titleRequiredError = true
            return
        }
        leaving = true
        scope.launch {
            try {
                when (saveNow(snapshot)) {
                    is NoteSaveResult.Failed, NoteSaveResult.ReadOnly -> {
                        showSaveFailure()
                        return@launch
                    }
                    NoteSaveResult.TitleRequired -> {
                        titleRequiredError = true
                        return@launch
                    }
                    else -> Unit // Obsidian is already queued by repository.saveNote.
                }
                persistEditorPosition()
                onBack()
            } finally {
                leaving = false
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val stopAndSave by rememberUpdatedState(newValue = {
        if (recording != null) finishRecording()
        persistEditorPosition()
        val snapshot = snapshotNow()
        scope.launch { saveNow(snapshot) }
    })
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) stopAndSave()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            persistEditorPosition()
            if (recorder.isRecording()) recorder.cancel()
        }
    }

    BackHandler(onBack = ::leaveEditor)

    // Recovery snapshots are lighter than Room writes and are intentionally kept ahead of the
    // autosave debounce. A successful database save removes the recovery file.
    LaunchedEffect(title, blocks, mood, initialized) {
        if (!initialized) return@LaunchedEffect
        delay(250)
        // Only construct a potentially huge JSON snapshot after typing has paused.
        val snapshot = snapshotNow()
        if (actualId == null && snapshot.isEmpty) {
            withContext(Dispatchers.IO) { recoveryStore.clear(entryRecoveryKey) }
            return@LaunchedEffect
        }
        val now = System.currentTimeMillis()
        val recoveryKey = activeRecoveryKey()
        withContext(Dispatchers.IO) {
            recoveryStore.write(
                recoveryKey,
                EditorRecoveryStore.Draft(
                    noteId = actualId,
                    type = type,
                    title = snapshot.title,
                    blocksJson = snapshot.encodedBlocks,
                    mood = snapshot.mood?.name,
                    createdAt = createdAt,
                    savedAt = now
                )
            )
            if (recoveryKey != entryRecoveryKey) recoveryStore.clear(entryRecoveryKey)
        }
    }

    // One debounced writer + one mutex means rapid IME updates never create overlapping Room writes.
    LaunchedEffect(title, blocks, mood, initialized) {
        if (!initialized) return@LaunchedEffect
        delay(900)
        val snapshot = snapshotNow()
        if (actualId == null && snapshot.isEmpty) return@LaunchedEffect
        if (snapshot.title.isNotBlank() && saveNow(snapshot) is NoteSaveResult.Failed) showSaveFailure()
    }

    if (imageSourceDialog) {
        MemoraSheet(onDismissRequest = { imageSourceDialog = false }) {
            Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 26.dp)) {
                Text(
                    L.t(language, S.IMAGE_SOURCE),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)
                )
                Surface(
                    onClick = { imageSourceDialog = false; requestCameraForImage() },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    shape = RoundedCornerShape(MemoraDesign.radiusMedium),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = .065f)
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CameraAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(L.t(language, S.CAMERA), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 14.dp))
                    }
                }
                Surface(
                    onClick = {
                        imageSourceDialog = false
                        imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    shape = RoundedCornerShape(MemoraDesign.radiusMedium),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = .065f)
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(L.t(language, S.GALLERY), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 14.dp))
                    }
                }
            }
        }
    }

    permissionPrompt?.let { kind ->
        val camera = kind == EditorPermissionKind.CAMERA
        MemoraPermissionDialog(
            language = language,
            title = if (camera) permissionCameraTitle(language) else permissionMicTitle(language),
            text = if (camera) permissionCameraText(language) else permissionMicText(language),
            confirmLabel = permissionContinueLabel(language),
            onConfirm = {
                permissionPrompt = null
                if (camera) cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
            onDismiss = { permissionPrompt = null }
        )
    }

    permissionSettings?.let { kind ->
        val camera = kind == EditorPermissionKind.CAMERA
        MemoraPermissionDialog(
            language = language,
            title = if (camera) permissionCameraTitle(language) else permissionMicTitle(language),
            text = permissionSettingsText(language),
            confirmLabel = permissionSettingsLabel(language),
            onConfirm = {
                permissionSettings = null
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                    )
                }
            },
            onDismiss = { permissionSettings = null }
        )
    }

    pendingMediaDelete?.let { media ->
        MemoraConfirmationDialog(
            language = language,
            title = if (language == AppLanguage.UZ) "Mediani o‘chirish" else "Delete media",
            text = if (language == AppLanguage.UZ) "Bu rasm yoki audio yozuvdan o‘chiriladi." else "This image or audio will be removed from the note.",
            onConfirm = {
                val path = when (media) {
                    is NoteBlock.Image -> media.relativePath
                    is NoteBlock.Audio -> media.relativePath
                    else -> null
                }
                if (path != null) removedMediaPaths = removedMediaPaths + path
                blocks = NoteBlocksCodec.remove(blocks, media.id)
                pendingMediaDelete = null
            },
            onDismiss = { pendingMediaDelete = null }
        )
    }

    if (toolsSheet) {
        MemoraSheet(onDismissRequest = { toolsSheet = false }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, bottom = 26.dp)
            ) {
                Text(
                    editorToolsTitle(language),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                )
                Surface(
                    onClick = {
                        toolsSheet = false
                        imageSourceDialog = true
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    shape = RoundedCornerShape(MemoraDesign.radiusMedium),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = .065f)
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(L.t(language, S.ADD_IMAGE), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 14.dp))
                    }
                }
                Surface(
                    onClick = {
                        toolsSheet = false
                        if (recording != null) finishRecording() else requestMicrophone()
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    shape = RoundedCornerShape(MemoraDesign.radiusMedium),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = .065f)
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            if (recording != null) stopAudioLabel(language) else L.t(language, S.ADD_AUDIO),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(start = 14.dp)
                        )
                    }
                }
                Text(
                    L.t(language, S.MOOD),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NoteMood.entries.forEach { option ->
                        val selectedMood = mood == option
                        Surface(
                            onClick = { mood = option; toolsSheet = false },
                            shape = RoundedCornerShape(MemoraDesign.radiusSmall),
                            color = if (selectedMood) MaterialTheme.colorScheme.primary.copy(alpha = .12f) else Color.Transparent
                        ) {
                            MoodSticker(
                                mood = option,
                                description = L.t(language, moodLabelKey(option)),
                                modifier = Modifier.padding(8.dp).size(36.dp)
                            )
                        }
                    }
                }
                if (mood != null) {
                    TextButton(
                        onClick = { mood = null; toolsSheet = false },
                        modifier = Modifier.align(Alignment.End).padding(top = 4.dp)
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = null)
                        Text(L.t(language, S.REMOVE_MOOD), modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
    }

    val chromeVisible = rememberNoteChromeVisibility(editorScrollState)
    NoteStatusBarEffect(editorScrollState)
    // The canvas extends under the status bar. Only the floating controls are inset.
    // The tap observer never consumes events, so cursor/selection/keyboard still work.
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .imePadding().navigationBarsPadding()
            .showNoteChromeOnTap { chromeVisible.value = true }
    ) {
        Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            val blockContent: @Composable (Modifier) -> Unit = { contentModifier ->
                Column(
                    modifier = contentModifier.verticalScroll(editorScrollState),
                    verticalArrangement = Arrangement.Top
                ) {
                    Spacer(Modifier.height(stableNoteStatusInset() + 66.dp))
                    NotePaper {
                    TextField(
                        value = title,
                        onValueChange = {
                            title = it.replace('\n', ' ')
                            if (it.isNotBlank()) titleRequiredError = false
                        },
                        modifier = Modifier
                            .fillMaxWidth(),
                        textStyle = MaterialTheme.typography.headlineLarge.copy(fontSize = 39.sp, lineHeight = 48.sp, fontWeight = FontWeight.SemiBold),
                        placeholder = {
                            Text(
                                editorTitleHint(language),
                                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 39.sp, lineHeight = 48.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        isError = titleRequiredError,
                        supportingText = if (titleRequiredError) {
                            { Text(L.t(language, S.TITLE_REQUIRED), color = MaterialTheme.colorScheme.error) }
                        } else null,
                        singleLine = false,
                        minLines = 1,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        trailingIcon = {
                            IconButton(onClick = { titleMenu = true }) {
                                Icon(Icons.Rounded.ArrowDropDown, contentDescription = L.t(language, S.SELECT_TITLE))
                            }
                            DropdownMenu(expanded = titleMenu, onDismissRequest = { titleMenu = false }) {
                                savedTitles.forEach { item ->
                                    DropdownMenuItem(
                                        text = { Text(item.value) },
                                        onClick = {
                                            title = item.value
                                            titleRequiredError = false
                                            titleMenu = false
                                        }
                                    )
                                }
                            }
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )

                    if (shortAudioWarning) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Text(
                                L.t(language, S.AUDIO_LIMIT_WARNING),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                    }

                    blocks.forEachIndexed { index, block ->
                        key(block.id) {
                            when (block) {
                                is NoteBlock.Text -> PlainTextEditor(
                                    text = block.text,
                                    styles = block.styles,
                                    onValueChanged = { value, spans, cursor ->
                                        val acceptedValue = if (type == NoteType.SHORT) {
                                            val otherBlocks = blocks.filterNot { it is NoteBlock.Text && it.id == block.id }
                                            val remaining = (50 - TextUtils.countWords(NoteBlocksCodec.plainText(otherBlocks))).coerceAtLeast(0)
                                            limitWords(value, remaining)
                                        } else value
                                        val safeCursor = cursor.coerceAtMost(acceptedValue.length)
                                        val safeSpans = spans.mapNotNull { span ->
                                            val start = span.start.coerceIn(0, acceptedValue.length)
                                            val end = span.end.coerceIn(start, acceptedValue.length)
                                            if (end > start) span.copy(start = start, end = end) else null
                                        }
                                        val result = NoteBlocksCodec.editText(blocks, block.id, acceptedValue, safeSpans, safeCursor)
                                        blocks = result.blocks
                                        if (result.focusTextId != block.id) {
                                            activeTextId = result.focusTextId
                                            activeCursor = result.focusCursor
                                            requestedFocusId = result.focusTextId
                                            requestedFocusCursor = result.focusCursor
                                        }
                                    },
                                    onParagraphBreak = { cursor, nextPrefix ->
                                        val result = NoteBlocksCodec.splitParagraph(blocks, block.id, cursor, nextPrefix)
                                        blocks = result.blocks
                                        activeTextId = result.focusTextId
                                        activeCursor = result.focusCursor
                                        requestedFocusId = result.focusTextId
                                        requestedFocusCursor = result.focusCursor
                                    },
                                    onMergeWithPrevious = {
                                        val result = NoteBlocksCodec.mergeWithPrevious(blocks, block.id)
                                        if (result != null) {
                                            blocks = result.blocks
                                            activeTextId = result.focusTextId
                                            activeCursor = result.focusCursor
                                            requestedFocusId = result.focusTextId
                                            requestedFocusCursor = result.focusCursor
                                            true
                                        } else false
                                    },
                                    fontChoice = writingFont,
                                    ink = MaterialTheme.colorScheme.onSurface,
                                    uiScale = uiScale,
                                    onFocused = {
                                        activeTextId = block.id
                                        requestedFocusId = null
                                        requestedFocusCursor = null
                                    },
                                    onCursorChanged = { cursor ->
                                        if (activeTextId == block.id) activeCursor = cursor
                                    },
                                    requestFocus = requestedFocusId == block.id,
                                    requestCursorPosition = if (requestedFocusId == block.id) requestedFocusCursor else null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = paragraphMinHeight)
                                )

                                is NoteBlock.Image -> ImageBlockView(
                                    block = block,
                                    file = storage.resolve(block.relativePath),
                                    editable = true,
                                    onDelete = { pendingMediaDelete = block }
                                )

                                is NoteBlock.Audio -> {
                                    val isThisRecording = recording?.audioBlockId == block.id
                                    AudioBlockView(
                                        block = block,
                                        file = storage.resolve(block.relativePath),
                                        editable = true,
                                        isRecording = isThisRecording,
                                        isRecordingPaused = isThisRecording && recordingPaused,
                                        recordingElapsedMs = if (isThisRecording) recordingElapsed else block.durationMs,
                                        language = language,
                                        onPauseRecording = ::pauseRecording,
                                        onResumeRecording = ::resumeRecording,
                                        onStopRecording = ::finishRecording,
                                        onDelete = { pendingMediaDelete = block }
                                    )
                                }
                            }
                        }

                        val next = blocks.getOrNull(index + 1)
                        if (next != null) {
                            val gap = if (block is NoteBlock.Text && next is NoteBlock.Text) paragraphGap else 12.dp
                            Spacer(Modifier.height(gap))
                        }
                    }
                    HashtagChips(tags = hashtags, modifier = Modifier.padding(top = 14.dp))
                    NoteDatesFooter(
                        createdLabel = createdText(language, createdAt),
                        editedLabel = if (actualId != null && updatedAt > createdAt + 1000L)
                            editedText(language, updatedAt) else null
                    )
                    } // End of one continuous document card.
                    Spacer(Modifier.height(112.dp))
                }
            }

            // The paper is inside one continuous scroll viewport, not a separately scrollable card.
            // Its top, title, blocks and dates all move above the physical screen edge.
            blockContent(
                Modifier.fillMaxWidth().weight(1f)
                    .padding(top = 2.dp, start = 4.dp, end = 4.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 7.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .42f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (type == NoteType.SHORT) Icons.Rounded.Description else Icons.Rounded.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            if (type == NoteType.SHORT) "${shortEditorTypeLabel(language)} · $wordCount/50" else longEditorTypeLabel(language),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 5.dp)
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Surface(
                    onClick = { toolsSheet = true },
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = .12f)
                ) {
                    Box(Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Add, contentDescription = editorToolsTitle(language), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        MemoraFeedbackHost(
            message = editorFeedback,
            onDismiss = { editorFeedback = null },
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )
        } // Stable editor body, including keyboard-aware tools.
        FloatingNoteChrome(
            visible = chromeVisible.value,
            backLabel = if (language == AppLanguage.UZ) "Orqaga" else if (language == AppLanguage.RU) "Назад" else "Back",
            onBack = ::leaveEditor
        ) {
            FloatingNoteControl(
                icon = Icons.Rounded.Done,
                description = if (language == AppLanguage.UZ) "Tayyor" else if (language == AppLanguage.RU) "Готово" else "Done",
                onClick = ::leaveEditor
            )
        }
    }
}


private fun limitWords(text: String, maxWords: Int): String {
    if (maxWords <= 0) return ""
    val matches = Regex("\\S+").findAll(text).toList()
    if (matches.size <= maxWords) return text
    val end = matches[maxWords - 1].range.last + 1
    return text.substring(0, end)
}

private fun moodLabelKey(mood: NoteMood): S = when (mood) {
    NoteMood.GREAT -> S.MOOD_GREAT
    NoteMood.GOOD -> S.MOOD_GOOD
    NoteMood.NEUTRAL -> S.MOOD_NEUTRAL
    NoteMood.BAD -> S.MOOD_BAD
    NoteMood.VERY_BAD -> S.MOOD_VERY_BAD
}

private fun editorTitleHint(language: AppLanguage): String = when (language) {
    AppLanguage.UZ -> "Sarlavha kiriting"
    AppLanguage.RU -> "Введите заголовок"
    else -> "Enter a title"
}

private fun shortEditorTypeLabel(language: AppLanguage): String = if (language == AppLanguage.UZ) "Qisqa matn" else "Short note"
private fun longEditorTypeLabel(language: AppLanguage): String = if (language == AppLanguage.UZ) "Uzun matn" else "Long note"

private fun createdText(language: AppLanguage, createdAt: Long): String = when (language) {
    AppLanguage.UZ -> "Yozilgan: ${TimeUtils.dateTime(createdAt, language)}"
    AppLanguage.RU -> "Создано: ${TimeUtils.dateTime(createdAt, language)}"
    else -> "Written: ${TimeUtils.dateTime(createdAt, language)}"
}

private fun editedText(language: AppLanguage, updatedAt: Long): String =
    L.format(language, S.EDITED, TimeUtils.dateTime(updatedAt, language))

private fun editorToolsTitle(language: AppLanguage): String = if (language == AppLanguage.UZ) "Qo‘shimchalar" else "Tools"
private fun stopAudioLabel(language: AppLanguage): String = if (language == AppLanguage.UZ) "Audio yozishni tugatish" else "Stop audio recording"

private fun permissionCameraTitle(l: AppLanguage) = if (l == AppLanguage.UZ) "Kamera ruxsati" else "Camera permission"
private fun permissionCameraText(l: AppLanguage) = if (l == AppLanguage.UZ) "Memora suratni kamera orqali yozuvga qo‘shish uchun kamera ruxsatidan foydalanadi." else "Memora uses camera access only to add a photo to your note."
private fun permissionMicTitle(l: AppLanguage) = if (l == AppLanguage.UZ) "Mikrofon ruxsati" else "Microphone permission"
private fun permissionMicText(l: AppLanguage) = if (l == AppLanguage.UZ) "Memora yozuv ichida audio yozish uchun mikrofon ruxsatidan foydalanadi." else "Memora uses microphone access only to record audio inside your note."
private fun permissionContinueLabel(l: AppLanguage) = if (l == AppLanguage.UZ) "Davom etish" else "Continue"
private fun permissionSettingsText(l: AppLanguage) = if (l == AppLanguage.UZ) "Ruxsat o‘chirib qo‘yilgan. Uni telefon Sozlamalaridan yoqishingiz mumkin." else "This permission is disabled. You can enable it in the app settings."
private fun permissionSettingsLabel(l: AppLanguage) = if (l == AppLanguage.UZ) "Sozlamalarni ochish" else "Open settings"
